package pe.edu.utp.restaurante.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.config.ApplicationContextProvider;
import pe.edu.utp.restaurante.model.Pedido;
import pe.edu.utp.restaurante.model.PedidoDetalle;
import pe.edu.utp.restaurante.model.Plato;
import pe.edu.utp.restaurante.repository.PedidoDetalleRepository;
import pe.edu.utp.restaurante.repository.PedidoRepository;
import pe.edu.utp.restaurante.repository.PlatoRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class CocinaController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

    @Autowired
    private PlatoRepository platoRepository;

    private ObservableList<Pedido> pedidosEnCocina = FXCollections.observableArrayList();

    @FXML
    private ListView<Pedido> lstPedidos;

    @FXML
    private TableView<PedidoDetalle> tblDetalles;

    @FXML
    private Label lblCodigo;

    @FXML
    private Label lblMesa;

    @FXML
    private Label lblFecha;

    @FXML
    private Label lblTotal;

    @FXML
    private Label lblEstado;

    @FXML
    private ComboBox<String> cmbCambioEstado;

    @FXML
    private TextArea txtObservacion;

    @FXML
    private Button btnMarcarListo;

    @FXML
    private Button btnRegresar;

    private Pedido pedidoSeleccionado;

    @FXML
    public void initialize() {
        // Configurar ListView de pedidos
        lstPedidos.setCellFactory(lv -> new ListCell<Pedido>() {
            @Override
            protected void updateItem(Pedido pedido, boolean empty) {
                super.updateItem(pedido, empty);
                if (empty || pedido == null) {
                    setText(null);
                } else {
                    String estado = pedido.getEstado() != null ? pedido.getEstado() : "PENDIENTE";
                    String texto = "Mesa " + pedido.getMesaId() + " - " + pedido.getCodigo() + " [" + estado + "]";
                    if ("EN_PROCESO".equals(estado)) {
                        setStyle("-fx-text-fill: #e65100; -fx-font-weight: bold;");
                    } else if ("LISTO".equals(estado)) {
                        setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #1a237e;");
                    }
                    setText(texto);
                }
            }
        });

        // Selección de pedido
        lstPedidos.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                pedidoSeleccionado = newVal;
                mostrarDetallePedido(newVal);
                cmbCambioEstado.setValue(newVal.getEstado());
            }
        });

        // Configurar ComboBox de estados (solo 2 estados)
        cmbCambioEstado.setItems(FXCollections.observableArrayList("EN_PROCESO", "LISTO"));

        // Configurar tabla de detalles
        TableColumn<PedidoDetalle, String> colPlato = new TableColumn<>("Plato");
        colPlato.setCellValueFactory(cellData -> {
            String nombre = platoRepository.findById(cellData.getValue().getPlatoId())
                    .map(Plato::getNombre)
                    .orElse("Desconocido");
            return javafx.beans.binding.Bindings.createStringBinding(() -> nombre);
        });
        colPlato.setPrefWidth(200);

        TableColumn<PedidoDetalle, Integer> colCant = new TableColumn<>("Cant.");
        colCant.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colCant.setPrefWidth(60);

        TableColumn<PedidoDetalle, String> colObs = new TableColumn<>("Observación");
        colObs.setCellValueFactory(new PropertyValueFactory<>("observacionExtra"));
        colObs.setPrefWidth(200);

        tblDetalles.getColumns().clear();
        tblDetalles.getColumns().addAll(colPlato, colCant, colObs);

        // Cargar pedidos
        cargarPedidos();
        Stage stage = (Stage) lstPedidos.getScene().getWindow();
        if (stage != null) {
            stage.setMinWidth(800);
            stage.setMinHeight(600);
        }
    }

    private void cargarPedidos() {
        // Mostrar solo pedidos EN_PROCESO y LISTO
        List<Pedido> pedidos = pedidoRepository.findByEstado("EN_PROCESO");
        pedidos.addAll(pedidoRepository.findByEstado("LISTO"));
        pedidosEnCocina.setAll(pedidos);
        lstPedidos.setItems(pedidosEnCocina);
    }

    private void mostrarDetallePedido(Pedido pedido) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        lblCodigo.setText("Código: " + pedido.getCodigo());
        lblMesa.setText("Mesa: " + pedido.getMesaId());
        lblFecha.setText("Fecha: " + (pedido.getCreatedAt() != null ? pedido.getCreatedAt().format(formatter) : "-"));
        lblTotal.setText("Total: S/ " + pedido.getTotal().toString());
        lblEstado.setText("Estado: " + pedido.getEstado());
        txtObservacion.setText(pedido.getObservacionExtra());

        List<PedidoDetalle> detalles = pedidoDetalleRepository.findByPedidoId(pedido.getId());
        tblDetalles.setItems(FXCollections.observableArrayList(detalles));
    }

    @FXML
    private void marcarListo() {
        if (pedidoSeleccionado == null) {
            mostrarMensaje("Seleccione un pedido");
            return;
        }

        if ("LISTO".equals(pedidoSeleccionado.getEstado())) {
            mostrarMensaje("Este pedido ya está marcado como LISTO");
            return;
        }

        pedidoSeleccionado.setEstado("LISTO");  // ✅ Estado correcto
        pedidoSeleccionado.setUpdatedAt(LocalDateTime.now());
        pedidoRepository.save(pedidoSeleccionado);

        cargarPedidos();
        limpiarDetalle();
        mostrarMensaje("Pedido marcado como LISTO - Listo para servir");
    }

    @FXML
    private void cambiarEstado() {
        if (pedidoSeleccionado == null) {
            mostrarMensaje("Seleccione un pedido");
            return;
        }

        String nuevoEstado = cmbCambioEstado.getValue();
        if (nuevoEstado == null) {
            mostrarMensaje("Seleccione un estado");
            return;
        }

        pedidoSeleccionado.setEstado(nuevoEstado);
        pedidoSeleccionado.setUpdatedAt(LocalDateTime.now());
        pedidoRepository.save(pedidoSeleccionado);

        cargarPedidos();
        mostrarDetallePedido(pedidoSeleccionado);
        mostrarMensaje("Estado actualizado a: " + nuevoEstado);
    }

    @FXML
    private void actualizar() {
        cargarPedidos();
        mostrarMensaje("Lista actualizada");
    }

    @FXML
    private void regresar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PanelPrincipalView.fxml"));
            loader.setControllerFactory(ApplicationContextProvider.getApplicationContext()::getBean);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Restaurante UTP");
            stage.setScene(new Scene(root, 450, 480));
            stage.setMaximized(true);
            stage.show();

            Stage currentStage = (Stage) lstPedidos.getScene().getWindow();
            currentStage.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void limpiarDetalle() {
        lblCodigo.setText("Código: -");
        lblMesa.setText("Mesa: -");
        lblFecha.setText("Fecha: -");
        lblTotal.setText("Total: S/ 0.00");
        lblEstado.setText("Estado: -");
        txtObservacion.clear();
        tblDetalles.getItems().clear();
        pedidoSeleccionado = null;
    }

    private void mostrarMensaje(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Mensaje");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    @FXML
    private void cerrarSesion() {
        Stage stage = (Stage) lstPedidos.getScene().getWindow();
        stage.close();
    }
}