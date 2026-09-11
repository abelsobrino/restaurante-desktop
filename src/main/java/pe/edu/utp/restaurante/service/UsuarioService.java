package pe.edu.utp.restaurante.service;

import pe.edu.utp.restaurante.model.Usuario;
import pe.edu.utp.restaurante.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsuarioService {
    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);

    @Autowired
    private UsuarioRepository usuarioRepository;

    public Optional<Usuario> autenticar(String dni, String password) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByDni(dni);
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            if (usuario.getActivo() != null && usuario.getActivo() &&
                    usuario.getPassword() != null && password != null &&
                    encoder.matches(password, usuario.getPassword())) {
                return Optional.of(usuario);
            }
        }
        return Optional.empty();
    }

    public Optional<Usuario> buscarPorDni(String dni) {
        return usuarioRepository.findByDni(dni);
    }

    @org.springframework.transaction.annotation.Transactional
    public Usuario guardarPersonal(Long id, String dni, String nombre, String apellido,
            String email, String rol, String clave, Long actorId) {
        dni = dni == null ? "" : dni.trim();
        nombre = nombre == null ? "" : nombre.trim();
        apellido = apellido == null ? "" : apellido.trim();
        email = email == null || email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!dni.matches("[0-9]{8}")) throw new IllegalArgumentException("El DNI debe contener exactamente 8 dígitos.");
        if (nombre.isBlank() || nombre.length() > 100) throw new IllegalArgumentException("Ingresa el nombre (máximo 100 caracteres).");
        if (apellido.isBlank() || apellido.length() > 100) throw new IllegalArgumentException("Ingresa el apellido (máximo 100 caracteres).");
        if (email != null && (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
            throw new IllegalArgumentException("El correo no tiene un formato válido.");
        if (rol == null || !java.util.List.of("MOZO", "CAJERO", "ADMIN", "COCINERO").contains(rol))
            throw new IllegalArgumentException("Selecciona un rol válido.");
        Usuario u = id == null ? new Usuario() : usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El trabajador ya no existe. Actualiza la lista."));
        if (id != null && !u.getDni().equals(dni)) throw new IllegalArgumentException("El DNI no se cambia al editar un trabajador.");
        var duplicado = usuarioRepository.findByDni(dni);
        if (duplicado.isPresent() && !java.util.Objects.equals(duplicado.get().getId(), id))
            throw new IllegalArgumentException("Ese DNI ya está registrado. Selecciona al trabajador para editarlo.");
        if (email != null) {
            var otro = usuarioRepository.findByEmailIgnoreCase(email);
            if (otro.isPresent() && !java.util.Objects.equals(otro.get().getId(), id))
                throw new IllegalArgumentException("El correo ya pertenece a otro trabajador.");
        }
        if (id != null && "ADMIN".equals(u.getRol()) && !"ADMIN".equals(rol)) {
            if (java.util.Objects.equals(id, actorId)) throw new IllegalArgumentException("No puedes quitarte tu propio rol de administrador.");
            if (Boolean.TRUE.equals(u.getActivo()) && usuarioRepository.countByRolAndActivoTrue("ADMIN") <= 1)
                throw new IllegalArgumentException("Debe quedar por lo menos un administrador activo.");
        }
        if ("MOZO".equals(rol)) {
            u.setPassword(null);
        } else if (clave != null && !clave.isEmpty()) {
            if (clave.isBlank() || clave.length() < 8 || clave.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres y como máximo 72 bytes UTF-8.");
            u.setPassword(encoder.encode(clave));
        } else if (id == null || u.getPassword() == null || !u.getPassword().startsWith("$2")) {
            throw new IllegalArgumentException("Este rol requiere una contraseña nueva de al menos 8 caracteres.");
        }
        u.setDni(dni); u.setNombre(nombre); u.setApellido(apellido); u.setEmail(email); u.setRol(rol);
        if (id == null) { u.setActivo(true); u.setCreatedAt(java.time.LocalDateTime.now()); }
        u.setUpdatedAt(java.time.LocalDateTime.now());
        return usuarioRepository.saveAndFlush(u);
    }

    @org.springframework.transaction.annotation.Transactional
    public void cambiarActivo(Long id, Long actorId) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        if (java.util.Objects.equals(id, actorId)) throw new IllegalArgumentException("No puedes darte de baja a ti mismo.");
        if (Boolean.TRUE.equals(u.getActivo()) && "ADMIN".equals(u.getRol()) && usuarioRepository.countByRolAndActivoTrue("ADMIN") <= 1)
            throw new IllegalArgumentException("Debe quedar un administrador activo.");
        u.setActivo(!Boolean.TRUE.equals(u.getActivo()));
        u.setUpdatedAt(java.time.LocalDateTime.now());
        usuarioRepository.saveAndFlush(u);
    }
}
