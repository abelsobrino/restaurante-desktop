package pe.edu.utp.restaurante.controller;

import java.math.BigDecimal;
import java.util.List;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.service.CatalogoOpcionesService;
import pe.edu.utp.restaurante.service.CatalogoOpcionesService.CategoriaFila;
import pe.edu.utp.restaurante.service.CatalogoOpcionesService.FamiliaAdmin;
import pe.edu.utp.restaurante.service.CatalogoOpcionesService.VarianteAdmin;

@Component
@Scope("prototype")
public class PlatosOpcionesController {
    private final CatalogoOpcionesService service;

    @FXML private ListView<FamiliaAdmin> lstFamilias;
    @FXML private TableView<VarianteAdmin> tblVariantes;
    @FXML private ComboBox<CategoriaFila> cmbCategoria;
    @FXML private TextField txtNombreFamilia;
    @FXML private TextArea txtDescripcionFamilia;
    @FXML private ComboBox<String> cmbEtiquetaVariante;
    @FXML private TextField txtPrecioVariante;
    @FXML private TextField txtTiempoVariante;
    @FXML private TextField txtStockVariante;
    @FXML private CheckBox chkDisponibleVariante;
    @FXML private Label lblAyuda;

    public PlatosOpcionesController(CatalogoOpcionesService service) {
        this.service = service;
    }

    @FXML
    public void initialize() {
        configurarTabla();
        cmbEtiquetaVariante.setEditable(true);
        cmbEtiquetaVariante.setItems(FXCollections.observableArrayList(
                "x6", "x8", "x10", "x12", "x16", "x24",
                "Personal", "Mediana", "Familiar", "1/4", "1/2", "Entera"
        ));
        chkDisponibleVariante.setSelected(true);
        lstFamilias.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            cargarFamiliaSeleccionada();
            cargarVariantes();
        });
        tblVariantes.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> cargarVarianteSeleccionada());
        actualizar();
    }

    private void configurarTabla() {
        TableColumn<VarianteAdmin, String> opcion = new TableColumn<>("Opción");
        opcion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().etiqueta()));
        opcion.setPrefWidth(125);

        TableColumn<VarianteAdmin, BigDecimal> precio = new TableColumn<>("Precio");
        precio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().precio()));
        precio.setPrefWidth(100);

        TableColumn<VarianteAdmin, Integer> stock = new TableColumn<>("Stock vendible");
        stock.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().stockDisponible()));
        stock.setPrefWidth(120);

        TableColumn<VarianteAdmin, String> estado = new TableColumn<>("Estado");
        estado.setCellValueFactory(c -> new SimpleStringProperty(
                !c.getValue().activa() ? "OCULTA" : c.getValue().disponible() ? "ACTIVA" : "NO DISPONIBLE"
        ));
        estado.setPrefWidth(130);

        TableColumn<VarianteAdmin, Integer> tiempo = new TableColumn<>("Min.");
        tiempo.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().tiempoPreparacion()));
        tiempo.setPrefWidth(70);

        tblVariantes.getColumns().setAll(opcion, precio, stock, estado, tiempo);
        tblVariantes.setRowFactory(t -> new TableRow<>() {
            @Override protected void updateItem(VarianteAdmin v, boolean empty) {
                super.updateItem(v, empty);
                setStyle(""); setOpacity(1);
                if (!empty && v != null && (v.stockDisponible() != null && v.stockDisponible() <= 0)) {
                    setStyle("-fx-background-color:#e5e7eb;-fx-text-fill:#6b7280;");
                    setOpacity(0.62);
                }
            }
        });
    }

    @FXML private void nuevaFamilia() {
        lstFamilias.getSelectionModel().clearSelection();
        txtNombreFamilia.clear();
        txtDescripcionFamilia.clear();
        cmbCategoria.setValue(null);
        tblVariantes.setItems(FXCollections.observableArrayList());
        limpiarVariante();
        lblAyuda.setText("Crea primero el plato base y luego agrega sus opciones.");
    }

    @FXML private void guardarFamilia() {
        try {
            FamiliaAdmin actual = lstFamilias.getSelectionModel().getSelectedItem();
            CategoriaFila categoria = cmbCategoria.getValue();
            if (actual == null) {
                Long id = service.crearFamilia(txtNombreFamilia.getText(), txtDescripcionFamilia.getText(), categoria == null ? null : categoria.id());
                actualizar();
                seleccionarFamilia(id);
                aviso("Plato base creado. Ahora agrega sus opciones.");
            } else {
                service.actualizarFamilia(actual.id(), txtNombreFamilia.getText(), txtDescripcionFamilia.getText(), categoria == null ? null : categoria.id());
                actualizar();
                seleccionarFamilia(actual.id());
                aviso("Plato base actualizado.");
            }
        } catch (Exception e) { error(e); }
    }

    @FXML private void activarDesactivarFamilia() {
        FamiliaAdmin f = lstFamilias.getSelectionModel().getSelectedItem();
        if (f == null) { aviso("Selecciona un plato base."); return; }
        try {
            service.cambiarFamiliaActiva(f.id(), !f.activo());
            actualizar();
            seleccionarFamilia(f.id());
        } catch (Exception e) { error(e); }
    }

    @FXML private void nuevaVariante() {
        tblVariantes.getSelectionModel().clearSelection();
        limpiarVariante();
    }

    @FXML private void guardarVariante() {
        FamiliaAdmin familia = lstFamilias.getSelectionModel().getSelectedItem();
        if (familia == null) { aviso("Selecciona o crea primero el plato base."); return; }
        try {
            String etiqueta = etiquetaActual();
            BigDecimal precio = decimal(txtPrecioVariante.getText());
            Integer tiempo = enteroOpcional(txtTiempoVariante.getText());
            Integer stock = enteroOpcional(txtStockVariante.getText());
            VarianteAdmin actual = tblVariantes.getSelectionModel().getSelectedItem();
            if (actual == null) {
                service.crearVariante(familia.id(), etiqueta, precio, tiempo, null);
                if (stock != null) aviso("Opción creada. Configura primero sus ingredientes en Gestión > Ingredientes y luego agrega el stock indicado.");
                else aviso("Opción creada. Configura sus ingredientes antes de agregar producción.");
            } else {
                service.actualizarVariante(actual.platoId(), etiqueta, precio, tiempo, chkDisponibleVariante.isSelected());
                if (stock != null) service.guardarStockHoy(actual.platoId(), stock);
                aviso("Opción actualizada.");
            }
            cargarVariantes();
            limpiarVariante();
        } catch (Exception e) { error(e); }
    }

    @FXML private void activarDesactivarVariante() {
        VarianteAdmin v = tblVariantes.getSelectionModel().getSelectedItem();
        if (v == null) { aviso("Selecciona una opción."); return; }
        try {
            service.cambiarVarianteActiva(v.platoId(), !v.activa());
            cargarVariantes();
        } catch (Exception e) { error(e); }
    }

    @FXML private void aplicarStockHoy() {
        VarianteAdmin v = tblVariantes.getSelectionModel().getSelectedItem();
        if (v == null) { aviso("Selecciona una opción."); return; }
        try {
            Integer stock = enteroOpcional(txtStockVariante.getText());
            if (stock == null || stock <= 0) throw new IllegalArgumentException("Ingresa una cantidad mayor que 0 para agregar al stock de hoy.");
            service.guardarStockHoy(v.platoId(), stock);
            cargarVariantes();
            aviso("Stock agregado a la producción de hoy.");
        } catch (Exception e) { error(e); }
    }

    @FXML private void actualizar() {
        Long familiaId = familiaSeleccionadaId();
        List<CategoriaFila> categorias = service.listarCategorias();
        cmbCategoria.setItems(FXCollections.observableArrayList(categorias));
        lstFamilias.setItems(FXCollections.observableArrayList(service.listarFamiliasAdmin()));
        if (familiaId != null) seleccionarFamilia(familiaId);
        else if (!lstFamilias.getItems().isEmpty()) lstFamilias.getSelectionModel().selectFirst();
        cargarVariantes();
    }

    private void cargarFamiliaSeleccionada() {
        FamiliaAdmin f = lstFamilias.getSelectionModel().getSelectedItem();
        if (f == null) return;
        txtNombreFamilia.setText(f.nombre());
        txtDescripcionFamilia.setText(f.descripcion() == null ? "" : f.descripcion());
        cmbCategoria.getItems().stream().filter(c -> c.id().equals(f.categoriaId())).findFirst().ifPresent(cmbCategoria::setValue);
        lblAyuda.setText("En carta se verá solo “" + f.nombre() + "”. Al presionarlo, el mozo elegirá una opción.");
    }

    private void cargarVariantes() {
        FamiliaAdmin f = lstFamilias.getSelectionModel().getSelectedItem();
        tblVariantes.setItems(FXCollections.observableArrayList(f == null ? List.of() : service.listarVariantes(f.id())));
    }

    private void cargarVarianteSeleccionada() {
        VarianteAdmin v = tblVariantes.getSelectionModel().getSelectedItem();
        if (v == null) return;
        cmbEtiquetaVariante.getEditor().setText(v.etiqueta());
        cmbEtiquetaVariante.setValue(v.etiqueta());
        txtPrecioVariante.setText(v.precio().toPlainString());
        txtTiempoVariante.setText(v.tiempoPreparacion() == null ? "" : String.valueOf(v.tiempoPreparacion()));
        txtStockVariante.clear();
        chkDisponibleVariante.setSelected(v.disponible());
    }

    private void limpiarVariante() {
        cmbEtiquetaVariante.setValue(null);
        cmbEtiquetaVariante.getEditor().clear();
        txtPrecioVariante.clear();
        txtTiempoVariante.clear();
        txtStockVariante.clear();
        chkDisponibleVariante.setSelected(true);
    }

    private String etiquetaActual() {
        String texto = cmbEtiquetaVariante.getEditor().getText();
        if (texto == null || texto.isBlank()) texto = cmbEtiquetaVariante.getValue();
        return texto;
    }

    private Long familiaSeleccionadaId() {
        FamiliaAdmin f = lstFamilias.getSelectionModel().getSelectedItem();
        return f == null ? null : f.id();
    }

    private void seleccionarFamilia(Long id) {
        if (id == null) return;
        for (int i = 0; i < lstFamilias.getItems().size(); i++) {
            if (id.equals(lstFamilias.getItems().get(i).id())) {
                lstFamilias.getSelectionModel().select(i);
                lstFamilias.scrollTo(i);
                return;
            }
        }
    }

    @FXML private void cerrar() { ((Stage) lstFamilias.getScene().getWindow()).close(); }

    private static BigDecimal decimal(String s) {
        try { return new BigDecimal(s == null ? "" : s.trim().replace(',', '.')); }
        catch (Exception e) { throw new IllegalArgumentException("Ingresa un precio válido."); }
    }

    private static Integer enteroOpcional(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.valueOf(s.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Ingresa un número entero válido."); }
    }

    private void aviso(String texto) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, texto, ButtonType.OK);
        a.setHeaderText(null); a.showAndWait();
    }

    private void error(Exception e) {
        aviso(e instanceof IllegalArgumentException || e instanceof IllegalStateException
                ? e.getMessage() : "No se pudo completar la operación: " + e.getMessage());
    }
}
