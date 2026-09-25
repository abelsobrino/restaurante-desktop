package pe.edu.utp.restaurante.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogoOpcionesService {
    private final JdbcTemplate jdbc;
    private final InventarioService inventarioService;

    public CatalogoOpcionesService(JdbcTemplate jdbc, InventarioService inventarioService) {
        this.jdbc = jdbc;
        this.inventarioService = inventarioService;
    }

    public record CategoriaFila(Long id, String nombre) {
        @Override public String toString() { return nombre; }
    }

    public record FamiliaCarta(
            Long id, String nombre, String descripcion, Long categoriaId, int orden
    ) {}

    public record VarianteCarta(
            Long familiaId, Long platoId, String etiqueta, int orden
    ) {}

    public record FamiliaAdmin(
            Long id, String nombre, String descripcion, Long categoriaId,
            String categoria, boolean activo, int orden
    ) {
        @Override public String toString() {
            return nombre + (activo ? "" : " — INACTIVO");
        }
    }

    public record VarianteAdmin(
            Long familiaId, Long platoId, String etiqueta, String nombrePlato,
            BigDecimal precio, boolean disponible, boolean activa, Integer stockDisponible,
            Integer tiempoPreparacion, int orden
    ) {}

    public List<CategoriaFila> listarCategorias() {
        return jdbc.query("""
                select id, nombre
                from categorias
                where activo
                order by orden, nombre
                """, (rs, n) -> new CategoriaFila(rs.getLong("id"), rs.getString("nombre")));
    }

    public List<FamiliaCarta> familiasCarta() {
        return jdbc.query("""
                select id, nombre, descripcion, categoria_id, orden
                from plato_familias
                where activo
                order by orden, nombre
                """, (rs, n) -> new FamiliaCarta(
                rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"),
                rs.getLong("categoria_id"), rs.getInt("orden")
        ));
    }

    public List<VarianteCarta> variantesCarta() {
        return jdbc.query("""
                select familia_id, plato_id, etiqueta, orden
                from plato_familia_variantes
                where activo
                order by familia_id, orden, etiqueta
                """, (rs, n) -> new VarianteCarta(
                rs.getLong("familia_id"), rs.getLong("plato_id"),
                rs.getString("etiqueta"), rs.getInt("orden")
        ));
    }

    /**
     * Todo plato vinculado a una familia deja de mostrarse como plato individual
     * en la carta del mozo, aunque la familia esté temporalmente inactiva.
     */
    public Set<Long> idsPlatosAgrupados() {
        return jdbc.query("select plato_id from plato_familia_variantes",
                        (rs, n) -> rs.getLong(1))
                .stream().collect(Collectors.toSet());
    }

    public List<FamiliaAdmin> listarFamiliasAdmin() {
        return jdbc.query("""
                select f.id, f.nombre, f.descripcion, f.categoria_id,
                       c.nombre as categoria, f.activo, f.orden
                from plato_familias f
                join categorias c on c.id = f.categoria_id
                order by f.activo desc, f.orden, f.nombre
                """, (rs, n) -> new FamiliaAdmin(
                rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"),
                rs.getLong("categoria_id"), rs.getString("categoria"),
                rs.getBoolean("activo"), rs.getInt("orden")
        ));
    }

    public List<VarianteAdmin> listarVariantes(Long familiaId) {
        if (familiaId == null) return List.of();
        return jdbc.query("""
                select fv.familia_id, fv.plato_id, fv.etiqueta, fv.orden,
                       fv.activo as variante_activa,
                       p.nombre, p.precio, p.disponible, p.tiempo_preparacion,
                       s.stock_disponible
                from plato_familia_variantes fv
                join platos p on p.id = fv.plato_id
                left join vw_platos_stock_hoy s on s.plato_id = p.id
                where fv.familia_id = ?
                order by fv.orden, fv.etiqueta
                """, (rs, n) -> new VarianteAdmin(
                rs.getLong("familia_id"), rs.getLong("plato_id"), rs.getString("etiqueta"),
                rs.getString("nombre"), rs.getBigDecimal("precio"),
                rs.getBoolean("disponible"), rs.getBoolean("variante_activa"),
                (Integer) rs.getObject("stock_disponible"),
                (Integer) rs.getObject("tiempo_preparacion"), rs.getInt("orden")
        ), familiaId);
    }

    public Long crearFamilia(String nombre, String descripcion, Long categoriaId) {
        validarTexto(nombre, "Nombre del plato", 150);
        validarDescripcion(descripcion);
        if (categoriaId == null) throw new IllegalArgumentException("Selecciona una categoría.");
        return jdbc.queryForObject("""
                insert into plato_familias(nombre, descripcion, categoria_id)
                values (?, nullif(btrim(?), ''), ?)
                returning id
                """, Long.class, nombre.trim(), nvl(descripcion), categoriaId);
    }

    public void actualizarFamilia(Long familiaId, String nombre, String descripcion, Long categoriaId) {
        if (familiaId == null) throw new IllegalArgumentException("Selecciona una familia.");
        validarTexto(nombre, "Nombre del plato", 150);
        validarDescripcion(descripcion);
        if (categoriaId == null) throw new IllegalArgumentException("Selecciona una categoría.");
        jdbc.update("""
                update plato_familias
                   set nombre = ?, descripcion = nullif(btrim(?), ''), categoria_id = ?, updated_at = now()
                 where id = ?
                """, nombre.trim(), nvl(descripcion), categoriaId, familiaId);

        // Mantiene nombre/categoría/descripción de las variantes sincronizados
        // con el plato base sin tocar precio, receta ni stock.
        jdbc.update("""
                update platos p
                   set categoria_id = ?,
                       nombre = ? || ' ' || fv.etiqueta,
                       descripcion = nullif(btrim(?), ''),
                       updated_at = LOCALTIMESTAMP
                  from plato_familia_variantes fv
                 where fv.plato_id = p.id and fv.familia_id = ?
                """, categoriaId, nombre.trim(), nvl(descripcion), familiaId);
    }

    public void cambiarFamiliaActiva(Long familiaId, boolean activa) {
        if (familiaId == null) return;
        jdbc.update("update plato_familias set activo = ?, updated_at = now() where id = ?", activa, familiaId);
    }

    @Transactional
    public Long crearVariante(Long familiaId, String etiqueta, BigDecimal precio,
                              Integer tiempoPreparacion, Integer stockInicialHoy) {
        if (familiaId == null) throw new IllegalArgumentException("Selecciona el plato base.");
        validarTexto(etiqueta, "Opción/presentación", 80);
        if (precio == null || precio.signum() <= 0)
            throw new IllegalArgumentException("El precio debe ser mayor que cero.");
        if (tiempoPreparacion != null && tiempoPreparacion < 0)
            throw new IllegalArgumentException("El tiempo de preparación no puede ser negativo.");
        if (stockInicialHoy != null && stockInicialHoy < 0)
            throw new IllegalArgumentException("El stock inicial no puede ser negativo.");

        FamiliaAdmin f = jdbc.query("""
                select f.id, f.nombre, f.descripcion, f.categoria_id, c.nombre categoria, f.activo, f.orden
                from plato_familias f join categorias c on c.id = f.categoria_id
                where f.id = ?
                """, rs -> rs.next() ? new FamiliaAdmin(
                rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"),
                rs.getLong("categoria_id"), rs.getString("categoria"), rs.getBoolean("activo"), rs.getInt("orden")) : null,
                familiaId);
        if (f == null) throw new IllegalArgumentException("El plato base ya no existe.");

        String nombreInterno = f.nombre().trim() + " " + etiqueta.trim();
        Long platoId = jdbc.queryForObject("""
                insert into platos(nombre, descripcion, precio, categoria_id, tiempo_preparacion, disponible)
                values (?, nullif(btrim(?), ''), ?, ?, ?, true)
                returning id
                """, Long.class, nombreInterno, nvl(f.descripcion()), precio,
                f.categoriaId(), tiempoPreparacion);

        jdbc.update("""
                insert into plato_familia_variantes(familia_id, plato_id, etiqueta, orden, activo)
                values (?, ?, ?,
                        coalesce((select max(orden) + 1 from plato_familia_variantes where familia_id = ?), 0),
                        true)
                """, familiaId, platoId, etiqueta.trim(), familiaId);

        return platoId;
    }

    public void actualizarVariante(Long platoId, String etiqueta, BigDecimal precio,
                                   Integer tiempoPreparacion, boolean disponible) {
        if (platoId == null) throw new IllegalArgumentException("Selecciona una variante.");
        validarTexto(etiqueta, "Opción/presentación", 80);
        if (precio == null || precio.signum() <= 0)
            throw new IllegalArgumentException("El precio debe ser mayor que cero.");
        if (tiempoPreparacion != null && tiempoPreparacion < 0)
            throw new IllegalArgumentException("El tiempo de preparación no puede ser negativo.");

        jdbc.update("""
                update plato_familia_variantes
                   set etiqueta = ?
                 where plato_id = ?
                """, etiqueta.trim(), platoId);
        jdbc.update("""
                update platos p
                   set nombre = f.nombre || ' ' || ?,
                       precio = ?, tiempo_preparacion = ?, disponible = ?, updated_at = LOCALTIMESTAMP
                  from plato_familia_variantes fv
                  join plato_familias f on f.id = fv.familia_id
                 where fv.plato_id = p.id and p.id = ?
                """, etiqueta.trim(), precio, tiempoPreparacion, disponible, platoId);
    }

    @Transactional
    public void cambiarVarianteActiva(Long platoId, boolean activa) {
        if (platoId == null) return;
        jdbc.update("update plato_familia_variantes set activo = ? where plato_id = ?", activa, platoId);
        jdbc.update("update platos set disponible = ?, updated_at = LOCALTIMESTAMP where id = ?", activa, platoId);
    }

    public void guardarStockHoy(Long platoId, Integer cantidadAgregar) {
        if (cantidadAgregar == null || cantidadAgregar <= 0) throw new IllegalArgumentException("Ingresa una cantidad mayor que 0.");
        inventarioService.guardarStockDiario(platoId, LocalDate.now(), cantidadAgregar);
    }

    private static void validarTexto(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(campo + " es obligatorio.");
        if (valor.trim().length() > maximo)
            throw new IllegalArgumentException(campo + " admite máximo " + maximo + " caracteres.");
    }

    private static void validarDescripcion(String descripcion) {
        if (descripcion != null && descripcion.trim().length() > 255)
            throw new IllegalArgumentException("La descripción admite máximo 255 caracteres.");
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
