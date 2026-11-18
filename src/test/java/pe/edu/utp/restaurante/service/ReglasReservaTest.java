package pe.edu.utp.restaurante.service;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReglasReservaTest {
    private final LocalDateTime hora=LocalDateTime.of(2026,11,17,19,0);
    @Test void bloqueoExacto(){assertFalse(ReglasReserva.bloqueada(hora,hora.minusMinutes(31))); assertTrue(ReglasReserva.bloqueada(hora,hora.minusMinutes(30)));}
    @Test void cruceYLimite(){assertTrue(ReglasReserva.solapa(hora,hora.plusMinutes(119))); assertFalse(ReglasReserva.solapa(hora,hora.plusMinutes(120)));}
    @Test void tolerancia(){assertFalse(ReglasReserva.vencida(hora,hora.plusMinutes(29))); assertTrue(ReglasReserva.vencida(hora,hora.plusMinutes(30)));}
    @Test void cruceMedianoche(){var tarde=hora.withHour(23); assertTrue(ReglasReserva.solapa(tarde,tarde.plusHours(1)));}
}
