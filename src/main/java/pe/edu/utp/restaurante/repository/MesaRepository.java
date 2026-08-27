package pe.edu.utp.restaurante.repository;

import pe.edu.utp.restaurante.model.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {
    List<Mesa> findByEstado(String estado);
    Optional<Mesa> findByNumero(Integer numero);
}