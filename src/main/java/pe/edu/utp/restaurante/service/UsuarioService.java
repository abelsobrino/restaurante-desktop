package pe.edu.utp.restaurante.service;

import pe.edu.utp.restaurante.model.Usuario;
import pe.edu.utp.restaurante.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public Optional<Usuario> autenticar(String dni, String password) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByDni(dni);
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            if (usuario.getActivo() != null && usuario.getActivo() &&
                    password.equals(usuario.getPassword())) {
                return Optional.of(usuario);
            }
        }
        return Optional.empty();
    }

    public Optional<Usuario> buscarPorDni(String dni) {
        return usuarioRepository.findByDni(dni);
    }
}