package pe.edu.utp.restaurante.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CierreCajaService {
    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public CierreCajaService(JdbcTemplate jdbc, AuditoriaService auditoria) { this.jdbc = jdbc; this.auditoria = auditoria; }

    public record Resumen(LocalDateTime inicio, LocalDateTime fin, Map<String, BigDecimal> metodos,
                          BigDecimal total, long operaciones) {}

    private LocalDateTime inicioUltimoCierre(Long usuarioId) {
        LocalDateTime ultimo = jdbc.query("select max(fecha_fin) from caja_cierres where usuario_id = ?",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toLocalDateTime() : null,
                usuarioId);
        return ultimo != null ? ultimo : LocalDateTime.now().toLocalDate().atStartOfDay();
    }

    private Resumen calcular(Long usuarioId, LocalDateTime inicio, LocalDateTime fin) {
        Map<String, BigDecimal> importes = new LinkedHashMap<>();
        for (String metodo : new String[]{"EFECTIVO", "TARJETA", "YAPE", "TRANSFERENCIA", "ONLINE", "OTROS"})
            importes.put(metodo, BigDecimal.ZERO.setScale(2));
        long[] operaciones = {0};
        jdbc.query("select upper(coalesce(metodo,'OTROS')) metodo, coalesce(sum(monto),0) importe, count(*) cantidad " +
                "from pagos where usuario_id = ? and created_at >= ? and created_at < ? group by upper(coalesce(metodo,'OTROS'))",
                rs -> {
                    String metodo = rs.getString("metodo");
                    if (!importes.containsKey(metodo)) metodo = "OTROS";
                    importes.put(metodo, importes.get(metodo).add(rs.getBigDecimal("importe")));
                    operaciones[0] += rs.getLong("cantidad");
                }, usuarioId, Timestamp.valueOf(inicio), Timestamp.valueOf(fin));
        BigDecimal total = importes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new Resumen(inicio, fin, importes, total, operaciones[0]);
    }

    public Resumen consultar(Long usuarioId) {
        if (usuarioId == null) throw new IllegalArgumentException("Inicia sesión para consultar tu caja.");
        LocalDateTime inicio = inicioUltimoCierre(usuarioId);
        LocalDateTime fin = LocalDateTime.now();
        if (!fin.isAfter(inicio)) throw new IllegalStateException("No hay un periodo nuevo para cerrar.");
        return calcular(usuarioId, inicio, fin);
    }

    @Transactional
    public Resumen cerrar(Long usuarioId, BigDecimal contado, String observacion, LocalDateTime inicioVista, LocalDateTime finVista) {
        if (usuarioId == null) throw new IllegalArgumentException("Se requiere un usuario para cerrar caja.");
        if (contado == null || contado.signum() < 0) throw new IllegalArgumentException("El dinero contado no puede ser negativo.");
        jdbc.query("select pg_advisory_xact_lock(?)", rs -> {}, usuarioId);
        LocalDateTime inicio = inicioUltimoCierre(usuarioId);
        if (!inicio.equals(inicioVista) || !finVista.isAfter(inicio) || finVista.isAfter(LocalDateTime.now().plusSeconds(2)))
            throw new IllegalStateException("El periodo cambió. Vuelve a consultar el cierre.");
        Resumen resumen = calcular(usuarioId, inicio, finVista);
        if (resumen.operaciones() == 0) throw new IllegalStateException("No hay cobros nuevos para cerrar.");
        BigDecimal diferencia = contado.subtract(resumen.metodos().get("EFECTIVO")).setScale(2, RoundingMode.HALF_UP);
        if (diferencia.signum() != 0 && (observacion == null || observacion.isBlank()))
            throw new IllegalArgumentException("Explica la diferencia entre lo contado y lo registrado.");
        jdbc.update("insert into caja_cierres(usuario_id,fecha_inicio,fecha_fin,efectivo,tarjeta,yape,transferencia,online,otros,total_sistema,total_contado,diferencia,observacion) " +
                "values (?,?,?,?,?,?,?,?,?,?,?,?,?)", usuarioId, Timestamp.valueOf(inicio), Timestamp.valueOf(finVista),
                resumen.metodos().get("EFECTIVO"), resumen.metodos().get("TARJETA"), resumen.metodos().get("YAPE"),
                resumen.metodos().get("TRANSFERENCIA"), resumen.metodos().get("ONLINE"), resumen.metodos().get("OTROS"),
                resumen.total(), contado.setScale(2, RoundingMode.HALF_UP), diferencia,
                observacion == null ? "" : observacion.trim());
        auditoria.registrar(usuarioId, "CIERRE_CAJA", "caja_cierres", finVista.toString(),
                "Efectivo esperado: " + resumen.metodos().get("EFECTIVO") + "; contado: " + contado + "; diferencia: " + diferencia);
        return resumen;
    }
}
