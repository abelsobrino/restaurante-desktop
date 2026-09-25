package pe.edu.utp.restaurante.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventarioService {
    private final JdbcTemplate jdbc;

    public InventarioService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record IngredienteFila(
            Long id, String nombre, String categoria, String unidadBase,
            BigDecimal stockActual, BigDecimal stockMinimo, BigDecimal costoPromedio,
            boolean descuentoAutomatico, boolean activo
    ) {
        @Override public String toString() {
            return nombre + " — " + formatearCantidad(stockActual, unidadBase)
                    + (descuentoAutomatico ? "" : " — manual");
        }
    }

    public record RecetaFila(Long ingredienteId, String ingrediente, String unidadBase,
                             BigDecimal cantidadBase, boolean descuentoAutomatico) {}

    public record ProduccionFila(Long platoId, String plato, int stockInicial, int stockActual,
                                 int vendidos, String insumosEnCocina) {}

    public record MovimientoFila(LocalDateTime fecha, String ingrediente, String tipo,
                                  BigDecimal cantidad, String unidadBase,
                                  BigDecimal stockAnterior, BigDecimal stockNuevo, String motivo) {}

    public List<IngredienteFila> listarIngredientes() {
        return jdbc.query("""
                select id, nombre, categoria, unidad_base, stock_actual, stock_minimo,
                       costo_promedio, coalesce(descuento_automatico, true) as descuento_automatico, activo
                from ingredientes
                order by activo desc, nombre
                """, (rs, n) -> new IngredienteFila(
                rs.getLong("id"), rs.getString("nombre"), rs.getString("categoria"), rs.getString("unidad_base"),
                rs.getBigDecimal("stock_actual"), rs.getBigDecimal("stock_minimo"), rs.getBigDecimal("costo_promedio"),
                rs.getBoolean("descuento_automatico"), rs.getBoolean("activo")
        ));
    }

    public Long crearIngrediente(String nombre, String categoria, String unidadBase,
                                 BigDecimal stockMinimo, boolean descuentoAutomatico) {
        validarTexto(nombre, "Nombre");
        validarUnidad(unidadBase);
        BigDecimal minimo = stockMinimo == null ? BigDecimal.ZERO : stockMinimo;
        if (minimo.signum() < 0) throw new IllegalArgumentException("El stock mínimo no puede ser negativo.");
        return jdbc.queryForObject("""
                insert into ingredientes(nombre, categoria, unidad_base, stock_minimo, descuento_automatico)
                values (?, nullif(btrim(?), ''), ?, ?, ?)
                returning id
                """, Long.class, nombre.trim(), categoria == null ? "" : categoria, unidadBase, minimo, descuentoAutomatico);
    }

    @Transactional
    public void actualizarIngrediente(Long id, String nombre, String categoria, String unidadBase,
                                      BigDecimal stockMinimo, boolean descuentoAutomatico) {
        if (id == null) throw new IllegalArgumentException("Selecciona un ingrediente.");
        validarTexto(nombre, "Nombre");
        validarUnidad(unidadBase);
        BigDecimal minimo = stockMinimo == null ? BigDecimal.ZERO : stockMinimo;
        if (minimo.signum() < 0) throw new IllegalArgumentException("El stock mínimo no puede ser negativo.");

        var actual = jdbc.queryForMap("select unidad_base, stock_actual from ingredientes where id = ? for update", id);
        String unidadActual = String.valueOf(actual.get("unidad_base"));
        BigDecimal stockActual = (BigDecimal) actual.get("stock_actual");
        if (!unidadActual.equals(unidadBase) && stockActual != null && stockActual.signum() != 0) {
            throw new IllegalStateException("No puedes cambiar la unidad mientras exista stock. Déjalo en 0 o realiza la conversión mediante ajustes antes de cambiarla.");
        }

        int filas = jdbc.update("""
                update ingredientes
                   set nombre = ?, categoria = nullif(btrim(?), ''), unidad_base = ?, stock_minimo = ?,
                       descuento_automatico = ?, updated_at = now()
                 where id = ?
                """, nombre.trim(), categoria == null ? "" : categoria, unidadBase, minimo, descuentoAutomatico, id);
        if (filas == 0) throw new IllegalArgumentException("Ingrediente no encontrado.");
    }

    public void cambiarActivo(Long id, boolean activo) {
        if (id == null) throw new IllegalArgumentException("Selecciona un ingrediente.");
        jdbc.update("update ingredientes set activo = ?, updated_at = now() where id = ?", activo, id);
    }

    @Transactional
    public BigDecimal ajustarStock(Long ingredienteId, BigDecimal delta, String motivo) {
        if (ingredienteId == null) throw new IllegalArgumentException("Selecciona un ingrediente.");
        if (delta == null || delta.signum() == 0) throw new IllegalArgumentException("Ingresa una variación distinta de cero.");
        String texto = motivo == null || motivo.isBlank() ? "Ajuste manual desde administrador" : motivo.trim();
        try {
            return jdbc.queryForObject(
                    "select ajustar_stock_ingrediente(?, ?, ?)", BigDecimal.class,
                    ingredienteId, delta, texto
            );
        } catch (DataAccessException e) {
            throw traducirErrorMigracion(e, "ajustar_stock_ingrediente");
        }
    }

    @Transactional
    public void guardarStockDiario(Long platoId, LocalDate fecha, int cantidadAgregar) {
        if (platoId == null) throw new IllegalArgumentException("Selecciona un plato.");
        if (fecha == null) fecha = LocalDate.now();
        if (!LocalDate.now().equals(fecha)) throw new IllegalArgumentException("El stock operativo se configura para hoy.");
        if (cantidadAgregar <= 0) throw new IllegalArgumentException("La cantidad a agregar debe ser mayor que 0.");
        try {
            jdbc.queryForObject("select agregar_stock_diario(?, ?, ?)", Integer.class,
                    platoId, Date.valueOf(fecha), cantidadAgregar);
        } catch (DataAccessException e) {
            throw traducirErrorMigracion(e, "agregar_stock_diario");
        }
    }

    public Integer obtenerStockDiario(Long platoId, LocalDate fecha) {
        if (platoId == null) return null;
        List<Integer> datos = jdbc.query("""
                select stock_actual from plato_stock_diario
                where plato_id = ? and fecha = ? and activo
                """, (rs, n) -> rs.getInt(1), platoId, Date.valueOf(fecha));
        return datos.isEmpty() ? null : datos.get(0);
    }

    public List<RecetaFila> receta(Long platoId) {
        if (platoId == null) return List.of();
        return jdbc.query("""
                select pi.ingrediente_id, i.nombre, i.unidad_base, pi.cantidad_base,
                       coalesce(i.descuento_automatico, true) as descuento_automatico
                from plato_ingredientes pi
                join ingredientes i on i.id = pi.ingrediente_id
                where pi.plato_id = ?
                order by i.nombre
                """, (rs, n) -> new RecetaFila(
                rs.getLong("ingrediente_id"), rs.getString("nombre"), rs.getString("unidad_base"),
                rs.getBigDecimal("cantidad_base"), rs.getBoolean("descuento_automatico")
        ), platoId);
    }

    @Transactional
    public void guardarIngredienteReceta(Long platoId, Long ingredienteId, BigDecimal cantidadBase) {
        validarEdicionReceta(platoId);
        if (ingredienteId == null) throw new IllegalArgumentException("Selecciona un ingrediente.");
        if (cantidadBase == null || cantidadBase.signum() <= 0)
            throw new IllegalArgumentException("La cantidad de receta debe ser mayor que cero.");
        Boolean automatico = jdbc.queryForObject("select descuento_automatico from ingredientes where id = ?", Boolean.class, ingredienteId);
        if (Boolean.FALSE.equals(automatico)) {
            throw new IllegalStateException("Este ingrediente está marcado como consumo MANUAL. No se agrega a una receta automática; descuéntalo desde Inventario cuando corresponda.");
        }
        jdbc.update("""
                insert into plato_ingredientes(plato_id, ingrediente_id, cantidad_base)
                values (?, ?, ?)
                on conflict (plato_id, ingrediente_id) do update set cantidad_base = excluded.cantidad_base
                """, platoId, ingredienteId, cantidadBase);
    }

    @Transactional
    public void eliminarIngredienteReceta(Long platoId, Long ingredienteId) {
        validarEdicionReceta(platoId);
        if (ingredienteId == null) return;
        jdbc.update("delete from plato_ingredientes where plato_id = ? and ingrediente_id = ?", platoId, ingredienteId);
    }

    private void validarEdicionReceta(Long platoId) {
        if (platoId == null) throw new IllegalArgumentException("Selecciona un plato.");
        Integer configuradoHoy = jdbc.queryForObject("""
                select count(*) from plato_stock_diario
                where plato_id = ?
                  and fecha = (now() at time zone 'America/Lima')::date
                  and stock_inicial > 0
                """, Integer.class, platoId);
        if (configuradoHoy != null && configuradoHoy > 0) {
            throw new IllegalStateException("La receta no se puede modificar después de habilitar stock para hoy. Haz el cambio antes de producir stock o aplícalo desde mañana.");
        }
    }

    public List<IngredienteFila> stockBajo() {
        return jdbc.query("""
                select id, nombre, categoria, unidad_base, stock_actual, stock_minimo,
                       costo_promedio, coalesce(descuento_automatico, true) as descuento_automatico, activo
                from ingredientes
                where activo and stock_actual <= stock_minimo
                order by stock_actual asc, nombre
                """, (rs, n) -> new IngredienteFila(
                rs.getLong("id"), rs.getString("nombre"), rs.getString("categoria"), rs.getString("unidad_base"),
                rs.getBigDecimal("stock_actual"), rs.getBigDecimal("stock_minimo"), rs.getBigDecimal("costo_promedio"),
                rs.getBoolean("descuento_automatico"), rs.getBoolean("activo")
        ));
    }

    public List<ProduccionFila> produccionHoy() {
        return jdbc.query("""
                select psd.plato_id, p.nombre as plato, psd.stock_inicial, psd.stock_actual,
                       greatest(psd.stock_inicial - psd.stock_actual, 0) as vendidos,
                       coalesce(string_agg(
                           case when i.id is null then null else
                               i.nombre || ': ' || regexp_replace(round(pi.cantidad_base * psd.stock_actual, 3)::text, '\\.?0+$', '') || ' ' ||
                               case i.unidad_base when 'GRAMO' then 'g' when 'KILOGRAMO' then 'kg'
                                    when 'MILILITRO' then 'ml' when 'LITRO' then 'L' else 'und' end
                           end,
                           ' | ' order by i.nombre
                       ), 'Sin receta automática') as insumos_en_cocina
                from plato_stock_diario psd
                join platos p on p.id = psd.plato_id
                left join plato_ingredientes pi on pi.plato_id = p.id
                left join ingredientes i on i.id = pi.ingrediente_id and coalesce(i.descuento_automatico, true)
                where psd.fecha = (now() at time zone 'America/Lima')::date and psd.activo
                group by psd.plato_id, p.nombre, psd.stock_inicial, psd.stock_actual
                order by p.nombre
                """, (rs, n) -> new ProduccionFila(
                rs.getLong("plato_id"), rs.getString("plato"), rs.getInt("stock_inicial"),
                rs.getInt("stock_actual"), rs.getInt("vendidos"), rs.getString("insumos_en_cocina")
        ));
    }

    public List<MovimientoFila> movimientosRecientes(int limite) {
        int max = Math.max(1, Math.min(limite, 1000));
        return jdbc.query("""
                select m.created_at, i.nombre as ingrediente, m.tipo, m.cantidad, i.unidad_base,
                       m.stock_anterior, m.stock_nuevo, m.motivo
                from inventario_movimientos m
                join ingredientes i on i.id = m.ingrediente_id
                order by m.created_at desc
                limit ?
                """, (rs, n) -> new MovimientoFila(
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("ingrediente"), rs.getString("tipo"),
                rs.getBigDecimal("cantidad"), rs.getString("unidad_base"), rs.getBigDecimal("stock_anterior"),
                rs.getBigDecimal("stock_nuevo"), rs.getString("motivo")
        ), max);
    }

    private RuntimeException traducirErrorMigracion(DataAccessException e, String funcion) {
        Throwable actual = e;
        while (actual != null) {
            String mensaje = actual.getMessage();
            if (mensaje != null && (mensaje.contains(funcion) && (mensaje.contains("does not exist") || mensaje.contains("no existe")))) {
                String script = "agregar_stock_diario".equals(funcion) ? "database/08_stock_acumulativo_v5.sql" : "database/07_mejoras_gestion_v4.sql";
                return new IllegalStateException("Falta actualizar la base de datos. Ejecuta " + script + " en Supabase y vuelve a intentar.", e);
            }
            if (actual instanceof SQLException sql && "P0001".equals(sql.getSQLState())) {
                String limpio = mensaje == null ? "La base de datos rechazó la operación." : mensaje.lines().findFirst().orElse(mensaje);
                if (limpio.startsWith("ERROR:")) limpio = limpio.substring(6).trim();
                return new IllegalStateException(limpio, e);
            }
            actual = actual.getCause();
        }
        return new IllegalStateException("No se pudo actualizar inventario/stock: " + causaRaiz(e), e);
    }


    private static String causaRaiz(Throwable e) {
        Throwable x=e; while(x.getCause()!=null) x=x.getCause();
        return x.getMessage()==null ? "error de base de datos" : x.getMessage();
    }

    private static void validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(campo + " es obligatorio.");
    }

    private static void validarUnidad(String unidad) {
        if (!List.of("UNIDAD", "GRAMO", "KILOGRAMO", "MILILITRO", "LITRO").contains(unidad))
            throw new IllegalArgumentException("Unidad base no válida.");
    }

    public static String abreviar(String unidad) {
        if (unidad == null) return "";
        return switch (unidad) {
            case "GRAMO" -> "g";
            case "KILOGRAMO" -> "kg";
            case "MILILITRO" -> "ml";
            case "LITRO" -> "L";
            default -> "und";
        };
    }

    public static String formatearCantidad(BigDecimal cantidad, String unidad) {
        BigDecimal v = cantidad == null ? BigDecimal.ZERO : cantidad;
        return v.stripTrailingZeros().toPlainString() + " " + abreviar(unidad);
    }
}
