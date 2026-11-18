package pe.edu.utp.restaurante.service;
import java.time.LocalDateTime;
/** Reglas de horario independientes de JavaFX y PostgreSQL. */
public final class ReglasReserva {
    private ReglasReserva() {}
    public static final int ANTICIPACION=30, DURACION=90, TOLERANCIA=30;
    public static boolean bloqueada(LocalDateTime inicio,LocalDateTime ahora){return !ahora.isBefore(inicio.minusMinutes(ANTICIPACION));}
    public static boolean solapa(LocalDateTime a,LocalDateTime b){return a.minusMinutes(ANTICIPACION).isBefore(b.plusMinutes(DURACION)) && b.minusMinutes(ANTICIPACION).isBefore(a.plusMinutes(DURACION));}
    public static boolean vencida(LocalDateTime inicio,LocalDateTime ahora){return !ahora.isBefore(inicio.plusMinutes(TOLERANCIA));}
}
