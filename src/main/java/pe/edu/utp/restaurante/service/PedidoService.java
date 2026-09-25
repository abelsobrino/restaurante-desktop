package pe.edu.utp.restaurante.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.restaurante.model.*;
import pe.edu.utp.restaurante.repository.*;

@Service
public class PedidoService {
    private static final List<String> ABIERTOS = List.of("PENDIENTE", "EN_PROCESO", "LISTO");
    @Autowired private MesaRepository mesaRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private PedidoDetalleRepository pedidoDetalleRepository;
    @Autowired private PlatoRepository platoRepository;
    @Autowired private StockService stockService;

    public static boolean pendienteCocina(PedidoDetalle d) {
        return "PENDIENTE".equals(d.getEstado()) || "EN_PROCESO".equals(d.getEstado());
    }

    // Ahora agrega solo renglones SIN ID; nunca borra los anteriores.
    @Transactional
    public Pedido guardarPedidoConDetalles(Pedido solicitud, List<PedidoDetalle> vista) {
        List<PedidoDetalle> nuevos = vista.stream().filter(d -> d.getId() == null).toList();
        if (nuevos.isEmpty()) throw new IllegalArgumentException("No hay productos nuevos para enviar.");
        Pedido cuenta;
        if (solicitud.getId() == null) {
            if (solicitud.getMesaId() == null || solicitud.getUsuarioId() == null)
                throw new IllegalArgumentException("Selecciona mesa e inicia sesión como mozo.");
            Mesa mesa = mesaRepository.bloquearPorId(solicitud.getMesaId()).orElseThrow();
            if (pedidoRepository.findFirstByMesaIdAndEstadoInOrderByCreatedAtDesc(
                    mesa.getId(), List.of("PENDIENTE", "EN_PROCESO", "LISTO", "TERMINADO")).isPresent())
                throw new IllegalStateException("La mesa ya tiene una cuenta. Actualiza antes de enviar.");
            cuenta = new Pedido();
            cuenta.setCodigo("PED-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            cuenta.setMesaId(mesa.getId());
            cuenta.setUsuarioId(solicitud.getUsuarioId());
            cuenta.setTipo("LOCAL");
            cuenta.setOrigen("PRESENCIAL");
            cuenta.setEstado("EN_PROCESO");
            cuenta.setCreatedAt(LocalDateTime.now());
            cuenta = pedidoRepository.saveAndFlush(cuenta);
        } else {
            cuenta = pedidoRepository.bloquearPorId(solicitud.getId()).orElseThrow();
            comprobarAbierto(cuenta);
            if (!Objects.equals(cuenta.getMesaId(), solicitud.getMesaId()))
                throw new IllegalStateException("La cuenta no corresponde a la mesa seleccionada. Actualiza las mesas.");
            normalizarListoAnterior(cuenta);
        }
        String observacion = solicitud.getObservacionExtra();
        if (observacion != null && observacion.length() > 255)
            throw new IllegalArgumentException("La observación admite hasta 255 caracteres.");
        for (PedidoDetalle borrador : nuevos) {
            if (borrador.getCantidad() == null || borrador.getCantidad() <= 0)
                throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
            Plato plato = platoRepository.findById(borrador.getPlatoId()).orElseThrow();
            if (!Boolean.TRUE.equals(plato.getDisponible()))
                throw new IllegalArgumentException(plato.getNombre() + " ya no está disponible.");
            if (plato.getPrecio() == null || plato.getPrecio().signum() <= 0)
                throw new IllegalArgumentException("El plato no tiene un precio válido.");
            if (borrador.getPrecioUnitario() == null || plato.getPrecio().compareTo(borrador.getPrecioUnitario()) != 0)
                throw new IllegalStateException("Cambió el precio de " + plato.getNombre() + ". Descarta los nuevos y actualiza la carta.");
            // Validación amigable. El descuento definitivo se hace en PostgreSQL al insertar el detalle.
            stockService.validarStock(plato.getId(), borrador.getCantidad(), plato.getNombre());
            PedidoDetalle linea = new PedidoDetalle();
            linea.setPedidoId(cuenta.getId());
            linea.setPlatoId(plato.getId());
            linea.setCantidad(borrador.getCantidad());
            linea.setPrecioUnitario(plato.getPrecio());
            linea.setSubtotal(plato.getPrecio().multiply(BigDecimal.valueOf(linea.getCantidad())).setScale(2, RoundingMode.HALF_UP));
            linea.setObservacionExtra(observacion);
            linea.setEstado("EN_PROCESO");
            linea.setCreatedAt(LocalDateTime.now());
            linea.setUpdatedAt(LocalDateTime.now());
            pedidoDetalleRepository.save(linea);
        }
        pedidoDetalleRepository.flush();
        cuenta.setEstado("EN_PROCESO");
        cuenta.setCerrado(false);
        cuenta.setObservacionExtra(observacion);
        actualizarTotal(cuenta);
        cuenta.setUpdatedAt(LocalDateTime.now());
        pedidoRepository.saveAndFlush(cuenta);
        mesaRepository.findById(cuenta.getMesaId()).ifPresent(m -> {
            m.setEstado("OCUPADA");
            mesaRepository.save(m);
        });
        return cuenta;
    }

    @Transactional
    public List<PedidoDetalle> consultarDetalle(Long pedidoId) {
        Pedido cuenta = pedidoRepository.bloquearPorId(pedidoId).orElseThrow();
        normalizarListoAnterior(cuenta);
        return detalleOrdenado(pedidoId);
    }

    // Antes se marcaba LISTO solo en cabecera. Corrige esos detalles al leer/agregar.
    private void normalizarListoAnterior(Pedido cuenta) {
        if (!"LISTO".equals(cuenta.getEstado())) return;
        for (PedidoDetalle d : pedidoDetalleRepository.findByPedidoId(cuenta.getId())) {
            if (pendienteCocina(d)) {
                d.setEstado("LISTO");
                d.setUpdatedAt(LocalDateTime.now());
                pedidoDetalleRepository.save(d);
            }
        }
        pedidoDetalleRepository.flush();
    }

    @Transactional
    public void cambiarEstadoCocina(Long pedidoId, Collection<Long> idsVisibles, String estado) {
        if (!List.of("EN_PROCESO", "LISTO").contains(estado))
            throw new IllegalArgumentException("Estado de cocina no válido.");
        Pedido cuenta = pedidoRepository.bloquearPorId(pedidoId).orElseThrow();
        if (!ABIERTOS.contains(cuenta.getEstado()) && !"TERMINADO".equals(cuenta.getEstado()))
            throw new IllegalStateException("La cuenta está pagada o cancelada. Actualiza la cocina.");
        normalizarListoAnterior(cuenta);
        Set<Long> ids = new HashSet<>(idsVisibles);
        int modificados = 0;
        for (PedidoDetalle d : detalleOrdenado(pedidoId)) {
            if (ids.contains(d.getId()) && pendienteCocina(d)) {
                d.setEstado(estado);
                d.setUpdatedAt(LocalDateTime.now());
                pedidoDetalleRepository.save(d);
                modificados++;
            }
        }
        pedidoDetalleRepository.flush();
        if (modificados == 0)
            throw new IllegalStateException("Estos productos ya fueron atendidos. Actualiza la lista.");
        if (!"TERMINADO".equals(cuenta.getEstado())) {
            boolean pendientes = detalleOrdenado(pedidoId).stream().anyMatch(PedidoService::pendienteCocina);
            cuenta.setEstado(pendientes ? "EN_PROCESO" : "LISTO");
            cuenta.setUpdatedAt(LocalDateTime.now());
            pedidoRepository.saveAndFlush(cuenta);
        }
    }

    @Transactional
    public Pedido terminarCuenta(Long pedidoId) {
        Pedido cuenta = pedidoRepository.bloquearPorId(pedidoId).orElseThrow();
        comprobarAbierto(cuenta);
        normalizarListoAnterior(cuenta);
        if (detalleOrdenado(pedidoId).isEmpty()) throw new IllegalStateException("La cuenta está vacía.");
        actualizarTotal(cuenta);
        cuenta.setEstado("TERMINADO");
        cuenta.setCerrado(true);
        cuenta.setFechaCierre(LocalDateTime.now());
        cuenta.setUpdatedAt(LocalDateTime.now());
        return pedidoRepository.saveAndFlush(cuenta);
    }

    private void actualizarTotal(Pedido cuenta) {
        BigDecimal total = detalleOrdenado(cuenta.getId()).stream()
                .map(PedidoDetalle::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        cuenta.setTotal(total);
        cuenta.setSubtotal(total.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP));
    }

    private List<PedidoDetalle> detalleOrdenado(Long id) {
        return pedidoDetalleRepository.findByPedidoId(id).stream()
                .sorted(Comparator.comparing(PedidoDetalle::getId)).toList();
    }

    private void comprobarAbierto(Pedido cuenta) {
        if (!ABIERTOS.contains(cuenta.getEstado()))
            throw new IllegalStateException("La cuenta ya está en caja, pagada o cancelada. Actualiza las mesas.");
    }

    @Transactional
    public void cancelarPedido(Long pedidoId) {
        if (pedidoId == null) return;
        Pedido cuenta = pedidoRepository.bloquearPorId(pedidoId).orElseThrow();
        comprobarAbierto(cuenta);
        cuenta.setEstado("CANCELADO");
        cuenta.setCerrado(true);
        cuenta.setUpdatedAt(LocalDateTime.now());
        pedidoRepository.saveAndFlush(cuenta);
        if (cuenta.getMesaId() != null && pedidoRepository.findFirstByMesaIdAndEstadoInOrderByCreatedAtDesc(
                cuenta.getMesaId(), List.of("PENDIENTE", "EN_PROCESO", "LISTO", "TERMINADO")).isEmpty()) {
            mesaRepository.findById(cuenta.getMesaId()).ifPresent(m -> {
                m.setEstado("DISPONIBLE");
                mesaRepository.save(m);
            });
        }
    }
}
