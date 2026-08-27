package pe.edu.utp.restaurante.repository;

import pe.edu.utp.restaurante.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByDni(String dni);

    Optional<Usuario> findByEmail(String email);

    boolean existsByDni(String dni);
}