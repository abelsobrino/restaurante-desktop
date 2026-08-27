package pe.edu.utp.restaurante.controller;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.config.ApplicationContextProvider;
import pe.edu.utp.restaurante.model.Mesa;
import pe.edu.utp.restaurante.model.Pago;
import pe.edu.utp.restaurante.model.Pedido;
import pe.edu.utp.restaurante.model.PedidoDetalle;
import pe.edu.utp.restaurante.model.Usuario;
import pe.edu.utp.restaurante.repository.MesaRepository;
import pe.edu.utp.restaurante.repository.PagoRepository;
import pe.edu.utp.restaurante.repository.PedidoDetalleRepository;
import pe.edu.utp.restaurante.repository.PedidoRepository;
import pe.edu.utp.restaurante.repository.PlatoRepository;
import pe.edu.utp.restaurante.model.Plato;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class CajaController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private PlatoRepository platoRepository;

    @Autowired
    private MesaRepository mesaRepository;

    private Usuario usuarioActual;
    private Pedido pedidoSeleccionado;
    private ObservableList<Mesa> mesas = FXCollections.observableArrayList();
    private ObservableList<PedidoDetalle> detallesPedido = FXCollections.observableArrayList();

    @FXML
    private Text txtUsuario;

    @FXML
    private ListView<Mesa> lstMesas;

    @FXML
    private TableView<PedidoDetalle> tblDetallePedido;

    @FXML
    private Label lblTotal;

    @FXML
    private Label lblMesaInfo;

    @FXML
    private Label lblFecha;

    @FXML
    private Label lblMozo;

    @FXML
    private Label lblCodigo;

    @FXML
    private Label lblEstado;

    @FXML
    private ComboBox<String> cmbMetodoPago;

    @FXML
    private TextField txtReferencia;

    @FXML
    private Button btnCobrar;

    @FXML
    private Button btnRegresar;

    @FXML
    public void initialize() {
        System.out.println("[CAJA] Inicializando controlador...");

        // Configurar métodos de pago
        if (cmbMetodoPago != null) {
            cmbMetodoPago.setItems(FXCollections.observableArrayList(
                    "EFECTIVO", "TARJETA", "YAPE", "TRANSFERENCIA", "OTRO"
            ));

            cmbMetodoPago.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (txtReferencia != null) {
                    txtReferencia.setVisible("YAPE".equals(newVal) || "TRANSFERENCIA".equals(newVal));
                }
            });
        }

        // Configurar ListView de Mesas
        if (lstMesas != null) {
            lstMesas.setCellFactory(lv -> new ListCell<Mesa>() {
                @Override
                protected void updateItem(Mesa mesa, boolean empty) {
                    super.updateItem(mesa, empty);
                    if (empty || mesa == null) {
                        setText(null);
                    } else {
                        setText("Mesa " + mesa.getNumero());
                    }
                }
            });

            lstMesas.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    cargarPedidosPorMesa(newVal);
                }
            });
        }

        // Configurar tabla de detalles
        if (tblDetallePedido != null) {
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

            TableColumn<PedidoDetalle, BigDecimal> colPrecio = new TableColumn<>("Precio");
            colPrecio.setCellValueFactory(new PropertyValueFactory<>("precioUnitario"));
            colPrecio.setPrefWidth(80);

            TableColumn<PedidoDetalle, BigDecimal> colSubtotal = new TableColumn<>("Subtotal");
            colSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));
            colSubtotal.setPrefWidth(80);

            tblDetallePedido.getColumns().clear();
            tblDetallePedido.getColumns().addAll(colPlato, colCant, colPrecio, colSubtotal);
            tblDetallePedido.setItems(detallesPedido);
        }

        // Cargar mesas
        cargarMesas();

        // Botón cobrar deshabilitado inicialmente
        if (btnCobrar != null && tblDetallePedido != null) {
            btnCobrar.disableProperty().bind(
                    tblDetallePedido.itemsProperty().isNull()
                            .or(Bindings.isEmpty(tblDetallePedido.getItems()))
            );
        }

        // ✅ ELIMINADO el código que intentaba acceder a Scene.getWindow()
        System.out.println("[CAJA] Inicialización completada");
    }

    public void setUsuario(Usuario usuario) {
        this.usuarioActual = usuario;
        if (txtUsuario != null) {
            txtUsuario.setText("Usuario: " + usuario.getNombre() + " " + usuario.getApellido());
        }
        cargarMesas();
    }

    private void cargarMesas() {
        try {
            if (lstMesas != null) {
                List<Mesa> listaMesas = mesaRepository.findAll();
                System.out.println("[CAJA] Mesas encontradas: " + listaMesas.size());
                mesas.clear();
                mesas.addAll(listaMesas);
                lstMesas.setItems(mesas);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error al cargar mesas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cargarPedidosPorMesa(Mesa mesa) {
        try {
            List<Pedido> pedidos = pedidoRepository.findByMesaIdAndEstado(mesa.getId(), "TERMINADO");
            if (!pedidos.isEmpty()) {
                pedidoSeleccionado = pedidos.get(0);
                cargarDetallePedido(pedidoSeleccionado.getId());
                mostrarInfoPedido(pedidoSeleccionado);
            } else {
                pedidoSeleccionado = null;
                detallesPedido.clear();
                lblTotal.setText("S/ 0.00");
                if (lblMesaInfo != null) {
                    lblMesaInfo.setText("Mesa: " + mesa.getNumero() + " - Sin pedidos para cobrar");
                }
                if (lblFecha != null) lblFecha.setText("Fecha: -");
                if (lblMozo != null) lblMozo.setText("Mozo: -");
                if (lblCodigo != null) lblCodigo.setText("Código: -");
                if (lblEstado != null) lblEstado.setText("Estado: -");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error al cargar pedidos por mesa: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void mostrarInfoPedido(Pedido pedido) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        if (lblMesaInfo != null) {
            lblMesaInfo.setText("Mesa: " + pedido.getMesaId());
        }
        if (lblFecha != null) {
            lblFecha.setText("Fecha: " + (pedido.getCreatedAt() != null ? pedido.getCreatedAt().format(formatter) : "-"));
        }
        if (lblMozo != null) {
            lblMozo.setText("Mozo: " + pedido.getUsuarioId());
        }
        if (lblCodigo != null) {
            lblCodigo.setText("Código: " + pedido.getCodigo());
        }
        if (lblEstado != null) {
            lblEstado.setText("Estado: " + pedido.getEstado());
        }
        if (lblTotal != null) {
            lblTotal.setText("S/ " + pedido.getTotal().toString());
        }
    }

    private void cargarDetallePedido(Long pedidoId) {
        try {
            List<PedidoDetalle> detalles = pedidoDetalleRepository.findByPedidoId(pedidoId);
            detallesPedido.clear();
            detallesPedido.addAll(detalles);
            if (tblDetallePedido != null) {
                tblDetallePedido.setItems(detallesPedido);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error al cargar detalle del pedido: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void cobrar() {
        if (pedidoSeleccionado == null) {
            mostrarMensaje("Seleccione un pedido", "error");
            return;
        }

        String metodo = cmbMetodoPago.getValue();
        if (metodo == null || metodo.isEmpty()) {
            mostrarMensaje("Seleccione un método de pago", "error");
            return;
        }

        try {
            Pago pago = new Pago();
            pago.setPedidoId(pedidoSeleccionado.getId());
            pago.setMonto(pedidoSeleccionado.getTotal());
            pago.setMetodo(metodo);
            pago.setReferencia(txtReferencia.getText().trim());
            pago.setUsuarioId(usuarioActual.getId());
            pago.setCreatedAt(LocalDateTime.now());
            pagoRepository.save(pago);

            pedidoSeleccionado.setEstado("ENTREGADO");
            pedidoSeleccionado.setCerrado(true);
            pedidoSeleccionado.setUpdatedAt(LocalDateTime.now());
            pedidoSeleccionado.setFechaCierre(LocalDateTime.now());
            pedidoRepository.save(pedidoSeleccionado);

            mostrarMensaje("✅ Cobro registrado exitosamente", "success");
            detallesPedido.clear();
            lblTotal.setText("S/ 0.00");
            if (lblFecha != null) lblFecha.setText("Fecha: -");
            if (lblMozo != null) lblMozo.setText("Mozo: -");
            if (lblCodigo != null) lblCodigo.setText("Código: -");
            if (lblEstado != null) lblEstado.setText("Estado: -");
            pedidoSeleccionado = null;
            cmbMetodoPago.getSelectionModel().clearSelection();
            txtReferencia.clear();

            cargarMesas();

        } catch (Exception e) {
            e.printStackTrace();
            mostrarMensaje("Error al cobrar: " + e.getMessage(), "error");
        }
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

            Stage currentStage = (Stage) txtUsuario.getScene().getWindow();
            currentStage.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarMensaje(String mensaje, String tipo) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Mensaje");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    @FXML
    private void cerrarSesion() {
        Stage stage = (Stage) txtUsuario.getScene().getWindow();
        stage.close();
    }
}