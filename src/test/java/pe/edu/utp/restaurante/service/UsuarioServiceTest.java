package pe.edu.utp.restaurante.service;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pe.edu.utp.restaurante.model.Usuario;
import pe.edu.utp.restaurante.repository.UsuarioRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
    @Mock UsuarioRepository repo;
    @InjectMocks UsuarioService servicio;

    @Test void dniInvalidoNoGuarda() {
        assertThrows(IllegalArgumentException.class, () -> servicio.guardarPersonal(null,"123","Ana","Perez",null,"MOZO","",null));
        verify(repo, never()).saveAndFlush(any());
    }
    @Test void dniDuplicadoNoSobrescribe() {
        Usuario previo = new Usuario(); previo.setId(10L); previo.setDni("12345678");
        when(repo.findByDni("12345678")).thenReturn(Optional.of(previo));
        assertThrows(IllegalArgumentException.class, () -> servicio.guardarPersonal(null,"12345678","Ana","Perez",null,"MOZO","",null));
        verify(repo, never()).saveAndFlush(any());
    }
    @Test void correoInvalidoNoGuarda() {
        assertThrows(IllegalArgumentException.class, () -> servicio.guardarPersonal(null,"12345678","Ana","Perez","incorrecto","MOZO","",null));
    }
    @Test void cajeroRequiereClave() {
        assertThrows(IllegalArgumentException.class, () -> servicio.guardarPersonal(null,"12345678","Ana","Perez",null,"CAJERO","",null));
    }
    @Test void mozoNoTieneClave() {
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        Usuario u = servicio.guardarPersonal(null,"12345678","Ana","Perez","","MOZO","",null);
        assertNull(u.getPassword()); assertNull(u.getEmail()); assertTrue(u.getActivo());
    }
    @Test void cajeroGuardaHashYNormalizaCorreo() {
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        Usuario u = servicio.guardarPersonal(null,"12345678","Ana","Perez"," ANA@GMAIL.COM ","CAJERO","ClaveDemo2026",null);
        assertEquals("ana@gmail.com",u.getEmail());
        assertNotEquals("ClaveDemo2026",u.getPassword());
        assertTrue(new BCryptPasswordEncoder().matches("ClaveDemo2026",u.getPassword()));
    }
    @Test void claveIncorrectaNoAutentica() {
        Usuario u = new Usuario(); u.setPassword(new BCryptPasswordEncoder().encode("correcta123")); u.setActivo(true);
        when(repo.findByDni("12345678")).thenReturn(Optional.of(u));
        assertTrue(servicio.autenticar("12345678","incorrecta").isEmpty());
    }
    @Test void inactivoNoAutenticaAunqueClaveSeaCorrecta() {
        Usuario u = new Usuario(); u.setPassword(new BCryptPasswordEncoder().encode("correcta123")); u.setActivo(false);
        when(repo.findByDni("12345678")).thenReturn(Optional.of(u));
        assertTrue(servicio.autenticar("12345678","correcta123").isEmpty());
    }
}
