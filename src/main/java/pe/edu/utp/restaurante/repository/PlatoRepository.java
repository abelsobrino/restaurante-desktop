package pe.edu.utp.restaurante.repository;

import pe.edu.utp.restaurante.model.Plato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatoRepository extends JpaRepository<Plato, Long> {
    List<Plato> findByDisponibleTrue();
}