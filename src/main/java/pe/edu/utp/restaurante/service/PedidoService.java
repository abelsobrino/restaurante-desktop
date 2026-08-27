package pe.edu.utp.restaurante.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.restaurante.model.Pedido;
import pe.edu.utp.restaurante.model.PedidoDetalle;
import pe.edu.utp.restaurante.repository.PedidoDetalleRepository;
import pe.edu.utp.restaurante.repository.PedidoRepository;

import java.util.List;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

    /**
     * Guarda el pedido y reemplaza sus detalles de forma atomica.
     * Toda la logica transaccional vive aqui (fuera de los controladores JavaFX)
     * para que Spring no necesite crear un proxy CGLIB de los controladores,
     * lo cual rompe la inyeccion de campos @FXML.
     */
    @Transactional
    public Pedido guardarPedidoConDetalles(Pedido pedido, List<PedidoDetalle> detalles) {
        Pedido guardado = pedidoRepository.save(pedido);

        pedidoDetalleRepository.deleteByPedidoId(guardado.getId());
        for (PedidoDetalle detalle : detalles) {
            detalle.setPedidoId(guardado.getId());
            pedidoDetalleRepository.save(detalle);
        }

        return guardado;
    }
}