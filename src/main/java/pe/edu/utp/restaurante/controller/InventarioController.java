package pe.edu.utp.restaurante.controller;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.model.Plato;
import pe.edu.utp.restaurante.repository.PlatoRepository;
import pe.edu.utp.restaurante.service.ExportadorTablasService;
import pe.edu.utp.restaurante.service.InventarioService;
import pe.edu.utp.restaurante.service.InventarioService.IngredienteFila;
import pe.edu.utp.restaurante.service.InventarioService.MovimientoFila;
import pe.edu.utp.restaurante.service.InventarioService.ProduccionFila;
import pe.edu.utp.restaurante.service.InventarioService.RecetaFila;
import pe.edu.utp.restaurante.service.StockService;

@Component
@Scope("prototype")
public class InventarioController {
    @Autowired private InventarioService inventarioService;
    @Autowired private StockService stockService;
    @Autowired private PlatoRepository platoRepository;
    @Autowired private ExportadorTablasService exportador;

    @FXML private TabPane tabPaneGestion;
    @FXML private TableView<IngredienteFila> tblIngredientes, tblInventario;
    @FXML private TableView<RecetaFila> tblReceta;
    @FXML private TableView<ProduccionFila> tblProduccion;
    @FXML private TableView<MovimientoFila> tblMovimientos;
    @FXML private ComboBox<String> cmbUnidad;
    @FXML private ComboBox<Plato> cmbPlatoStock, cmbPlatoReceta;
    @FXML private ComboBox<IngredienteFila> cmbIngredienteReceta;
    @FXML private TextField txtNombreIngrediente, txtCategoriaIngrediente, txtStockMinimo;
    @FXML private TextField txtAjuste, txtMotivoAjuste, txtStockDiario, txtCantidadReceta;
    @FXML private CheckBox chkAutomatico;
    @FXML private Label lblStockPlato, lblStockBajo, lblModoIngrediente, lblIngredienteAjuste;
    @FXML private Button btnGuardarIngrediente, btnActivarIngrediente;

    private IngredienteFila ingredienteEditando;

    @FXML public void initialize() {
        cmbUnidad.setItems(FXCollections.observableArrayList("UNIDAD", "GRAMO", "KILOGRAMO", "MILILITRO", "LITRO"));
        cmbUnidad.setValue("UNIDAD");
        chkAutomatico.setSelected(true);
        configurarTablaIngredientes(tblIngredientes, true);
        configurarTablaIngredientes(tblInventario, false);
        configurarTablaReceta();
        configurarTablaProduccion();
        configurarTablaMovimientos();
        configurarComboPlato(cmbPlatoStock);
        configurarComboPlato(cmbPlatoReceta);
        cmbPlatoStock.valueProperty().addListener((o,a,b) -> mostrarStockPlato());
        cmbPlatoReceta.valueProperty().addListener((o,a,b) -> cargarReceta());
        tblReceta.getSelectionModel().selectedItemProperty().addListener((o,a,r) -> seleccionarReceta(r));
        tblInventario.getSelectionModel().selectedItemProperty().addListener((o,a,i) -> seleccionarAjuste(i));
        actualizar();
    }

    public void abrirSeccion(String seccion) {
        if (tabPaneGestion == null || seccion == null) return;
        int indice = switch (seccion.toUpperCase()) {
            case "INVENTARIO" -> 1;
            case "STOCK" -> 2;
            default -> 0;
        };
        tabPaneGestion.getSelectionModel().select(indice);
    }

    private void configurarTablaIngredientes(TableView<IngredienteFila> tabla, boolean editable) {
        TableColumn<IngredienteFila,String> nombre = colTexto("Ingrediente", 190, IngredienteFila::nombre);
        TableColumn<IngredienteFila,String> categoria = colTexto("Categoría", 120, x -> nvl(x.categoria()));
        TableColumn<IngredienteFila,String> stock = colTexto("Cantidad", 100, x -> x.stockActual().stripTrailingZeros().toPlainString());
        TableColumn<IngredienteFila,String> unidad = colTexto("Unidad", 80, x -> InventarioService.abreviar(x.unidadBase()));
        TableColumn<IngredienteFila,String> minimo = colTexto("Stock mínimo", 105, x -> x.stockMinimo().stripTrailingZeros().toPlainString());
        TableColumn<IngredienteFila,String> modo = colTexto("Consumo", 95, x -> x.descuentoAutomatico() ? "AUTO" : "MANUAL");
        TableColumn<IngredienteFila,String> estado = colTexto("Estado", 80, x -> x.activo() ? "ACTIVO" : "INACTIVO");
        tabla.getColumns().setAll(nombre, categoria, stock, unidad, minimo, modo, estado);
        tabla.setRowFactory(t -> new TableRow<>() {
            @Override protected void updateItem(IngredienteFila i, boolean empty) {
                super.updateItem(i, empty);
                if (empty || i == null) setStyle("");
                else if (!i.activo()) setStyle("-fx-opacity:.55;");
                else if (i.stockActual().compareTo(i.stockMinimo()) <= 0) setStyle("-fx-background-color:#fee2e2;-fx-font-weight:bold;");
                else setStyle("");
            }
        });
        if (editable) tabla.getSelectionModel().selectedItemProperty().addListener((o,a,i) -> cargarIngrediente(i));
    }

    private void configurarTablaReceta() {
        tblReceta.getColumns().setAll(
                colTexto("Ingrediente", 230, RecetaFila::ingrediente),
                colTexto("Cantidad por plato", 170, r -> InventarioService.formatearCantidad(r.cantidadBase(), r.unidadBase())),
                colTexto("Descuento", 110, r -> r.descuentoAutomatico() ? "Automático" : "Manual")
        );
    }

    private void configurarTablaProduccion() {
        TableColumn<ProduccionFila,Number> inicial = new TableColumn<>("Preparado");
        inicial.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().stockInicial()));
        TableColumn<ProduccionFila,Number> actual = new TableColumn<>("Disponible");
        actual.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().stockActual()));
        TableColumn<ProduccionFila,Number> vendido = new TableColumn<>("Vendido");
        vendido.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().vendidos()));
        tblProduccion.getColumns().setAll(
                colTexto("Plato", 230, ProduccionFila::plato), inicial, actual, vendido,
                colTexto("Insumos preparados / en cocina", 470, ProduccionFila::insumosEnCocina)
        );
    }

    private void configurarTablaMovimientos() {
        tblMovimientos.getColumns().setAll(
                colTexto("Fecha", 135, m -> m.fecha() == null ? "-" : m.fecha().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))),
                colTexto("Ingrediente", 170, MovimientoFila::ingrediente),
                colTexto("Tipo", 150, MovimientoFila::tipo),
                colTexto("Movimiento", 110, m -> InventarioService.formatearCantidad(m.cantidad(), m.unidadBase())),
                colTexto("Stock final", 110, m -> InventarioService.formatearCantidad(m.stockNuevo(), m.unidadBase())),
                colTexto("Motivo", 300, m -> nvl(m.motivo()))
        );
    }

    private static <T> TableColumn<T,String> colTexto(String titulo, double ancho, java.util.function.Function<T,String> f) {
        TableColumn<T,String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(v -> new SimpleStringProperty(f.apply(v.getValue())));
        c.setPrefWidth(ancho);
        return c;
    }

    private void configurarComboPlato(ComboBox<Plato> combo) {
        combo.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(Plato p, boolean empty) { super.updateItem(p, empty); setText(empty || p == null ? null : p.getNombre()); }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Plato p, boolean empty) { super.updateItem(p, empty); setText(empty || p == null ? null : p.getNombre()); }
        });
    }

    @FXML private void guardarIngrediente() {
        try {
            if (ingredienteEditando == null) {
                inventarioService.crearIngrediente(txtNombreIngrediente.getText(), txtCategoriaIngrediente.getText(), cmbUnidad.getValue(), decimal(txtStockMinimo.getText(), true), chkAutomatico.isSelected());
                aviso("Ingrediente creado.");
            } else {
                inventarioService.actualizarIngrediente(ingredienteEditando.id(), txtNombreIngrediente.getText(), txtCategoriaIngrediente.getText(), cmbUnidad.getValue(), decimal(txtStockMinimo.getText(), true), chkAutomatico.isSelected());
                aviso("Ingrediente actualizado.");
            }
            limpiarIngrediente(); actualizar();
        } catch (Exception e) { error(e); }
    }

    @FXML private void nuevoIngrediente() { limpiarIngrediente(); }

    @FXML private void cambiarActivoIngrediente() {
        if (ingredienteEditando == null) { aviso("Selecciona un ingrediente."); return; }
        try {
            inventarioService.cambiarActivo(ingredienteEditando.id(), !ingredienteEditando.activo());
            limpiarIngrediente(); actualizar();
        } catch (Exception e) { error(e); }
    }

    private void cargarIngrediente(IngredienteFila i) {
        ingredienteEditando = i;
        if (i == null) { limpiarIngrediente(); return; }
        lblModoIngrediente.setText("Editando: " + i.nombre());
        txtNombreIngrediente.setText(i.nombre());
        txtCategoriaIngrediente.setText(nvl(i.categoria()));
        cmbUnidad.setValue(i.unidadBase());
        txtStockMinimo.setText(i.stockMinimo().stripTrailingZeros().toPlainString());
        chkAutomatico.setSelected(i.descuentoAutomatico());
        btnGuardarIngrediente.setText("Guardar cambios");
        btnActivarIngrediente.setText(i.activo() ? "Desactivar" : "Reactivar");
    }

    private void limpiarIngrediente() {
        ingredienteEditando = null;
        if (tblIngredientes != null) tblIngredientes.getSelectionModel().clearSelection();
        txtNombreIngrediente.clear(); txtCategoriaIngrediente.clear(); txtStockMinimo.clear();
        cmbUnidad.setValue("UNIDAD"); chkAutomatico.setSelected(true);
        lblModoIngrediente.setText("Nuevo ingrediente");
        btnGuardarIngrediente.setText("Crear ingrediente"); btnActivarIngrediente.setText("Desactivar / Reactivar");
    }

    @FXML private void ajustarStock() {
        IngredienteFila i = tblInventario.getSelectionModel().getSelectedItem();
        if (i == null) { aviso("Selecciona un ingrediente del inventario."); return; }
        try {
            BigDecimal nuevo = inventarioService.ajustarStock(i.id(), decimal(txtAjuste.getText(), false), txtMotivoAjuste.getText());
            txtAjuste.clear(); txtMotivoAjuste.clear(); actualizar();
            aviso("Nuevo stock: " + InventarioService.formatearCantidad(nuevo, i.unidadBase()));
        } catch (Exception e) { error(e); }
    }

    private void seleccionarAjuste(IngredienteFila i) {
        lblIngredienteAjuste.setText(i == null ? "Selecciona un ingrediente" : i.nombre() + " — " + InventarioService.formatearCantidad(i.stockActual(), i.unidadBase()));
    }

    @FXML private void guardarStockDiario() {
        Plato p = cmbPlatoStock.getValue();
        try {
            int cantidad = enteroPositivo(txtStockDiario.getText());
            inventarioService.guardarStockDiario(p == null ? null : p.getId(), LocalDate.now(), cantidad);
            txtStockDiario.clear(); actualizar();
            aviso("Se agregaron " + cantidad + " porciones a la producción de hoy. Preparado y disponible fueron incrementados.");
        } catch (Exception e) { error(e); }
    }

    @FXML private void guardarIngredienteReceta() {
        Plato p = cmbPlatoReceta.getValue();
        IngredienteFila i = cmbIngredienteReceta.getValue();
        try {
            inventarioService.guardarIngredienteReceta(p == null ? null : p.getId(), i == null ? null : i.id(), decimal(txtCantidadReceta.getText(), false));
            txtCantidadReceta.clear(); tblReceta.getSelectionModel().clearSelection(); cargarReceta();
        } catch (Exception e) { error(e); }
    }

    @FXML private void eliminarIngredienteReceta() {
        Plato p = cmbPlatoReceta.getValue();
        RecetaFila r = tblReceta.getSelectionModel().getSelectedItem();
        if (p == null || r == null) { aviso("Selecciona un ingrediente de la receta."); return; }
        try { inventarioService.eliminarIngredienteReceta(p.getId(), r.ingredienteId()); txtCantidadReceta.clear(); cargarReceta(); }
        catch (Exception e) { error(e); }
    }

    private void seleccionarReceta(RecetaFila r) {
        if (r == null) return;
        cmbIngredienteReceta.getItems().stream().filter(i -> i.id().equals(r.ingredienteId())).findFirst().ifPresent(cmbIngredienteReceta::setValue);
        txtCantidadReceta.setText(r.cantidadBase().stripTrailingZeros().toPlainString());
    }

    @FXML private void actualizar() {
        try {
            List<IngredienteFila> ingredientes = inventarioService.listarIngredientes();
            tblIngredientes.setItems(FXCollections.observableArrayList(ingredientes));
            tblInventario.setItems(FXCollections.observableArrayList(ingredientes.stream().filter(IngredienteFila::activo).toList()));
            cmbIngredienteReceta.setItems(FXCollections.observableArrayList(ingredientes.stream().filter(i -> i.activo() && i.descuentoAutomatico()).toList()));
            List<Plato> platos = platoRepository.findAll().stream().filter(p -> Boolean.TRUE.equals(p.getDisponible())).toList();
            Plato ps = cmbPlatoStock.getValue(), pr = cmbPlatoReceta.getValue();
            cmbPlatoStock.setItems(FXCollections.observableArrayList(platos));
            cmbPlatoReceta.setItems(FXCollections.observableArrayList(platos));
            restaurar(cmbPlatoStock, ps); restaurar(cmbPlatoReceta, pr);
            tblProduccion.setItems(FXCollections.observableArrayList(inventarioService.produccionHoy()));
            tblMovimientos.setItems(FXCollections.observableArrayList(inventarioService.movimientosRecientes(250)));
            lblStockBajo.setText("Alertas de stock mínimo: " + inventarioService.stockBajo().size());
            cargarReceta(); mostrarStockPlato();
        } catch (Exception e) { error(e); }
    }

    private void cargarReceta() {
        Plato p = cmbPlatoReceta.getValue();
        try { tblReceta.setItems(FXCollections.observableArrayList(p == null ? List.of() : inventarioService.receta(p.getId()))); }
        catch (Exception e) { tblReceta.setItems(FXCollections.observableArrayList()); error(e); }
    }

    private void mostrarStockPlato() {
        Plato p = cmbPlatoStock.getValue();
        if (p == null) { lblStockPlato.setText("Disponible hoy: -"); return; }
        try {
            int stock = stockService.stockDisponible(p.getId());
            lblStockPlato.setText("Disponible hoy: " + stock + (stock == 0 ? " — no aparecerá para el mozo" : " — habilitado para venta"));
        } catch (Exception e) { error(e); }
    }

    @FXML private void exportarRecetaPdf() { exportarReceta(true); }
    @FXML private void exportarRecetaExcel() { exportarReceta(false); }
    private void exportarReceta(boolean pdf) {
        Plato p = cmbPlatoReceta.getValue();
        if (p == null) { aviso("Selecciona un plato."); return; }
        List<List<String>> filas = tblReceta.getItems().stream().map(r -> List.of(r.ingrediente(), InventarioService.formatearCantidad(r.cantidadBase(), r.unidadBase()), r.descuentoAutomatico()?"Automático":"Manual")).toList();
        exportar("Receta_" + p.getNombre().replaceAll("[^A-Za-z0-9_-]","_"), List.of("Ingrediente","Cantidad por plato","Consumo"), filas, pdf);
    }

    @FXML private void exportarMovimientosPdf() { exportarMovimientos(true); }
    @FXML private void exportarMovimientosExcel() { exportarMovimientos(false); }
    private void exportarMovimientos(boolean pdf) {
        List<List<String>> filas = tblMovimientos.getItems().stream().map(m -> List.of(m.fecha()==null?"-":m.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), m.ingrediente(), m.tipo(), InventarioService.formatearCantidad(m.cantidad(),m.unidadBase()), InventarioService.formatearCantidad(m.stockNuevo(),m.unidadBase()), nvl(m.motivo()))).toList();
        exportar("Movimientos_Inventario", List.of("Fecha","Ingrediente","Tipo","Movimiento","Stock final","Motivo"), filas, pdf);
    }

    @FXML private void exportarInventarioPdf() { exportarInventario(true); }
    @FXML private void exportarInventarioExcel() { exportarInventario(false); }
    private void exportarInventario(boolean pdf) {
        List<List<String>> filas = tblInventario.getItems().stream().map(i -> List.of(i.nombre(), nvl(i.categoria()), InventarioService.formatearCantidad(i.stockActual(), i.unidadBase()), InventarioService.formatearCantidad(i.stockMinimo(), i.unidadBase()), i.descuentoAutomatico()?"Automático":"Manual")).toList();
        exportar("Inventario_LaFonda", List.of("Ingrediente","Categoría","Stock","Mínimo","Consumo"), filas, pdf);
    }

    @FXML private void exportarStockPdf() { exportarStock(true); }
    @FXML private void exportarStockExcel() { exportarStock(false); }
    private void exportarStock(boolean pdf) {
        List<List<String>> filas = tblProduccion.getItems().stream().map(p -> List.of(p.plato(), String.valueOf(p.stockInicial()), String.valueOf(p.stockActual()), String.valueOf(p.vendidos()), p.insumosEnCocina())).toList();
        exportar("Produccion_Stock_Platos_" + LocalDate.now(), List.of("Plato","Preparado","Disponible","Vendido","Insumos en cocina"), filas, pdf);
    }

    private void exportar(String nombre, List<String> columnas, List<List<String>> filas, boolean pdf) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(pdf ? "PDF" : "Excel", pdf ? "*.pdf" : "*.xlsx"));
        fc.setInitialFileName(nombre + (pdf ? ".pdf" : ".xlsx"));
        File f = fc.showSaveDialog(tabPaneGestion.getScene().getWindow());
        if (f == null) return;
        try {
            if (pdf) exportador.exportarPdf(nombre.replace('_',' '), columnas, filas, f.toPath());
            else exportador.exportarXlsx(nombre.replace('_',' '), columnas, filas, f.toPath());
            abrir(f); aviso("Reporte guardado en:\n" + f.getAbsolutePath());
        } catch (Exception e) { error(e); }
    }

    private static void abrir(File f) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(f);
                return;
            }
        } catch (Exception ignored) { }
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win"))
                new ProcessBuilder("cmd", "/c", "start", "", f.getAbsolutePath()).start();
        } catch (Exception ignored) { }
    }
    @FXML private void cerrar() { ((Stage) tabPaneGestion.getScene().getWindow()).close(); }

    private static void restaurar(ComboBox<Plato> combo, Plato previo) { if (previo != null) combo.getItems().stream().filter(p -> p.getId().equals(previo.getId())).findFirst().ifPresent(combo::setValue); }
    private static BigDecimal decimal(String s, boolean vacioCero) { if (s == null || s.isBlank()) { if (vacioCero) return BigDecimal.ZERO; throw new IllegalArgumentException("Completa la cantidad."); } try { return new BigDecimal(s.trim().replace(',','.')); } catch (NumberFormatException e) { throw new IllegalArgumentException("Número inválido: " + s); } }
    private static int enteroPositivo(String s) { try { int v=Integer.parseInt(s == null ? "" : s.trim()); if(v<=0) throw new NumberFormatException(); return v; } catch(Exception e){ throw new IllegalArgumentException("La cantidad a agregar debe ser un número entero mayor que 0."); } }
    private static String nvl(String s) { return s == null ? "" : s; }
    private void aviso(String s) { Alert a=new Alert(Alert.AlertType.INFORMATION,s,ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
    private void error(Exception e) { aviso(e instanceof IllegalArgumentException || e instanceof IllegalStateException ? e.getMessage() : "No se pudo completar la operación: " + e.getMessage()); }
}
