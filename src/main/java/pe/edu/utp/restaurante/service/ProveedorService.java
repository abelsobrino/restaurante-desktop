package pe.edu.utp.restaurante.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProveedorService {
    private final JdbcTemplate jdbc;
    public ProveedorService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ProveedorFila(Long id, String ruc, String nombre, String telefono, String email, String direccion, boolean activo) {
        @Override public String toString() { return nombre + (ruc == null || ruc.isBlank() ? "" : " — RUC " + ruc); }
    }
    public record CompraItem(Long ingredienteId, String ingrediente, BigDecimal cantidadCompra, String unidadCompra, BigDecimal cantidadBase, BigDecimal costoTotal) {}
    public record PrecioProveedor(Long ingredienteId, String ingrediente, BigDecimal precioReferencia, BigDecimal cantidadReferencia, String unidadCompra) {}
    public record CompraFila(Long id, LocalDateTime fecha, String proveedor, String documento, BigDecimal total, String estado) {}
    public record CompraDetalleFila(String ingrediente, BigDecimal cantidadCompra, String unidadCompra, BigDecimal cantidadBase, String unidadBase, BigDecimal costoTotal) {}

    public List<ProveedorFila> listar() {
        return jdbc.query("""
                select id,ruc,nombre,telefono,email,direccion,activo from proveedores
                order by activo desc,nombre
                """, (rs,n) -> new ProveedorFila(rs.getLong("id"),rs.getString("ruc"),rs.getString("nombre"),rs.getString("telefono"),rs.getString("email"),rs.getString("direccion"),rs.getBoolean("activo")));
    }

    public Long crear(String nombre,String ruc,String telefono,String email,String direccion) {
        validarProveedor(nombre,ruc);
        return jdbc.queryForObject("""
                insert into proveedores(ruc,nombre,telefono,email,direccion)
                values(nullif(btrim(?),''),?,nullif(btrim(?),''),nullif(btrim(?),''),nullif(btrim(?),'')) returning id
                """, Long.class,nvl(ruc),nombre.trim(),nvl(telefono),nvl(email),nvl(direccion));
    }

    public void actualizar(Long id,String nombre,String ruc,String telefono,String email,String direccion) {
        if(id==null) throw new IllegalArgumentException("Selecciona un proveedor.");
        validarProveedor(nombre,ruc);
        jdbc.update("""
                update proveedores set ruc=nullif(btrim(?),''),nombre=?,telefono=nullif(btrim(?),''),
                email=nullif(btrim(?),''),direccion=nullif(btrim(?),''),updated_at=now() where id=?
                """,nvl(ruc),nombre.trim(),nvl(telefono),nvl(email),nvl(direccion),id);
    }

    public void cambiarActivo(Long id, boolean activo) {
        if(id==null) throw new IllegalArgumentException("Selecciona un proveedor.");
        jdbc.update("update proveedores set activo=?,updated_at=now() where id=?",activo,id);
    }

    public List<PrecioProveedor> precios(Long proveedorId) {
        if(proveedorId==null) return List.of();
        return jdbc.query("""
                select pi.ingrediente_id,i.nombre,pi.precio_referencia,pi.cantidad_referencia,pi.unidad_compra
                from proveedor_ingredientes pi join ingredientes i on i.id=pi.ingrediente_id
                where pi.proveedor_id=? order by i.nombre
                """,(rs,n)->new PrecioProveedor(rs.getLong("ingrediente_id"),rs.getString("nombre"),rs.getBigDecimal("precio_referencia"),rs.getBigDecimal("cantidad_referencia"),rs.getString("unidad_compra")),proveedorId);
    }

    public void guardarPrecio(Long proveedorId,Long ingredienteId,BigDecimal precio,BigDecimal cantidadReferencia,String unidadCompra) {
        if(proveedorId==null||ingredienteId==null) throw new IllegalArgumentException("Selecciona proveedor e ingrediente.");
        if(precio==null||precio.signum()<0) throw new IllegalArgumentException("Precio inválido.");
        if(cantidadReferencia==null||cantidadReferencia.signum()<=0) throw new IllegalArgumentException("Cantidad de referencia inválida.");
        jdbc.update("""
                insert into proveedor_ingredientes(proveedor_id,ingrediente_id,precio_referencia,cantidad_referencia,unidad_compra,updated_at)
                values(?,?,?,?,nullif(btrim(?),''),now())
                on conflict(proveedor_id,ingrediente_id) do update set precio_referencia=excluded.precio_referencia,
                cantidad_referencia=excluded.cantidad_referencia,unidad_compra=excluded.unidad_compra,updated_at=now()
                """,proveedorId,ingredienteId,precio,cantidadReferencia,nvl(unidadCompra));
    }

    @Transactional
    public Long registrarCompra(Long proveedorId,Long usuarioId,String documento,String observacion,List<CompraItem> items) {
        if(proveedorId==null) throw new IllegalArgumentException("Selecciona un proveedor.");
        if(items==null||items.isEmpty()) throw new IllegalArgumentException("Agrega al menos un producto a la compra.");
        List<CompraItem> validos=new ArrayList<>(); BigDecimal total=BigDecimal.ZERO;
        for(CompraItem item:items){
            if(item.ingredienteId()==null||item.cantidadBase()==null||item.cantidadBase().signum()<=0) throw new IllegalArgumentException("Hay una cantidad de compra inválida.");
            if(item.costoTotal()==null||item.costoTotal().signum()<0) throw new IllegalArgumentException("El costo no puede ser negativo.");
            validos.add(item); total=total.add(item.costoTotal());
        }
        total=total.setScale(2,RoundingMode.HALF_UP);
        BigDecimal subtotal=total.divide(new BigDecimal("1.18"),2,RoundingMode.HALF_UP), igv=total.subtract(subtotal).setScale(2,RoundingMode.HALF_UP);
        Long compraId=jdbc.queryForObject("""
                insert into compras(proveedor_id,usuario_id,numero_documento,subtotal,igv,total,observacion,estado)
                values(?,?,nullif(btrim(?),''),?,?,?,nullif(btrim(?),''),'REGISTRADA') returning id
                """,Long.class,proveedorId,usuarioId,nvl(documento),subtotal,igv,total,nvl(observacion));
        if(compraId==null) throw new IllegalStateException("No se pudo registrar la compra.");
        for(CompraItem item:validos){
            BigDecimal unit=item.costoTotal().divide(item.cantidadBase(),6,RoundingMode.HALF_UP);
            jdbc.update("""
                    insert into compra_detalles(compra_id,ingrediente_id,cantidad_compra,unidad_compra,cantidad_base,costo_total,costo_unitario_base)
                    values(?,?,?,?,?,?,?)
                    """,compraId,item.ingredienteId(),item.cantidadCompra(),item.unidadCompra(),item.cantidadBase(),item.costoTotal(),unit);
        }
        return compraId;
    }

    public List<CompraFila> comprasRecientes(int limite) {
        int max=Math.max(1,Math.min(limite,500));
        return jdbc.query("""
                select c.id,c.fecha,p.nombre proveedor,c.numero_documento,c.total,c.estado
                from compras c join proveedores p on p.id=c.proveedor_id order by c.fecha desc limit ?
                """,(rs,n)->new CompraFila(rs.getLong("id"),rs.getTimestamp("fecha").toLocalDateTime(),rs.getString("proveedor"),rs.getString("numero_documento"),rs.getBigDecimal("total"),rs.getString("estado")),max);
    }

    public List<CompraDetalleFila> detallesCompra(Long compraId) {
        if(compraId==null) return List.of();
        return jdbc.query("""
                select i.nombre,cd.cantidad_compra,cd.unidad_compra,cd.cantidad_base,i.unidad_base,cd.costo_total
                from compra_detalles cd join ingredientes i on i.id=cd.ingrediente_id
                where cd.compra_id=? order by cd.id
                """,(rs,n)->new CompraDetalleFila(rs.getString("nombre"),rs.getBigDecimal("cantidad_compra"),rs.getString("unidad_compra"),rs.getBigDecimal("cantidad_base"),rs.getString("unidad_base"),rs.getBigDecimal("costo_total")),compraId);
    }

    private static void validarProveedor(String nombre,String ruc){
        if(nombre==null||nombre.isBlank()) throw new IllegalArgumentException("El nombre del proveedor es obligatorio.");
        if(ruc!=null&&!ruc.isBlank()&&!ruc.trim().matches("[0-9]{11}")) throw new IllegalArgumentException("El RUC debe tener 11 dígitos.");
    }
    private static String nvl(String s){return s==null?"":s;}
}
