package pe.edu.utp.restaurante.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pe.edu.utp.restaurante.model.*;
import pe.edu.utp.restaurante.repository.*;

@Configuration
@Profile("demo")
public class DatosDemo {
    @Bean
    CommandLineRunner cargarDemo(UsuarioRepository usuarios, MesaRepository mesas,
            CategoriaRepository categorias, PlatoRepository platos) {
        return args -> {
            if (usuarios.count() == 0) {
                String[] roles = {"ADMIN", "CAJERO", "MOZO"};
                String[] dnis = {"11111111", "22222222", "33333333"};
                for (int i = 0; i < roles.length; i++) {
                    Usuario u = new Usuario();
                    u.setDni(dnis[i]); u.setNombre(roles[i]); u.setApellido("Demo");
                    u.setRol(roles[i]);
                    u.setPassword("MOZO".equals(roles[i]) ? null : new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("demo12345"));
                    u.setCreatedAt(LocalDateTime.now());
                    usuarios.save(u);
                }
            }
            if (mesas.count() == 0) for (int i = 1; i <= 25; i++) {
                Mesa m = new Mesa(); m.setNumero(i); m.setCreatedAt(LocalDateTime.now());
                if (i == 5) m.setEstado("RESERVADA");
                mesas.save(m);
            }
            if (categorias.count() == 0 && platos.count() == 0) {
                String[][] carta = {{"Alitas", "Alitas BBQ", "28.00"}, {"Bebidas", "Chicha morada", "8.00"},
                    {"Pizzas", "Pizza de la casa", "35.00"}, {"Hamburguesas", "Hamburguesa clásica", "18.00"},
                    {"Postres", "Torta de chocolate", "12.00"}};
                for (String[] fila : carta) {
                    Categoria c = new Categoria(); c.setNombre(fila[0]); c.setCreatedAt(LocalDateTime.now()); categorias.save(c);
                    Plato p = new Plato(); p.setNombre(fila[1]); p.setDescripcion("Porción de demostración");
                    p.setPrecio(new BigDecimal(fila[2])); p.setCategoriaId(c.getId()); p.setCreatedAt(LocalDateTime.now()); platos.save(p);
                }
            }
        };
    }
}
