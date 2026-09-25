package pe.edu.utp.restaurante.controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.config.ApplicationContextProvider;
import pe.edu.utp.restaurante.model.*;
import pe.edu.utp.restaurante.repository.*;
import pe.edu.utp.restaurante.service.CatalogoOpcionesService;
import pe.edu.utp.restaurante.service.CatalogoOpcionesService.FamiliaCarta;
import pe.edu.utp.restaurante.service.CatalogoOpcionesService.VarianteCarta;
import pe.edu.utp.restaurante.service.PedidoService;
import pe.edu.utp.restaurante.service.StockService;

@Component
@Scope("prototype")
public class MozoController {
    @Autowired private MesaRepository mesaRepository;
    @Autowired private PlatoRepository platoRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private PedidoService pedidoService;
    @Autowired private StockService stockService;
    @Autowired private CatalogoOpcionesService catalogoOpcionesService;

    @FXML private Text txtUsuario, txtMesaSeleccionada;
    @FXML private TilePane panelMesas;
    @FXML private VBox panelCategorias, panelPlatos, panelCarta;
    @FXML private Label lblCategoria, lblSubtotal;
    @FXML private ListView<Mesa> lstMesas;
    @FXML private ListView<ItemCarta> lstPlatos;
    @FXML private TextField txtBuscarPlato;
    @FXML private Spinner<Integer> spnCantidad;
    @FXML private TableView<PedidoDetalle> tblPedido;
    @FXML private TextArea txtObservacion;
    @FXML private Button btnLiberarMesa, btnAgregarPlato, btnEnviarCocina, btnTerminarPedido;

    private final ObservableList<PedidoDetalle> vista = FXCollections.observableArrayList();
    private final List<PedidoDetalle> enviados = new ArrayList<>();
    private final List<PedidoDetalle> nuevos = new ArrayList<>();
    private List<Mesa> mesas = List.of();
    private List<Plato> platos = List.of();
    private List<Categoria> categorias = List.of();
    private Map<Long, Integer> stockPlatos = new HashMap<>();
    private Map<Long, Plato> platosPorId = new HashMap<>();
    private List<FamiliaCarta> familiasCarta = List.of();
    private Map<Long, List<VarianteCarta>> variantesPorFamilia = new HashMap<>();
    private Set<Long> platosAgrupados = new HashSet<>();
    private Usuario usuarioActual;
    private Mesa mesaSeleccionada;
    private Pedido cuenta;
    private Long categoriaId;
    private boolean sinCategoria, ocupado, requiereRevision;

    private record ItemCarta(Long familiaId, Plato plato, String nombre, String descripcion, Long categoriaId) {
        boolean esFamilia() { return familiaId != null; }
    }

    @FXML public void initialize() {
        spnCantidad.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1));
        spnCantidad.setEditable(false);
        txtObservacion.setPromptText("Observación SOLO para el nuevo envío (máximo 255 caracteres)");
        btnEnviarCocina.setText("ENVIAR SOLO NUEVOS");
        btnTerminarPedido.setText("MANDAR CUENTA A CAJA");
        btnLiberarMesa.setText("ANULAR CUENTA / LIBERAR");
        lstPlatos.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(ItemCarta item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(false); setOpacity(1); setStyle("");
                if (empty || item == null) { setText(null); return; }
                boolean agotado = itemAgotado(item);
                if (item.esFamilia()) {
                    String opciones = etiquetasFamilia(item.familiaId());
                    BigDecimal desde = precioMinimoFamilia(item.familiaId());
                    setText(item.nombre()
                            + (desde == null ? "" : " — Desde S/ " + desde)
                            + (item.descripcion() == null || item.descripcion().isBlank() ? "" : "\n" + item.descripcion())
                            + (opciones.isBlank() ? "" : "\nOpciones: " + opciones)
                            + (agotado ? "\nAGOTADO" : ""));
                } else {
                    Plato plato = item.plato();
                    Integer stock = stockPlatos.get(plato.getId());
                    String estadoStock = stock == null ? "" : "\nStock: " + stock + (agotado ? " — AGOTADO" : "");
                    setText(plato.getNombre() + " — S/ " + plato.getPrecio()
                            + (plato.getDescripcion() == null ? "" : "\n" + plato.getDescripcion()) + estadoStock);
                }
                setWrapText(true);
                if (agotado) {
                    setDisable(true); setOpacity(0.48);
                    setStyle("-fx-background-color:#e5e7eb; -fx-text-fill:#6b7280;");
                }
            }
        });
        txtBuscarPlato.textProperty().addListener((o, a, b) -> filtrarPlatos());
        lstPlatos.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> refrescarVista());
        configurarTabla();
        try { cargarCatalogos(); } catch (Exception e) { error(e); }
        volverCategorias();
        refrescarVista();
        txtUsuario.sceneProperty().addListener((o, a, scene) -> {
            if (scene != null) scene.windowProperty().addListener((w, antes, ventana) -> {
                if (ventana != null) ventana.addEventHandler(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST,
                        e -> { if (!confirmarSalida()) e.consume(); });
            });
        });
    }

    public void setUsuario(Usuario usuario) {
        usuarioActual = usuario;
        txtUsuario.setText(usuario.getNombre() + " " + usuario.getApellido());
    }

    private void configurarTabla() {
        TableColumn<PedidoDetalle, String> nombre = new TableColumn<>("Producto");
        nombre.setCellValueFactory(c -> new SimpleStringProperty(nombrePlato(c.getValue())));
        nombre.setPrefWidth(175);
        TableColumn<PedidoDetalle, Integer> cantidad = new TableColumn<>("Cant.");
        cantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        cantidad.setPrefWidth(50);
        TableColumn<PedidoDetalle, BigDecimal> precio = new TableColumn<>("P. unit.");
        precio.setCellValueFactory(new PropertyValueFactory<>("precioUnitario"));
        TableColumn<PedidoDetalle, BigDecimal> importe = new TableColumn<>("Importe");
        importe.setCellValueFactory(new PropertyValueFactory<>("subtotal"));
        TableColumn<PedidoDetalle, String> estado = new TableColumn<>("Estado");
        estado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getId() == null
                ? "NUEVO" : "LISTO".equals(c.getValue().getEstado()) ? "LISTO" : "ENVIADO"));
        estado.setPrefWidth(90);
        TableColumn<PedidoDetalle, Void> quitar = new TableColumn<>("");
        quitar.setPrefWidth(45);
        quitar.setCellFactory(c -> new TableCell<>() {
            private final Button boton = new Button("X");
            {
                boton.setOnAction(e -> {
                    if (getIndex() < 0 || getIndex() >= vista.size()) return;
                    PedidoDetalle d = vista.get(getIndex());
                    if (d.getId() == null && !requiereRevision) { nuevos.remove(d); refrescarVista(); }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                boolean editable = !empty && getIndex() >= 0 && getIndex() < vista.size()
                        && vista.get(getIndex()).getId() == null && !requiereRevision;
                setGraphic(editable ? boton : null);
            }
        });
        tblPedido.getColumns().setAll(nombre, cantidad, precio, importe, estado, quitar);
        tblPedido.setItems(vista);
        tblPedido.setPlaceholder(new Label("Selecciona una mesa y agrega productos."));
        tblPedido.setRowFactory(t -> new TableRow<>() {
            @Override protected void updateItem(PedidoDetalle d, boolean empty) {
                super.updateItem(d, empty);
                setOpacity(1);
                setStyle("");
                if (!empty && d != null) {
                    if (d.getId() != null) {
                        setStyle("-fx-background-color: #e5e7eb;");
                        setOpacity(0.55);
                    } else {
                        setStyle("-fx-background-color: #dcfce7; -fx-font-weight: bold;");
                    }
                }
            }
        });
    }

    private String nombrePlato(PedidoDetalle d) {
        if (d.getPlatoNombre() != null) return d.getPlatoNombre();
        return platosPorId.getOrDefault(d.getPlatoId(), null) != null
                ? platosPorId.get(d.getPlatoId()).getNombre() : "Producto " + d.getPlatoId();
    }

    private void cargarCatalogos() {
        mesas = mesaRepository.findAll().stream().sorted(Comparator.comparing(Mesa::getNumero)).toList();
        platos = platoRepository.findAll();
        platosPorId = platos.stream().collect(java.util.stream.Collectors.toMap(Plato::getId, p -> p, (a,b) -> a));
        stockPlatos = stockService.stockDisponible(platos.stream().map(Plato::getId).toList());
        familiasCarta = catalogoOpcionesService.familiasCarta();
        platosAgrupados = catalogoOpcionesService.idsPlatosAgrupados();
        variantesPorFamilia = catalogoOpcionesService.variantesCarta().stream()
                .collect(java.util.stream.Collectors.groupingBy(VarianteCarta::familiaId));
        categorias = categoriaRepository.findAll().stream().sorted(Comparator.comparing(Categoria::getNombre)).toList();
        dibujarMesas();
        panelCategorias.getChildren().clear();
        String[] colores = {"#d97706", "#2563eb", "#9333ea", "#dc2626", "#16803c", "#475569"};
        int i = 0;
        List<ItemCarta> items = construirItemsCarta();
        for (Categoria c : categorias) {
            long disponibles = items.stream().filter(item -> Objects.equals(item.categoriaId(), c.getId())).count();
            agregarCategoria(c.getId(), c.getNombre() + " (" + disponibles + ")", false, colores[i++ % colores.length]);
        }
        Set<Long> ids = new HashSet<>(categorias.stream().map(Categoria::getId).toList());
        if (items.stream().anyMatch(item -> !ids.contains(item.categoriaId())))
            agregarCategoria(null, "SIN CATEGORÍA", true, "#475569");
        if (panelCategorias.getChildren().isEmpty()) panelCategorias.getChildren().add(new Label("No hay categorías."));
    }

    private void agregarCategoria(Long id, String nombre, boolean otros, String color) {
        Button b = new Button(nombre);
        b.setMaxWidth(Double.MAX_VALUE); b.setMinHeight(56); b.setWrapText(true);
        b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-size: 17; -fx-font-weight: bold; -fx-background-radius: 10;");
        b.setOnAction(e -> {
            categoriaId = id; sinCategoria = otros; lblCategoria.setText(nombre);
            panelCategorias.setVisible(false); panelCategorias.setManaged(false);
            panelPlatos.setVisible(true); panelPlatos.setManaged(true);
            txtBuscarPlato.clear(); filtrarPlatos();
        });
        panelCategorias.getChildren().add(b);
    }

    @FXML private void volverCategorias() {
        panelCategorias.setVisible(true); panelCategorias.setManaged(true);
        panelPlatos.setVisible(false); panelPlatos.setManaged(false);
        txtBuscarPlato.clear(); lstPlatos.getSelectionModel().clearSelection();
    }

    @FXML private void filtrarPlatos() {
        String texto = txtBuscarPlato.getText() == null ? "" : txtBuscarPlato.getText().toLowerCase(Locale.ROOT);
        Set<Long> ids = new HashSet<>(categorias.stream().map(Categoria::getId).toList());
        List<ItemCarta> filtrados = construirItemsCarta().stream()
                .filter(item -> sinCategoria ? !ids.contains(item.categoriaId()) : Objects.equals(categoriaId, item.categoriaId()))
                .filter(item -> coincideBusqueda(item, texto))
                .sorted(Comparator.comparing(ItemCarta::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
        lstPlatos.setItems(FXCollections.observableArrayList(filtrados));
        lstPlatos.setPlaceholder(new Label("No hay platos disponibles."));
    }

    private List<ItemCarta> construirItemsCarta() {
        Map<Long, Plato> porId = platosPorId;
        List<ItemCarta> items = new ArrayList<>();

        for (FamiliaCarta familia : familiasCarta) {
            boolean tieneVariantesVisibles = variantesPorFamilia.getOrDefault(familia.id(), List.of()).stream()
                    .map(VarianteCarta::platoId)
                    .map(porId::get)
                    .anyMatch(p -> p != null && Boolean.TRUE.equals(p.getDisponible()) && stockHabilitado(p));
            if (tieneVariantesVisibles) {
                items.add(new ItemCarta(familia.id(), null, familia.nombre(), familia.descripcion(), familia.categoriaId()));
            }
        }

        for (Plato plato : platos) {
            if (!Boolean.TRUE.equals(plato.getDisponible()) || platosAgrupados.contains(plato.getId()) || !stockHabilitado(plato)) continue;
            items.add(new ItemCarta(null, plato, plato.getNombre(), plato.getDescripcion(), plato.getCategoriaId()));
        }
        return items;
    }

    private boolean coincideBusqueda(ItemCarta item, String texto) {
        if (texto == null || texto.isBlank()) return true;
        if (item.nombre().toLowerCase(Locale.ROOT).contains(texto)) return true;
        if (item.descripcion() != null && item.descripcion().toLowerCase(Locale.ROOT).contains(texto)) return true;
        if (item.esFamilia()) {
            return variantesPorFamilia.getOrDefault(item.familiaId(), List.of()).stream()
                    .anyMatch(v -> v.etiqueta().toLowerCase(Locale.ROOT).contains(texto));
        }
        return false;
    }

    private void dibujarMesas() {
        panelMesas.getChildren().clear();
        for (Mesa m : mesas) {
            Button b = new Button(m.getNumero() + "\n" + m.getEstado());
            b.setPrefSize(96, 86); b.setWrapText(true);
            boolean seleccion = mesaSeleccionada != null && m.getId().equals(mesaSeleccionada.getId());
            String color = "OCUPADA".equals(m.getEstado()) ? "#0284c7" : "RESERVADA".equals(m.getEstado()) ? "#7c3aed" : "#d97706";
            b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-width: 3; -fx-border-color: " + (seleccion ? "#0f172a" : "transparent") + ";");
            b.setOnAction(e -> seleccionarMesa(m));
            panelMesas.getChildren().add(b);
        }
    }

    private void seleccionarMesa(Mesa mesa) {
        if (ocupado) return;
        if (!nuevos.isEmpty() || requiereRevision) {
            aviso("Envía o descarta los nuevos productos antes de cambiar de mesa."); return;
        }
        try {
            if (!pedidoRepository.findByMesaIdAndEstado(mesa.getId(), "TERMINADO").isEmpty()) {
                aviso("Esta mesa ya está en caja. Primero debe cobrarse."); return;
            }
            mesaSeleccionada = mesa;
            cargarCuenta();
            txtObservacion.clear();
            volverCategorias(); dibujarMesas(); refrescarVista();
        } catch (Exception e) { limpiarSeleccion(); error(e); }
    }

    private void cargarCuenta() {
        cuenta = pedidoRepository.findFirstByMesaIdAndEstadoInOrderByCreatedAtDesc(
                mesaSeleccionada.getId(), List.of("PENDIENTE", "EN_PROCESO", "LISTO")).orElse(null);
        List<PedidoDetalle> detalle = cuenta == null ? List.of() : pedidoService.consultarDetalle(cuenta.getId());
        enviados.clear(); enviados.addAll(detalle);
        txtMesaSeleccionada.setText("Mesa: " + mesaSeleccionada.getNumero());
    }

    @FXML private void agregarPlato() {
        if (mesaSeleccionada == null || requiereRevision || ocupado) return;
        ItemCarta item = lstPlatos.getSelectionModel().getSelectedItem();
        if (item == null) { aviso("Selecciona un producto."); return; }
        Plato p = item.esFamilia() ? elegirVariante(item) : item.plato();
        if (p == null) return;
        int cantidad = spnCantidad.getValue();
        int yaEnBorrador = nuevos.stream().filter(x -> x.getPlatoId().equals(p.getId()))
                .mapToInt(x -> x.getCantidad() == null ? 0 : x.getCantidad()).sum();
        try { stockService.validarStock(p.getId(), yaEnBorrador + cantidad, p.getNombre()); }
        catch (Exception e) { error(e); return; }
        // Se agrupa solo en el borrador; nunca se incrementa un renglón ya enviado.
        PedidoDetalle d = nuevos.stream().filter(x -> x.getPlatoId().equals(p.getId())).findFirst().orElse(null);
        if (d == null) {
            d = new PedidoDetalle();
            d.setPlatoId(p.getId()); d.setCantidad(0); d.setPrecioUnitario(p.getPrecio());
            nuevos.add(d);
        }
        d.setCantidad(d.getCantidad() + cantidad);
        d.setSubtotal(d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad())));
        refrescarVista();
    }

    @FXML protected void enviarCocina() {
        if (ocupado || requiereRevision || nuevos.isEmpty() || mesaSeleccionada == null) return;
        if (usuarioActual == null) { aviso("Inicia sesión como mozo."); return; }
        if (txtObservacion.getText().length() > 255) { aviso("La observación admite 255 caracteres."); return; }
        ocupado = true; refrescarVista();
        try {
            Pedido solicitud = new Pedido();
            solicitud.setId(cuenta == null ? null : cuenta.getId());
            solicitud.setMesaId(mesaSeleccionada.getId());
            solicitud.setUsuarioId(usuarioActual.getId());
            solicitud.setObservacionExtra(txtObservacion.getText());
            cuenta = pedidoService.guardarPedidoConDetalles(solicitud, List.copyOf(nuevos));
            // La transacción ya terminó: quitar borrador ANTES de volver a consultar.
            nuevos.clear(); txtObservacion.clear();
            enviados.clear();
            enviados.addAll(pedidoService.consultarDetalle(cuenta.getId()));
            mesas = mesaRepository.findAll().stream().sorted(Comparator.comparing(Mesa::getNumero)).toList();
            stockPlatos = stockService.stockDisponible(platos.stream().map(Plato::getId).toList());
            filtrarPlatos(); dibujarMesas();
            aviso("Solo los productos nuevos fueron enviados. Los anteriores siguen en la cuenta.");
        } catch (DataAccessException e) {
            String mensajeNegocio = mensajeSqlNegocio(e);
            if (mensajeNegocio != null) {
                // Las excepciones P0001 provienen de validaciones controladas de PostgreSQL
                // (por ejemplo stock insuficiente). La transacción se revierte completa.
                requiereRevision = false;
                try {
                    stockPlatos = stockService.stockDisponible(platos.stream().map(Plato::getId).toList());
                    filtrarPlatos();
                } catch (Exception ignorada) {
                    // El mensaje de negocio sigue siendo el dato principal para el usuario.
                }
                aviso(mensajeNegocio);
            } else {
                // Para fallos de red/timeout no asumimos si el servidor alcanzó a confirmar.
                requiereRevision = true;
                aviso("No se pudo confirmar el resultado. Pulsa ACTUALIZAR MESAS Y CARTA para comprobar lo guardado antes de reenviar.");
            }
        } catch (Exception e) { error(e); }
        finally { ocupado = false; refrescarVista(); }
    }

    @FXML protected void terminarPedido() {
        if (!nuevos.isEmpty()) { aviso("Primero envía los nuevos productos a cocina o descártalos."); return; }
        if (requiereRevision || ocupado || cuenta == null) return;
        if (!confirmar("Enviar la cuenta acumulada de la mesa a caja?")) return;
        try {
            pedidoService.terminarCuenta(cuenta.getId());
            limpiarSeleccion(); cargarCatalogos();
            aviso("Caja recibirá todo el consumo acumulado de la mesa.");
        } catch (Exception e) { error(e); }
    }

    // CANCELAR elimina solo el borrador, nunca los consumos guardados.
    @FXML private void cancelarPedido() {
        if (requiereRevision) { aviso("Primero pulsa ACTUALIZAR para verificar el envío."); return; }
        if (nuevos.isEmpty()) { aviso("No hay productos nuevos que descartar. Los enviados se conservan."); return; }
        if (confirmar("Descartar únicamente los productos nuevos sin enviar?")) {
            nuevos.clear(); txtObservacion.clear(); refrescarVista();
        }
    }

    @FXML private void liberarMesa() {
        if (mesaSeleccionada == null || requiereRevision || ocupado) return;
        if (cuenta == null) { cancelarPedido(); return; }
        if (!confirmar("ANULAR TODA la cuenta de esta mesa, incluidos sus consumos enviados? No uses esta opción para quitar un adicional.")) return;
        try {
            pedidoService.cancelarPedido(cuenta.getId());
            limpiarSeleccion(); cargarCatalogos();
        } catch (Exception e) { error(e); }
    }

    @FXML private void actualizarCarta() {
        if (ocupado) return;
        if (!nuevos.isEmpty() && !requiereRevision) { aviso("Envía o descarta los nuevos antes de actualizar."); return; }
        try {
            if (requiereRevision) {
                if (!confirmar("Consultar la cuenta guardada y descartar el borrador local para evitar duplicados?")) return;
                nuevos.clear();
            }
            cargarCatalogos();
            if (mesaSeleccionada != null) {
                if (!pedidoRepository.findByMesaIdAndEstado(mesaSeleccionada.getId(), "TERMINADO").isEmpty()) limpiarSeleccion();
                else cargarCuenta();
            }
            requiereRevision = false;
            volverCategorias(); refrescarVista();
        } catch (Exception e) { requiereRevision = true; refrescarVista(); error(e); }
    }

    private void limpiarSeleccion() {
        cuenta = null; mesaSeleccionada = null;
        enviados.clear(); nuevos.clear(); txtObservacion.clear();
        txtMesaSeleccionada.setText("Mesa: Ninguna");
        volverCategorias(); refrescarVista(); dibujarMesas();
    }

    private void refrescarVista() {
        vista.setAll(enviados); vista.addAll(nuevos); tblPedido.refresh();
        BigDecimal total = vista.stream().map(PedidoDetalle::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        lblSubtotal.setText("S/ " + total.setScale(2));
        boolean bloqueado = ocupado || requiereRevision || mesaSeleccionada == null;
        panelCarta.setDisable(bloqueado);
        txtObservacion.setDisable(bloqueado);
        ItemCarta seleccionado = lstPlatos.getSelectionModel().getSelectedItem();
        btnAgregarPlato.setDisable(bloqueado || (seleccionado != null && itemAgotado(seleccionado)));
        btnEnviarCocina.setDisable(bloqueado || nuevos.isEmpty());
        btnTerminarPedido.setDisable(bloqueado || cuenta == null || !nuevos.isEmpty());
        btnLiberarMesa.setDisable(bloqueado || cuenta == null);
    }

    private boolean itemAgotado(ItemCarta item) {
        if (item == null) return false;
        if (!item.esFamilia()) return !stockHabilitado(item.plato());
        Map<Long, Plato> porId = platosPorId;
        return variantesPorFamilia.getOrDefault(item.familiaId(), List.of()).stream()
                .map(VarianteCarta::platoId)
                .map(porId::get)
                .filter(Objects::nonNull)
                .filter(p -> Boolean.TRUE.equals(p.getDisponible()))
                .noneMatch(this::stockHabilitado);
    }

    private BigDecimal precioMinimoFamilia(Long familiaId) {
        Map<Long, Plato> porId = platosPorId;
        return variantesPorFamilia.getOrDefault(familiaId, List.of()).stream()
                .map(VarianteCarta::platoId)
                .map(porId::get)
                .filter(Objects::nonNull)
                .filter(p -> Boolean.TRUE.equals(p.getDisponible()))
                .map(Plato::getPrecio)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo).orElse(null);
    }

    private String etiquetasFamilia(Long familiaId) {
        Map<Long, Plato> porId = platosPorId;
        return variantesPorFamilia.getOrDefault(familiaId, List.of()).stream()
                .filter(v -> {
                    Plato p = porId.get(v.platoId());
                    return p != null && Boolean.TRUE.equals(p.getDisponible());
                })
                .map(VarianteCarta::etiqueta)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" · "));
    }

    private Plato elegirVariante(ItemCarta item) {
        Map<Long, Plato> porId = platosPorId;
        List<VarianteCarta> opciones = variantesPorFamilia.getOrDefault(item.familiaId(), List.of()).stream()
                .filter(v -> {
                    Plato p = porId.get(v.platoId());
                    return p != null && Boolean.TRUE.equals(p.getDisponible()) && stockHabilitado(p);
                }).toList();
        if (opciones.isEmpty()) {
            aviso("Este plato no tiene opciones disponibles.");
            return null;
        }

        Dialog<Plato> dialogo = new Dialog<>();
        dialogo.setTitle(item.nombre());
        dialogo.setHeaderText("Elige una presentación");
        ButtonType aceptar = new ButtonType("Seleccionar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(aceptar, ButtonType.CANCEL);

        ListView<VarianteCarta> lista = new ListView<>(FXCollections.observableArrayList(opciones));
        lista.setPrefSize(430, Math.min(330, 64.0 * opciones.size() + 20));
        lista.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(VarianteCarta v, boolean empty) {
                super.updateItem(v, empty);
                setDisable(false); setOpacity(1); setStyle("");
                if (empty || v == null) { setText(null); return; }
                Plato p = porId.get(v.platoId());
                Integer stock = p == null ? null : stockPlatos.get(p.getId());
                boolean agotado = p == null || !stockHabilitado(p);
                setText(v.etiqueta() + (p == null ? "" : " — S/ " + p.getPrecio())
                        + (stock == null ? "" : " — Stock: " + stock)
                        + (agotado ? " — AGOTADO" : ""));
                if (agotado) {
                    setDisable(true); setOpacity(0.48);
                    setStyle("-fx-background-color:#e5e7eb;-fx-text-fill:#6b7280;");
                }
            }
        });
        dialogo.getDialogPane().setContent(lista);
        Button botonAceptar = (Button) dialogo.getDialogPane().lookupButton(aceptar);
        botonAceptar.setDisable(true);
        lista.getSelectionModel().selectedItemProperty().addListener((o,a,v) -> {
            Plato p = v == null ? null : porId.get(v.platoId());
            botonAceptar.setDisable(p == null || !stockHabilitado(p));
        });
        lista.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !botonAceptar.isDisable()) {
                botonAceptar.fire();
            }
        });
        dialogo.setResultConverter(b -> {
            if (b != aceptar) return null;
            VarianteCarta v = lista.getSelectionModel().getSelectedItem();
            return v == null ? null : porId.get(v.platoId());
        });
        return dialogo.showAndWait().orElse(null);
    }

    private boolean stockHabilitado(Plato p) {
        if (p == null) return false;
        Integer stock = stockPlatos.get(p.getId());
        return stock != null && stock > 0;
    }


    private String mensajeSqlNegocio(Throwable error) {
        Throwable actual = error;
        while (actual != null) {
            if (actual instanceof SQLException sql && "P0001".equals(sql.getSQLState())) {
                String mensaje = sql.getMessage();
                if (mensaje == null || mensaje.isBlank()) return "La operación fue rechazada por una validación de la base de datos.";
                String primera = mensaje.lines().findFirst().orElse(mensaje).trim();
                return primera.startsWith("ERROR:") ? primera.substring(6).trim() : primera;
            }
            actual = actual.getCause();
        }
        return null;
    }

    private boolean confirmarSalida() {
        return nuevos.isEmpty() && !requiereRevision || confirmar("Salir? Los productos enviados seguirán guardados; el borrador local se perderá.");
    }
    @FXML private void regresar() {
        if (!confirmarSalida()) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PanelPrincipalView.fxml"));
            loader.setControllerFactory(ApplicationContextProvider.getApplicationContext()::getBean);
            Stage stage = new Stage(); stage.setScene(new Scene(loader.load(), 450, 480));
            stage.setTitle("Restaurante La Fonda"); stage.show();
            ((Stage) txtUsuario.getScene().getWindow()).close();
        } catch (Exception e) { error(e); }
    }
    @FXML private void cerrarSesion() {
        if (confirmarSalida()) ((Stage) txtUsuario.getScene().getWindow()).close();
    }
    private boolean confirmar(String texto) {
        return new Alert(Alert.AlertType.CONFIRMATION, texto, ButtonType.OK, ButtonType.CANCEL)
                .showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private void aviso(String texto) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, texto, ButtonType.OK);
        a.setHeaderText(null); a.showAndWait();
    }
    private void error(Exception e) {
        aviso(e instanceof IllegalArgumentException || e instanceof IllegalStateException
                ? e.getMessage() : "No se pudo completar la operación. Comprueba la conexión y actualiza.");
    }
}
