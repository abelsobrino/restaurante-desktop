package pe.edu.utp.restaurante.service;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utp.restaurante.model.*;
import pe.edu.utp.restaurante.repository.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CobroServiceTest {
    @Mock PedidoRepository pedidos;
    @Mock PagoRepository pagos;
    @Mock MesaRepository mesas;
    @InjectMocks CobroService servicio;

    @Test void rechazaSegundoCobro() {
        Pedido pedido = new Pedido(); pedido.setEstado("ENTREGADO");
        when(pedidos.bloquearPorId(1L)).thenReturn(Optional.of(pedido));
        assertThrows(IllegalStateException.class, () -> servicio.cobrar(1L, 2L, "EFECTIVO", ""));
        verify(pagos, never()).save(any());
    }

    @Test void registraPagoYCierraPedido() {
        Pedido pedido = new Pedido(); pedido.setId(1L); pedido.setEstado("TERMINADO"); pedido.setTotal(new BigDecimal("118.00"));
        when(pedidos.bloquearPorId(1L)).thenReturn(Optional.of(pedido));
        Pago pago = servicio.cobrar(1L, 2L, "EFECTIVO", "");
        assertEquals(new BigDecimal("118.00"), pago.getMonto());
        assertEquals("ENTREGADO", pedido.getEstado());
        assertTrue(pedido.getCerrado());
        verify(pagos).save(pago);
        verify(pedidos).saveAndFlush(pedido);
    }
}
