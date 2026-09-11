package pe.edu.utp.restaurante.controller;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.config.ApplicationContextProvider;
import pe.edu.utp.restaurante.model.*;
import pe.edu.utp.restaurante.repository.*;
import pe.edu.utp.restaurante.service.PedidoService;

@Component
@Scope("prototype")
public class CocinaController {
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private PedidoDetalleRepository pedidoDetalleRepository;
    @Autowired private PlatoRepository platoRepository;
    @Autowired private PedidoService pedidoService;

    @FXML private ListView<Pedido> lstPedidos;
    @FXML private TableView<PedidoDetalle> tblDetalles;
    @FXML private Label lblCodigo, lblMesa, lblFecha, lblTotal, lblEstado;
    @FXML private ComboBox<String> cmbCambioEstado;
    @FXML private TextArea txtObservacion;
    @FXML private Button btnMarcarListo, btnRegresar;
    private Pedido pedidoSeleccionado;
    private boolean actualizandoVista;
    private final Map<Long, String> nombres = new HashMap<>();

    @FXML public void initialize() {
        lstPedidos.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(Pedido p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : "Mesa " + numeroMesa(p) + " — " + p.getCodigo());
                setStyle(empty ? "" : "-fx-text-fill: #c2410c; -fx-font-weight: bold;");
            }
        });
        lstPedidos.getSelectionModel().selectedItemProperty().addListener((o, antes, p) -> {
            if (!actualizandoVista && p != null) {
                try { mostrarDetallePedido(p); }
                catch (Exception e) { limpiarDetalle(); error(e); }
            }
        });
        cmbCambioEstado.setItems(FXCollections.observableArrayList("EN_PROCESO", "LISTO"));
        TableColumn<PedidoDetalle, String> plato = new TableColumn<>("Producto por preparar");
        plato.setCellValueFactory(c -> new SimpleStringProperty(nombre(c.getValue())));
        plato.setPrefWidth(220);
        TableColumn<PedidoDetalle, Integer> cantidad = new TableColumn<>("Cant.");
        cantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        cantidad.setPrefWidth(60);
        TableColumn<PedidoDetalle, String> observacion = new TableColumn<>("Observación de este envío");
        observacion.setCellValueFactory(new PropertyValueFactory<>("observacionExtra"));
        observacion.setPrefWidth(230);
        TableColumn<PedidoDetalle, String> hora = new TableColumn<>("Enviado");
        hora.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreatedAt() == null ? "-"
                : c.getValue().getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm"))));
        tblDetalles.getColumns().setAll(plato, cantidad, observacion, hora);
        tblDetalles.setPlaceholder(new Label("No hay productos pendientes de preparación."));
        btnMarcarListo.setText("MARCAR LISTO");
        btnMarcarListo.setTooltip(new Tooltip("Marca solo los productos que aparecen en esta tabla."));
        txtObservacion.setEditable(false);
        limpiarDetalle();
        actualizar();
    }

    private String numeroMesa(Pedido p) {
        return p.getMesaNumero() != null ? p.getMesaNumero().toString()
                : p.getMesaId() == null ? "Web" : p.getMesaId().toString();
    }

    private String nombre(PedidoDetalle d) {
        if (d.getPlatoNombre() != null) return d.getPlatoNombre();
        return nombres.getOrDefault(d.getPlatoId(), "Producto " + d.getPlatoId());
    }

    private void cargarPedidos() {
        List<Pedido> pendientes = new ArrayList<>();
        for (String estado : List.of("PENDIENTE", "EN_PROCESO", "TERMINADO")) {
            for (Pedido p : pedidoRepository.findByEstado(estado)) {
                if (pedidoDetalleRepository.findByPedidoId(p.getId()).stream().anyMatch(PedidoService::pendienteCocina))
                    pendientes.add(p);
            }
        }
        pendientes.sort(Comparator.comparing(Pedido::getId));
        actualizandoVista = true;
        try {
            lstPedidos.setItems(FXCollections.observableArrayList(pendientes));
            lstPedidos.getSelectionModel().clearSelection();
            limpiarDetalle();
        } finally { actualizandoVista = false; }
    }

    private void mostrarDetallePedido(Pedido p) {
        List<PedidoDetalle> pendientes = pedidoService.consultarDetalle(p.getId()).stream()
                .filter(PedidoService::pendienteCocina).toList();
        pedidoSeleccionado = p;
        tblDetalles.setItems(FXCollections.observableArrayList(pendientes));
        lblCodigo.setText("Código: " + p.getCodigo());
        lblMesa.setText("Mesa: " + numeroMesa(p));
        lblFecha.setText("Apertura: " + (p.getCreatedAt() == null ? "-"
                : p.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
        BigDecimal total = pendientes.stream().map(PedidoDetalle::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotal.setText("Por preparar: S/ " + total.setScale(2));
        lblEstado.setText(pendientes.isEmpty() ? "Sin productos pendientes" : "Productos pendientes de cocina");
        txtObservacion.setText(String.join("\n", pendientes.stream().map(PedidoDetalle::getObservacionExtra)
                .filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList()));
        actualizandoVista = true;
        try { cmbCambioEstado.setValue(pendientes.isEmpty() ? null : "EN_PROCESO"); }
        finally { actualizandoVista = false; }
        btnMarcarListo.setDisable(pendientes.isEmpty());
        cmbCambioEstado.setDisable(pendientes.isEmpty());
    }

    @FXML private void marcarListo() { aplicarEstado("LISTO"); }

    @FXML private void cambiarEstado() {
        if (!actualizandoVista && cmbCambioEstado.getValue() != null)
            aplicarEstado(cmbCambioEstado.getValue());
    }

    private void aplicarEstado(String estado) {
        if (pedidoSeleccionado == null || tblDetalles.getItems().isEmpty()) return;
        List<Long> ids = tblDetalles.getItems().stream().map(PedidoDetalle::getId).toList();
        btnMarcarListo.setDisable(true);
        cmbCambioEstado.setDisable(true);
        try {
            pedidoService.cambiarEstadoCocina(pedidoSeleccionado.getId(), ids, estado);
            cargarPedidos();
        } catch (Exception e) {
            limpiarDetalle();
            error(e);
        }
    }

    @FXML private void actualizar() {
        try {
            nombres.clear();
            for (Plato p : platoRepository.findAll()) nombres.put(p.getId(), p.getNombre());
            cargarPedidos();
        } catch (Exception e) { limpiarDetalle(); error(e); }
    }

    private void limpiarDetalle() {
        pedidoSeleccionado = null;
        tblDetalles.setItems(FXCollections.observableArrayList());
        lblCodigo.setText("Código: -"); lblMesa.setText("Mesa: -");
        lblFecha.setText("Fecha: -"); lblTotal.setText("Por preparar: S/ 0.00");
        lblEstado.setText("Selecciona una cuenta con productos pendientes");
        txtObservacion.clear();
        boolean anterior = actualizandoVista;
        actualizandoVista = true;
        try { cmbCambioEstado.setValue(null); }
        finally { actualizandoVista = anterior; }
        cmbCambioEstado.setDisable(true); btnMarcarListo.setDisable(true);
    }

    @FXML private void regresar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PanelPrincipalView.fxml"));
            loader.setControllerFactory(ApplicationContextProvider.getApplicationContext()::getBean);
            Stage stage = new Stage();
            stage.setTitle("Restaurante La Fonda");
            stage.setScene(new Scene(loader.load(), 450, 480));
            stage.setMaximized(true); stage.show();
            ((Stage) lstPedidos.getScene().getWindow()).close();
        } catch (Exception e) { error(e); }
    }

    @FXML private void cerrarSesion() { ((Stage) lstPedidos.getScene().getWindow()).close(); }

    private void error(Exception e) {
        String texto = e instanceof IllegalArgumentException || e instanceof IllegalStateException
                ? e.getMessage() : "No se pudo completar la operación. Comprueba la conexión y pulsa Actualizar.";
        Alert a = new Alert(Alert.AlertType.WARNING, texto, ButtonType.OK);
        a.setHeaderText(null); a.showAndWait();
    }
}