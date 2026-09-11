package pe.edu.utp.restaurante.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.restaurante.model.*;
import pe.edu.utp.restaurante.repository.*;

@Service
public class CobroService {
    @Autowired private PedidoRepository pedidos;
    @Autowired private PagoRepository pagos;
    @Autowired private MesaRepository mesas;

    @Transactional
    public Pago cobrar(Long pedidoId, Long cajeroId, String metodo, String referencia) {
        if (!List.of("EFECTIVO", "TARJETA", "YAPE", "TRANSFERENCIA", "OTRO").contains(metodo))
            throw new IllegalArgumentException("Selecciona un método de pago válido.");
        if (referencia != null && referencia.length() > 50)
            throw new IllegalArgumentException("La referencia admite como máximo 50 caracteres.");
        Pedido pedido = pedidos.bloquearPorId(pedidoId).orElseThrow();
        if (!"TERMINADO".equals(pedido.getEstado()) || pagos.existsByPedidoId(pedidoId))
            throw new IllegalStateException("Este pedido ya se cobró o no está listo para caja. Actualiza la lista.");
        if (pedido.getTotal() == null || pedido.getTotal().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalStateException("El total del pedido debe ser mayor que cero.");
        Pago pago = new Pago();
        pago.setPedidoId(pedidoId);
        pago.setMonto(pedido.getTotal());
        pago.setMetodo(metodo);
        pago.setReferencia(referencia);
        pago.setUsuarioId(cajeroId);
        pago.setCreatedAt(LocalDateTime.now());
        pagos.save(pago);
        pedido.setEstado("ENTREGADO");
        pedido.setCerrado(true);
        pedido.setFechaCierre(pago.getCreatedAt());
        pedido.setUpdatedAt(pago.getCreatedAt());
        pedidos.saveAndFlush(pedido);
        if (pedido.getMesaId() != null && pedidos.findFirstByMesaIdAndEstadoInOrderByCreatedAtDesc(
                pedido.getMesaId(), List.of("PENDIENTE", "EN_PROCESO", "LISTO", "TERMINADO")).isEmpty()) {
            mesas.findById(pedido.getMesaId()).ifPresent(m -> { m.setEstado("DISPONIBLE"); mesas.save(m); });
        }
        return pago;
    }
}
