package pe.edu.utp.restaurante.repository;

import pe.edu.utp.restaurante.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    Optional<Pedido> findFirstByMesaIdAndEstadoInOrderByCreatedAtDesc(Long mesaId, List<String> estados);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> bloquearPorId(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Pedido> findByMesaIdAndEstadoNot(Long mesaId, String estado);
    List<Pedido> findByEstado(String estado);
    List<Pedido> findByMesaIdAndEstado(Long mesaId, String estado);
    List<Pedido> findByOrigenOrderByCreatedAtDesc(String origen);

    @org.springframework.data.jpa.repository.Query(value = """
            select p.* from pedidos p
            where p.origen = 'WEB' and p.estado <> 'CANCELADO'
              and exists (select 1 from pagos pg where pg.pedido_id = p.id)
            order by p.created_at desc
            limit 100
            """, nativeQuery = true)
    List<Pedido> findPedidosWebPagados();
}
