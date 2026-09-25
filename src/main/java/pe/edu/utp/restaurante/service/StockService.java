package pe.edu.utp.restaurante.service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StockService {
    private final JdbcTemplate jdbc;

    public StockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Integer stockDisponible(Long platoId) {
        if (platoId == null) return 0;
        Integer valor = jdbc.query(
                "select stock_disponible from vw_platos_stock_hoy where plato_id = ?",
                rs -> rs.next() ? rs.getInt(1) : 0,
                platoId
        );
        return valor == null ? 0 : valor;
    }

    public Map<Long, Integer> stockDisponible(Collection<Long> platoIds) {
        if (platoIds == null || platoIds.isEmpty()) return Collections.emptyMap();
        Map<Long, Integer> resultado = new HashMap<>();
        for (Long id : platoIds) resultado.put(id, 0);
        jdbc.query("select plato_id, stock_disponible from vw_platos_stock_hoy", rs -> {
            long id = rs.getLong("plato_id");
            if (resultado.containsKey(id)) resultado.put(id, rs.getInt("stock_disponible"));
        });
        return resultado;
    }

    public void validarStock(Long platoId, int cantidad, String nombrePlato) {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        int disponible = stockDisponible(platoId);
        if (disponible < cantidad) {
            String nombre = nombrePlato == null || nombrePlato.isBlank() ? "el plato" : nombrePlato;
            throw new IllegalStateException("No hay stock suficiente de " + nombre + ". Disponible: " + disponible + ".");
        }
    }
}
