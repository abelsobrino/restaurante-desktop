package pe.edu.utp.restaurante.repository;

import pe.edu.utp.restaurante.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    Optional<Pedido> findByMesaIdAndEstadoNot(Long mesaId, String estado);

    List<Pedido> findByEstadoAndCerrado(String estado, Boolean cerrado);

    List<Pedido> findByEstado(String estado);

    List<Pedido> findByMesaId(Long mesaId);

    List<Pedido> findByMesaIdAndEstado(Long mesaId, String estado);
}