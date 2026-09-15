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
@org.springframework.context.annotation.Scope("prototype")
public class CajaController {
    @Autowired private pe.edu.utp.restaurante.service.CobroService cobroService;
    @Autowired private pe.edu.utp.restaurante.service.ComprobanteService comprobanteService;
    @Autowired private pe.edu.utp.restaurante.repository.UsuarioRepository usuarioRepository;
    @FXML private javafx.scene.layout.TilePane panelMesas;
    @FXML private ComboBox<Pedido> cmbPedidos;
    @FXML private CheckBox chkPagados;
    @FXML private Label lblBase, lblIgv;
    @FXML private Button btnComprobante;

    @FXML private void actualizarMesas() {
        cargarMesas();
        if (lstMesas.getSelectionModel().getSelectedItem() != null)
            cargarPedidosPorMesa(lstMesas.getSelectionModel().getSelectedItem());
    }

    private void dibujarMesas() {
        panelMesas.getChildren().clear();
        var pendientes = pedidoRepository.findByEstado("TERMINADO");
        for (Mesa mesa : mesas.stream().sorted(java.util.Comparator.comparing(Mesa::getNumero)).toList()) {
            long cantidad = pendientes.stream().filter(p -> java.util.Objects.equals(p.getMesaId(), mesa.getId())).count();
            Button boton = new Button("Mesa " + mesa.getNumero() + "\n" + (cantidad > 0 ? cantidad + " por cobrar" : "Sin cobros"));
            boton.setPrefSize(112, 88);
            boton.setStyle("-fx-background-color: " + (cantidad > 0 ? "#d97706" : "#475569") + "; -fx-text-fill: white; -fx-background-radius: 12; -fx-font-weight: bold; -fx-cursor: hand;");
            boton.setOnAction(e -> { lstMesas.getSelectionModel().select(mesa); cargarPedidosPorMesa(mesa); });
            panelMesas.getChildren().add(boton);
        }
    }

    @FXML private void exportarComprobante() {
        if (pedidoSeleccionado == null) return;
        var pago = pagoRepository.findFirstByPedidoIdOrderByCreatedAtDesc(pedidoSeleccionado.getId());
        if (pago.isEmpty()) { mostrarMensaje("Primero registra el cobro.", "error"); return; }
        guardarComprobante(pago.get());
    }

    private void guardarComprobante(Pago pago) {
        javafx.stage.FileChooser selector = new javafx.stage.FileChooser();
        selector.setTitle("Guardar comprobante interno");
        selector.setInitialFileName("LaFonda-Pago-" + pago.getId() + ".pdf");
        selector.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        java.io.File archivo = selector.showSaveDialog(txtUsuario.getScene().getWindow());
        if (archivo == null) return;
        try {
            comprobanteService.generar(pago, archivo.toPath());
            ButtonType abrir = new ButtonType("Abrir PDF");
            ButtonType imprimir = new ButtonType("Imprimir");
            Alert aviso = new Alert(Alert.AlertType.INFORMATION, "PDF guardado. El pago ya está registrado.", abrir, imprimir, ButtonType.CLOSE);
            var respuesta = aviso.showAndWait().orElse(ButtonType.CLOSE);
            if (respuesta == abrir) {
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(archivo);
                else mostrarMensaje("Abre el archivo con tu lector PDF: " + archivo, "success");
            } else if (respuesta == imprimir) {
                java.awt.EventQueue.invokeLater(() -> {
                    try (var pdf = org.apache.pdfbox.pdmodel.PDDocument.load(archivo)) {
                        java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
                        job.setPageable(new org.apache.pdfbox.printing.PDFPageable(pdf));
                        if (job.printDialog()) job.print();
                    } catch (Exception e) {
                        javafx.application.Platform.runLater(() -> mostrarMensaje("No se pudo imprimir. El PDF sigue guardado y el pago no se repite.", "error"));
                    }
                });
            }
        } catch (Exception e) {
            mostrarMensaje("El pago sigue registrado. No se pudo generar o abrir el PDF: " + e.getMessage() + ". Puedes reintentarlo con Ver pagados y PDF / imprimir.", "error");
        }
    }

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
        pedidoSeleccionado = null;
        detallesPedido.clear();
        cmbPedidos.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Pedido p) { return p == null ? "" : p.getCodigo() + " - " + p.getEstado() + " - S/ " + p.getTotal(); }
            @Override public Pedido fromString(String texto) { return null; }
        });
        cmbPedidos.valueProperty().addListener((o, a, p) -> {
            pedidoSeleccionado = p;
            detallesPedido.clear();
            lblTotal.setText("S/ 0.00"); lblBase.setText("S/ 0.00"); lblIgv.setText("S/ 0.00");
            btnComprobante.setDisable(p == null || !"ENTREGADO".equals(p.getEstado()));
            if (p != null) { cargarDetallePedido(p.getId()); mostrarInfoPedido(p); }
        });
        chkPagados.setOnAction(e -> actualizarMesas());
        System.out.println("[CAJA] Inicializando controlador...");

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

        cargarMesas();

        if (btnCobrar != null && tblDetallePedido != null) {
            btnCobrar.disableProperty().bind(
                    Bindings.createBooleanBinding(() -> cmbPedidos.getValue() == null || !"TERMINADO".equals(cmbPedidos.getValue().getEstado()), cmbPedidos.valueProperty())
            );
        }

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
                dibujarMesas();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error al cargar mesas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cargarPedidosPorMesa(Mesa mesa) {
        try {
            List<Pedido> pedidos = pedidoRepository.findByMesaIdAndEstado(mesa.getId(), "TERMINADO");
            if (chkPagados.isSelected()) pedidos.addAll(pedidoRepository.findByMesaIdAndEstado(mesa.getId(), "ENTREGADO"));
            pedidos.sort(java.util.Comparator.comparing(Pedido::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
            cmbPedidos.setItems(FXCollections.observableArrayList(pedidos));
            cmbPedidos.getSelectionModel().clearSelection();
            if (!pedidos.isEmpty()) {
                pedidoSeleccionado = pedidos.get(0);
                cmbPedidos.getSelectionModel().selectFirst();
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
            lblMesaInfo.setText(pedido.getMesaId() == null ? "Sin mesa" : mesaRepository.findById(pedido.getMesaId()).map(m -> String.valueOf(m.getNumero())).orElse("-"));
        }
        if (lblFecha != null) {
            lblFecha.setText("Fecha: " + (pedido.getCreatedAt() != null ? pedido.getCreatedAt().format(formatter) : "-"));
        }
        if (lblMozo != null) {
            lblMozo.setText(pedido.getUsuarioId() == null ? "-" : usuarioRepository.findById(pedido.getUsuarioId()).map(u -> u.getNombre() + " " + u.getApellido()).orElse("-"));
        }
        if (lblCodigo != null) {
            lblCodigo.setText("Código: " + pedido.getCodigo());
        }
        if (lblEstado != null) {
            lblEstado.setText("Estado: " + pedido.getEstado());
        }
        if (lblTotal != null) {
            lblTotal.setText("S/ " + pedido.getTotal().toString());
            var importes = pe.edu.utp.restaurante.service.Importes.desdeTotal(pedido.getTotal());
            lblBase.setText("S/ " + importes.subtotal());
            lblIgv.setText("S/ " + importes.igv());
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
            Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION, "Confirmar cobro de S/ " + pedidoSeleccionado.getTotal() + " por " + metodo + "?", ButtonType.OK, ButtonType.CANCEL);
            if (confirmar.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            Pago pago = cobroService.cobrar(pedidoSeleccionado.getId(), usuarioActual.getId(), metodo, txtReferencia.getText().trim());
            cmbPedidos.getSelectionModel().clearSelection();
            guardarComprobante(pago);

            mostrarMensaje("Cobro registrado exitosamente", "success");
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
            stage.setTitle("Restaurante LA FONDA");
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
        Alert alert = new Alert("error".equals(tipo) ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION);
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
