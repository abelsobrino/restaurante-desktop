package pe.edu.utp.restaurante.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import pe.edu.utp.restaurante.repository.UsuarioRepository;
import pe.edu.utp.restaurante.service.UsuarioService;

@Configuration
@Profile("!demo")
public class AdministradorInicial {
    @Bean CommandLineRunner iniciarAdministrador(UsuarioRepository usuarios, UsuarioService personal,
            @Value("${ADMIN_INICIAL_DNI:}") String dni,
            @Value("${ADMIN_INICIAL_CLAVE:}") String clave,
            @Value("${ADMIN_INICIAL_NOMBRE:Administrador}") String nombre,
            @Value("${ADMIN_INICIAL_APELLIDO:Inicial}") String apellido) {
        return args -> {
            if (usuarios.count() != 0) return;
            if (dni.isBlank() || clave.isBlank()) {
                System.err.println("Base sin personal: define ADMIN_INICIAL_DNI y ADMIN_INICIAL_CLAVE para crear el primer administrador.");
                return;
            }
            personal.guardarPersonal(null, dni, nombre, apellido, null, "ADMIN", clave, null);
            System.out.println("Administrador inicial creado. Retira las variables ADMIN_INICIAL_* de la configuración.");
        };
    }
}
