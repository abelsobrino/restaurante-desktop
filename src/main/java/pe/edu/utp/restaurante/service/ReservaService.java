package pe.edu.utp.restaurante.service;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.restaurante.model.Mesa;
import pe.edu.utp.restaurante.repository.MesaRepository;
import pe.edu.utp.restaurante.repository.PedidoRepository;

@Service
public class ReservaService {
    private final JdbcTemplate db;
    private final MesaRepository mesas;
    private final PedidoRepository pedidos;
    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final List<String> ABIERTOS = List.of("PENDIENTE","EN_PROCESO","LISTO","TERMINADO");
    public ReservaService(JdbcTemplate db, MesaRepository mesas, PedidoRepository pedidos) {
        this.db=db; this.mesas=mesas; this.pedidos=pedidos;
    }
    public record Reserva(long id,String nombre,String telefono,LocalDate fecha,LocalTime hora,int personas,String descripcion,String estado,Long mesaId,Integer mesaNumero,Long pedidoId) {
        public LocalDateTime inicio() { return fecha.atTime(hora); }
        public boolean activa() { return Set.of("PENDIENTE","ASIGNADA","EN_ATENCION").contains(estado); }
    }
    public static LocalDateTime ahora() { return LocalDateTime.now(LIMA); }
    public boolean instalado() { return Boolean.TRUE.equals(db.queryForObject("select to_regclass('public.reservas_web') is not null",Boolean.class)); }
    private static final String SELECT="select r.*, m.numero as mesa_numero from public.reservas_web r left join public.mesas m on m.id=r.mesa_id ";
    private List<Reserva> consultar(String condicion,Object... args) {
        return db.query(SELECT+condicion,(r,n)->new Reserva(r.getLong("id"),r.getString("nombre"),r.getString("telefono"),r.getObject("fecha",LocalDate.class),r.getObject("hora",LocalTime.class),r.getInt("personas"),r.getString("descripcion"),r.getString("estado"),r.getObject("mesa_id",Long.class),r.getObject("mesa_numero",Integer.class),r.getObject("pedido_id",Long.class)),args);
    }
    public List<Reserva> delDia(LocalDate fecha) { return consultar("where r.fecha=? order by r.hora,r.id",fecha); }
    public List<Reserva> activas() {
        if (!instalado()) return List.of();
        return consultar("where r.estado in ('ASIGNADA','EN_ATENCION') order by r.fecha,r.hora");
    }
    public String estadoMesa(Mesa mesa,List<Reserva> reservas) {
        var ahora=ahora();
        for (Reserva r:reservas) if (Objects.equals(r.mesaId(),mesa.getId()) && r.estado().equals("EN_ATENCION")) return "RESERVADA - EN ATENCIÓN";
        for (Reserva r:reservas) if (Objects.equals(r.mesaId(),mesa.getId()) && r.estado().equals("ASIGNADA") && ReglasReserva.bloqueada(r.inicio(),ahora))
            return "DISPONIBLE".equals(mesa.getEstado()) ? "RESERVADA" : "OCUPADA · RESERVA PENDIENTE";
        if ("DISPONIBLE".equals(mesa.getEstado()) && reservas.stream().anyMatch(r->Objects.equals(r.mesaId(),mesa.getId()) && r.fecha().equals(ahora.toLocalDate()))) return "RESERVA PRÓXIMA";
        return mesa.getEstado();
    }
    private Reserva bloquear(long id) { return consultar("where r.id=? for update of r",id).stream().findFirst().orElseThrow(()->new IllegalStateException("Reserva inexistente")); }
    private boolean cuentaAbierta(long mesa) { return pedidos.findFirstByMesaIdAndEstadoInOrderByCreatedAtDesc(mesa,ABIERTOS).isPresent(); }
    private boolean solapa(Reserva a,Reserva b) { return ReglasReserva.solapa(a.inicio(),b.inicio()); }
    public List<Mesa> candidatas(Reserva r) {
        List<Reserva> agenda=activas();
        return mesas.findAll().stream().filter(m->m.getCapacidad()!=null && m.getCapacidad()>=r.personas() && "DISPONIBLE".equals(m.getEstado()) && !cuentaAbierta(m.getId()) && agenda.stream().noneMatch(o->o.id()!=r.id() && Objects.equals(o.mesaId(),m.getId()) && (o.estado().equals("EN_ATENCION") || solapa(r,o) || ReglasReserva.bloqueada(o.inicio(),ahora())))).sorted(Comparator.comparing(Mesa::getNumero)).toList();
    }
    @Transactional
    public void asignar(long id,long mesaId) {
        Mesa mesa=mesas.bloquearPorId(mesaId).orElseThrow(); Reserva r=bloquear(id);
        if (!Set.of("PENDIENTE","ASIGNADA").contains(r.estado())) throw new IllegalStateException("La reserva ya no admite asignación");
        if (ReglasReserva.vencida(r.inicio(),ahora())) throw new IllegalStateException("La reserva está vencida. Revisa la asistencia.");
        if(mesa.getCapacidad()==null || mesa.getCapacidad()<r.personas()) throw new IllegalStateException("La capacidad de la mesa es insuficiente");
        if (!"DISPONIBLE".equals(mesa.getEstado()) || cuentaAbierta(mesaId)) throw new IllegalStateException("Mesa no disponible. Elige otra o agrega una mesa física.");
        for(Reserva otra:activas()) if(otra.id()!=id && Objects.equals(otra.mesaId(),mesaId) && (otra.estado().equals("EN_ATENCION") || solapa(r,otra) || ReglasReserva.bloqueada(otra.inicio(),ahora()))) throw new IllegalStateException("La mesa tiene otra reserva incompatible");
        db.update("update public.reservas_web set mesa_id=?,estado='ASIGNADA' where id=?",mesaId,id);
    }
    @Transactional
    public void llegada(long id,long mesaId) {
        Mesa mesa=mesas.bloquearPorId(mesaId).orElseThrow(); Reserva r=bloquear(id);
        if(!r.estado().equals("ASIGNADA") || !Objects.equals(r.mesaId(),mesaId)) throw new IllegalStateException("Actualiza la reserva antes de registrar la llegada");
        if(!ReglasReserva.bloqueada(r.inicio(),ahora())) throw new IllegalStateException("La llegada se registra desde 30 minutos antes");
        if(!"DISPONIBLE".equals(mesa.getEstado()) || cuentaAbierta(mesaId)) throw new IllegalStateException("La mesa está ocupada; debes reasignarla");
        if(activas().stream().anyMatch(o->o.id()!=id && Objects.equals(o.mesaId(),mesaId) && o.estado().equals("EN_ATENCION"))) throw new IllegalStateException("Otra reserva está en atención");
        db.update("update public.reservas_web set estado='EN_ATENCION' where id=?",id);
    }
    @Transactional
    public void cerrar(long id,String estado) {
        Reserva r=bloquear(id);
        if(!r.activa()) throw new IllegalStateException("La reserva ya está cerrada");
        if("NO_ASISTIO".equals(estado) && (!r.estado().equals("ASIGNADA") || !ReglasReserva.vencida(r.inicio(),ahora()))) throw new IllegalStateException("La inasistencia se registra 30 minutos después de la hora");
        if("FINALIZADA".equals(estado) && !r.estado().equals("EN_ATENCION")) throw new IllegalStateException("Primero registra la llegada");
        if("CANCELADA".equals(estado) && r.estado().equals("EN_ATENCION")) throw new IllegalStateException("Finaliza la atención en lugar de cancelarla");
        if(r.pedidoId()!=null && pedidos.findById(r.pedidoId()).map(p->ABIERTOS.contains(p.getEstado())).orElse(false)) throw new IllegalStateException("Cierra la cuenta en caja primero");
        if(!Set.of("NO_ASISTIO","FINALIZADA","CANCELADA").contains(estado)) throw new IllegalArgumentException("Estado inválido");
        db.update("update public.reservas_web set estado=? where id=?",estado,id);
    }
    // Llamar con la mesa bloqueada, dentro de la transacción de creación de cuenta.
    public Reserva validarNuevaCuenta(Mesa mesa) {
        var activas=activas().stream().filter(r->Objects.equals(r.mesaId(),mesa.getId())).toList();
        for(Reserva r:activas) if(r.estado().equals("EN_ATENCION")) {
            Reserva actual=bloquear(r.id());
            if(!actual.estado().equals("EN_ATENCION") || actual.pedidoId()!=null) throw new IllegalStateException("Actualiza el estado de reserva");
            return actual;
        }
        if(activas.stream().anyMatch(r->ReglasReserva.bloqueada(r.inicio(),ahora()))) throw new IllegalStateException("Mesa reservada. El administrador debe registrar la llegada primero.");
        if(!"DISPONIBLE".equals(mesa.getEstado())) throw new IllegalStateException("Mesa no disponible");
        return null;
    }
    public void vincular(Reserva r,long pedido) { if(r!=null) db.update("update public.reservas_web set pedido_id=? where id=?",pedido,r.id()); }
    public void terminarPedido(long pedido,boolean cobrado) {
        if(instalado()) db.update("update public.reservas_web set estado=case when ? then 'FINALIZADA' else 'EN_ATENCION' end, pedido_id=case when ? then pedido_id else null end where pedido_id=? and estado='EN_ATENCION'",cobrado,cobrado,pedido);
    }
    @Transactional
    public void guardarMesa(Mesa propuesta) {
        if(propuesta.getNumero()==null || propuesta.getNumero()<1 || propuesta.getCapacidad()==null || propuesta.getCapacidad()<1 || propuesta.getEstado()==null || !Set.of("DISPONIBLE","OCUPADA","RESERVADA").contains(propuesta.getEstado())) throw new IllegalArgumentException("Número, capacidad y estado válidos son obligatorios");
        if(propuesta.getId()!=null) {
            Mesa actual=mesas.bloquearPorId(propuesta.getId()).orElseThrow();
            if(cuentaAbierta(actual.getId()) || activas().stream().anyMatch(r->Objects.equals(r.mesaId(),actual.getId()))) throw new IllegalStateException("No edites una mesa con cuenta o reserva activa");
        }
        mesas.save(propuesta);
    }
    @Transactional
    public void eliminarMesa(long id) {
        mesas.bloquearPorId(id).orElseThrow();
        if(cuentaAbierta(id) || (instalado() && Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from public.reservas_web where mesa_id=?)",Boolean.class,id)))) throw new IllegalStateException("Mesa con cuenta o historial de reservas: no se puede eliminar");
        mesas.deleteById(id);
    }
}
