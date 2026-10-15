package pe.edu.utp.restaurante.service;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {
    private final JdbcTemplate jdbc;
    public AuditoriaService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean disponible() {
        return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('public.escritorio_auditoria') is not null", Boolean.class));
    }

    public void registrar(Long usuarioId, String accion, String entidad, String referencia, String detalle) {
        if (!disponible()) return;
        jdbc.update("insert into escritorio_auditoria(usuario_id,accion,entidad,referencia,detalle) values(?,?,?,?,?)",
                usuarioId, accion, entidad, referencia, detalle);
    }

    public List<String> recientes() {
        if (!disponible()) throw new IllegalStateException("Primero ejecuta el script SQL de auditoría en Supabase.");
        return jdbc.query("select a.created_at, coalesce(u.nombre||' '||u.apellido,'Sin usuario') usuario, a.accion, a.entidad, a.referencia, a.detalle " +
                "from escritorio_auditoria a left join usuarios u on u.id=a.usuario_id order by a.created_at desc limit 100",
                (rs,n) -> rs.getTimestamp("created_at") + "  |  " + rs.getString("usuario") + "  |  " +
                        rs.getString("accion") + "  |  " + rs.getString("entidad") + " " +
                        rs.getString("referencia") + "  |  " + rs.getString("detalle"));
    }
}
