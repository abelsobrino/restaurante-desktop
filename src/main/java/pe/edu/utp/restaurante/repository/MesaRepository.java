package pe.edu.utp.restaurante.repository;

import pe.edu.utp.restaurante.model.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Mesa m where m.id = :id")
    Optional<Mesa> bloquearPorId(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Mesa> findByNumero(Integer numero);
}
