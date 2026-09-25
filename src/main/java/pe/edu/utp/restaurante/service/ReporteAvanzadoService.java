package pe.edu.utp.restaurante.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReporteAvanzadoService {
    private final JdbcTemplate jdbc;
    public ReporteAvanzadoService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public record Resumen(BigDecimal ventas,BigDecimal subtotal,BigDecimal igv,BigDecimal descuentos,BigDecimal delivery,long pedidos,BigDecimal ticketPromedio,BigDecimal compras,BigDecimal gastos,BigDecimal saldoCajaAcumulado){
        public BigDecimal resultadoEstimado(){return ventas.subtract(compras).subtract(gastos).setScale(2,RoundingMode.HALF_UP);}
    }
    public record VentaDia(LocalDate fecha,long pedidos,BigDecimal total){}
    public record MetodoPago(String metodo,long operaciones,BigDecimal total){}
    public record PlatoVendido(String plato,long unidades,BigDecimal importe){}
    public record MovimientoCaja(LocalDateTime fecha,String tipo,String descripcion,BigDecimal monto){}
    public record Reporte(LocalDate desde,LocalDate hasta,Resumen resumen,List<VentaDia> porDia,List<MetodoPago> metodos,List<PlatoVendido> topPlatos,List<MovimientoCaja> movimientosCaja){}

    public Reporte generar(LocalDate desde,LocalDate hasta){
        validarRango(desde,hasta);
        Timestamp ti=Timestamp.valueOf(desde.atStartOfDay()), tf=Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());
        Resumen base=jdbc.queryForObject("""
                select coalesce(sum(pg.monto),0) ventas,coalesce(sum(p.subtotal),0) subtotal,
                       coalesce(sum(case when p.igv>0 then p.igv else greatest(p.total-p.subtotal,0) end),0) igv,
                       coalesce(sum(p.descuento),0) descuentos,coalesce(sum(p.costo_envio),0) delivery,
                       count(*) pedidos,coalesce(avg(pg.monto),0) ticket
                from pagos pg join pedidos p on p.id=pg.pedido_id
                where pg.created_at>=? and pg.created_at<?
                """,(rs,n)->new Resumen(dinero(rs.getBigDecimal("ventas")),dinero(rs.getBigDecimal("subtotal")),dinero(rs.getBigDecimal("igv")),dinero(rs.getBigDecimal("descuentos")),dinero(rs.getBigDecimal("delivery")),rs.getLong("pedidos"),dinero(rs.getBigDecimal("ticket")),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),ti,tf);
        BigDecimal compras=jdbc.queryForObject("select coalesce(sum(total),0) from compras where estado='REGISTRADA' and fecha>=? and fecha<?",BigDecimal.class,ti,tf);
        BigDecimal gastos=jdbc.queryForObject("select coalesce(sum(monto),0) from gastos where activo and fecha>=? and fecha<?",BigDecimal.class,ti,tf);
        BigDecimal saldo=jdbc.queryForObject("""
                select coalesce((select sum(monto) from pagos),0)
                     - coalesce((select sum(total) from compras where estado='REGISTRADA'),0)
                     - coalesce((select sum(monto) from gastos where activo),0)
                """,BigDecimal.class);
        Resumen resumen=new Resumen(base.ventas(),base.subtotal(),base.igv(),base.descuentos(),base.delivery(),base.pedidos(),base.ticketPromedio(),dinero(compras),dinero(gastos),dinero(saldo));
        List<VentaDia> porDia=jdbc.query("""
                select created_at::date fecha,count(*) pedidos,coalesce(sum(monto),0) total from pagos
                where created_at>=? and created_at<? group by created_at::date order by fecha
                """,(rs,n)->new VentaDia(rs.getDate("fecha").toLocalDate(),rs.getLong("pedidos"),dinero(rs.getBigDecimal("total"))),ti,tf);
        List<MetodoPago> metodos=jdbc.query("""
                select metodo,count(*) operaciones,coalesce(sum(monto),0) total from pagos
                where created_at>=? and created_at<? group by metodo order by total desc
                """,(rs,n)->new MetodoPago(rs.getString("metodo"),rs.getLong("operaciones"),dinero(rs.getBigDecimal("total"))),ti,tf);
        List<PlatoVendido> top=jdbc.query("""
                select coalesce(pd.plato_nombre,pl.nombre,'Plato '||pd.plato_id) plato,sum(pd.cantidad)::bigint unidades,coalesce(sum(pd.subtotal),0) importe
                from pedido_detalles pd join pagos pg on pg.pedido_id=pd.pedido_id left join platos pl on pl.id=pd.plato_id
                where pg.created_at>=? and pg.created_at<? and pd.estado<>'CANCELADO'
                group by coalesce(pd.plato_nombre,pl.nombre,'Plato '||pd.plato_id) order by unidades desc,importe desc limit 15
                """,(rs,n)->new PlatoVendido(rs.getString("plato"),rs.getLong("unidades"),dinero(rs.getBigDecimal("importe"))),ti,tf);
        List<MovimientoCaja> movimientos=jdbc.query("""
                select fecha,tipo,descripcion,monto from (
                    select pg.created_at fecha,'INGRESO'::text tipo,'Pago pedido '||p.codigo descripcion,pg.monto monto
                    from pagos pg join pedidos p on p.id=pg.pedido_id where pg.created_at>=? and pg.created_at<?
                    union all
                    select c.fecha,'COMPRA'::text,'Compra proveedor: '||pr.nombre||coalesce(' - '||c.numero_documento,''),-c.total
                    from compras c join proveedores pr on pr.id=c.proveedor_id where c.estado='REGISTRADA' and c.fecha>=? and c.fecha<?
                    union all
                    select g.fecha,'GASTO'::text,g.categoria||': '||g.descripcion,-g.monto
                    from gastos g where g.activo and g.fecha>=? and g.fecha<?
                ) x order by fecha desc
                """,(rs,n)->new MovimientoCaja(rs.getTimestamp("fecha").toLocalDateTime(),rs.getString("tipo"),rs.getString("descripcion"),dinero(rs.getBigDecimal("monto"))),ti,tf,ti,tf,ti,tf);
        return new Reporte(desde,hasta,resumen,porDia,metodos,top,movimientos);
    }

    public void registrarGasto(String categoria,String descripcion,BigDecimal monto,Long usuarioId){
        if(categoria==null||categoria.isBlank())throw new IllegalArgumentException("La categoría del gasto es obligatoria.");
        if(descripcion==null||descripcion.isBlank())throw new IllegalArgumentException("La descripción del gasto es obligatoria.");
        if(monto==null||monto.signum()<=0)throw new IllegalArgumentException("El monto debe ser mayor que cero.");
        jdbc.update("insert into gastos(usuario_id,categoria,descripcion,monto) values(?,?,?,?)",usuarioId,categoria.trim(),descripcion.trim(),monto.setScale(2,RoundingMode.HALF_UP));
    }
    public BigDecimal variacionPorcentual(Reporte actual,Reporte anterior){BigDecimal base=anterior.resumen().ventas();if(base.signum()==0)return actual.resumen().ventas().signum()==0?BigDecimal.ZERO:null;return actual.resumen().ventas().subtract(base).multiply(new BigDecimal("100")).divide(base,2,RoundingMode.HALF_UP);}
    private static BigDecimal dinero(BigDecimal v){return(v==null?BigDecimal.ZERO:v).setScale(2,RoundingMode.HALF_UP);}
    private static void validarRango(LocalDate d,LocalDate h){if(d==null||h==null)throw new IllegalArgumentException("Selecciona ambas fechas.");if(h.isBefore(d))throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");}
}
