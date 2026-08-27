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
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.restaurante.config.ApplicationContextProvider;
import pe.edu.utp.restaurante.model.Mesa;
import pe.edu.utp.restaurante.model.Pedido;
import pe.edu.utp.restaurante.model.PedidoDetalle;
import pe.edu.utp.restaurante.model.Plato;
import pe.edu.utp.restaurante.model.Usuario;
import pe.edu.utp.restaurante.repository.MesaRepository;
import pe.edu.utp.restaurante.repository.PedidoDetalleRepository;
import pe.edu.utp.restaurante.repository.PedidoRepository;
import pe.edu.utp.restaurante.repository.PlatoRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class MozoController {

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PlatoRepository platoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

    private Usuario usuarioActual;
    private Mesa mesaSeleccionada;
    private Pedido pedidoActualEnBD;
    private ObservableList<Mesa> mesas = FXCollections.observableArrayList();
    private ObservableList<Plato> platos = FXCollections.observableArrayList();
    private ObservableList<Plato> platosFiltrados = FXCollections.observableArrayList();
    private ObservableList<PedidoDetalle> pedidoActual = FXCollections.observableArrayList();

    @FXML
    private Text txtUsuario;

    @FXML
    private Text txtMesaSeleccionada;

    @FXML
    private ListView<Mesa> lstMesas;

    @FXML
    private ListView<Plato> lstPlatos;

    @FXML
    private TextField txtBuscarPlato;

    @FXML
    private Spinner<Integer> spnCantidad;

    @FXML
    private TableView<PedidoDetalle> tblPedido;

    @FXML
    private Label lblSubtotal;

    @FXML
    private TextArea txtObservacion;

    @FXML
    private Button btnLiberarMesa;

    @FXML
    private Button btnAgregarPlato;

    @FXML
    private Button btnEnviarCocina;

    @FXML
    private Button btnTerminarPedido;

    @FXML
    private Button btnCancelarPedido;

    @FXML
    private Button btnRegresar;

    @FXML
    public void initialize() {
        System.out.println("[MOZO] Inicializando controlador...");

        // Configurar Spinner
        if (spnCantidad != null) {
            spnCantidad.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 1));
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
                        String estado = mesa.getEstado() != null ? mesa.getEstado() : "DISPONIBLE";
                        setText("Mesa " + mesa.getNumero() + " - " + estado);
                        if ("OCUPADA".equals(estado)) {
                            setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                        } else if ("RESERVADA".equals(estado)) {
                            setStyle("-fx-text-fill: #e65100;");
                        } else {
                            setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                        }
                    }
                }
            });

            lstMesas.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    mesaSeleccionada = newVal;
                    txtMesaSeleccionada.setText("Mesa: " + newVal.getNumero() + " (" + newVal.getEstado() + ")");
                    cargarPedidoExistente(newVal);
                }
            });
        }

        // Configurar ListView de Platos
        if (lstPlatos != null) {
            lstPlatos.setCellFactory(lv -> new ListCell<Plato>() {
                @Override
                protected void updateItem(Plato plato, boolean empty) {
                    super.updateItem(plato, empty);
                    if (empty || plato == null) {
                        setText(null);
                    } else {
                        setText(plato.getNombre() + " - S/ " + plato.getPrecio());
                    }
                }
            });
        }

        // Configurar TableView del pedido
        if (tblPedido != null) {
            TableColumn<PedidoDetalle, String> colPlato = new TableColumn<>("Plato");
            colPlato.setCellValueFactory(cellData -> {
                String nombre = platoRepository.findById(cellData.getValue().getPlatoId())
                        .map(Plato::getNombre)
                        .orElse("Desconocido");
                return javafx.beans.binding.Bindings.createStringBinding(() -> nombre);
            });
            colPlato.setPrefWidth(180);

            TableColumn<PedidoDetalle, Integer> colCantidad = new TableColumn<>("Cant.");
            colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
            colCantidad.setPrefWidth(60);

            TableColumn<PedidoDetalle, BigDecimal> colPrecio = new TableColumn<>("Precio");
            colPrecio.setCellValueFactory(new PropertyValueFactory<>("precioUnitario"));
            colPrecio.setPrefWidth(80);

            TableColumn<PedidoDetalle, BigDecimal> colSubtotal = new TableColumn<>("Subtotal");
            colSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));
            colSubtotal.setPrefWidth(80);

            TableColumn<PedidoDetalle, Void> colAccion = new TableColumn<>("Acción");
            colAccion.setCellFactory(param -> new TableCell<>() {
                private final Button btnEliminar = new Button("✕");
                {
                    btnEliminar.setStyle("-fx-background-color: #b71c1c; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                    btnEliminar.setOnAction(event -> {
                        PedidoDetalle detalle = getTableView().getItems().get(getIndex());
                        pedidoActual.remove(detalle);
                        actualizarSubtotal();
                        if (pedidoActual.isEmpty() && pedidoActualEnBD != null) {
                            pedidoActualEnBD.setEstado("CANCELADO");
                            pedidoRepository.save(pedidoActualEnBD);
                            pedidoActualEnBD = null;
                        }
                    });
                }
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        setGraphic(btnEliminar);
                    }
                }
            });
            colAccion.setPrefWidth(60);

            tblPedido.getColumns().clear();
            tblPedido.getColumns().addAll(colPlato, colCantidad, colPrecio, colSubtotal, colAccion);
            tblPedido.setItems(pedidoActual);
        }

        // Cargar datos
        cargarMesas();
        cargarPlatos();

        // Eventos
        if (txtBuscarPlato != null) {
            txtBuscarPlato.textProperty().addListener((obs, oldVal, newVal) -> filtrarPlatos());
        }

        // Deshabilitar botones
        if (btnAgregarPlato != null && lstMesas != null) {
            btnAgregarPlato.disableProperty().bind(lstMesas.getSelectionModel().selectedItemProperty().isNull());
        }

        if (btnEnviarCocina != null && tblPedido != null && lstMesas != null) {
            btnEnviarCocina.disableProperty().bind(
                    lstMesas.getSelectionModel().selectedItemProperty().isNull()
                            .or(Bindings.isEmpty(tblPedido.getItems()))
            );
        }

        if (btnTerminarPedido != null && tblPedido != null && lstMesas != null) {
            btnTerminarPedido.disableProperty().bind(
                    lstMesas.getSelectionModel().selectedItemProperty().isNull()
                            .or(Bindings.isEmpty(tblPedido.getItems()))
            );
        }

        System.out.println("[MOZO] Inicialización completada");
    }

    public void setUsuario(Usuario usuario) {
        this.usuarioActual = usuario;
        if (txtUsuario != null) {
            txtUsuario.setText("Usuario: " + usuario.getNombre() + " " + usuario.getApellido() + " (DNI: " + usuario.getDni() + ")");
        }
    }

    private void cargarMesas() {
        try {
            if (lstMesas != null) {
                List<Mesa> listaMesas = mesaRepository.findAll();
                System.out.println("[MOZO] Mesas encontradas: " + listaMesas.size());
                mesas.setAll(listaMesas);
                lstMesas.setItems(mesas);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error al cargar mesas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cargarPlatos() {
        try {
            if (lstPlatos != null) {
                List<Plato> listaPlatos = platoRepository.findByDisponibleTrue();
                System.out.println("[MOZO] Platos encontrados: " + listaPlatos.size());
                platos.setAll(listaPlatos);
                lstPlatos.setItems(platos);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error al cargar platos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void filtrarPlatos() {
        if (lstPlatos == null || txtBuscarPlato == null) return;
        String filtro = txtBuscarPlato.getText().toLowerCase();
        if (filtro.isEmpty()) {
            lstPlatos.setItems(platos);
        } else {
            platosFiltrados.setAll(platos.stream()
                    .filter(p -> p.getNombre().toLowerCase().contains(filtro))
                    .toList());
            lstPlatos.setItems(platosFiltrados);
        }
    }

    @FXML
    private void liberarMesa() {
        if (mesaSeleccionada == null) {
            mostrarAlerta("Error", "Seleccione una mesa", Alert.AlertType.ERROR);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Liberar Mesa");
        confirm.setHeaderText("¿Está seguro de liberar la mesa " + mesaSeleccionada.getNumero() + "?");
        confirm.setContentText("Esto cancelará el pedido actual");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                if (pedidoActualEnBD != null && !pedidoActualEnBD.getEstado().equals("ENTREGADO")) {
                    pedidoActualEnBD.setEstado("CANCELADO");
                    pedidoActualEnBD.setCerrado(true);
                    pedidoRepository.save(pedidoActualEnBD);
                }
                mesaSeleccionada.setEstado("DISPONIBLE");
                mesaRepository.save(mesaSeleccionada);
                pedidoActual.clear();
                pedidoActualEnBD = null;
                actualizarSubtotal();
                cargarMesas();
                if (txtMesaSeleccionada != null) {
                    txtMesaSeleccionada.setText("Mesa: Ninguna");
                }
                mesaSeleccionada = null;
                mostrarAlerta("Éxito", "Mesa liberada correctamente", Alert.AlertType.INFORMATION);
            }
        });
    }

    @FXML
    private void agregarPlato() {
        if (lstPlatos == null) {
            mostrarAlerta("Error", "Lista de platos no disponible", Alert.AlertType.ERROR);
            return;
        }

        Plato plato = lstPlatos.getSelectionModel().getSelectedItem();
        if (plato == null) {
            mostrarAlerta("Error", "Seleccione un plato", Alert.AlertType.ERROR);
            return;
        }

        if (mesaSeleccionada == null) {
            mostrarAlerta("Error", "Seleccione una mesa primero", Alert.AlertType.ERROR);
            return;
        }

        int cantidad = 1;
        if (spnCantidad != null) {
            cantidad = spnCantidad.getValue();
        }

        // Buscar si el plato ya está en el pedido
        for (PedidoDetalle detalle : pedidoActual) {
            if (detalle.getPlatoId().equals(plato.getId())) {
                detalle.setCantidad(detalle.getCantidad() + cantidad);
                detalle.setSubtotal(detalle.getPrecioUnitario().multiply(BigDecimal.valueOf(detalle.getCantidad())));
                actualizarSubtotal();
                return;
            }
        }

        // Agregar nuevo detalle
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setPlatoId(plato.getId());
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(plato.getPrecio());
        detalle.setSubtotal(plato.getPrecio().multiply(BigDecimal.valueOf(cantidad)));
        detalle.setEstado("PENDIENTE");
        detalle.setCreatedAt(LocalDateTime.now());
        pedidoActual.add(detalle);
        actualizarSubtotal();

        if (!"OCUPADA".equals(mesaSeleccionada.getEstado())) {
            mesaSeleccionada.setEstado("OCUPADA");
            mesaRepository.save(mesaSeleccionada);
            cargarMesas();
        }
    }

    @Transactional
    @FXML
    protected void enviarCocina() {
        if (pedidoActual.isEmpty()) {
            mostrarAlerta("Error", "El pedido está vacío", Alert.AlertType.ERROR);
            return;
        }

        if (mesaSeleccionada == null) {
            mostrarAlerta("Error", "Seleccione una mesa", Alert.AlertType.ERROR);
            return;
        }

        try {
            if (pedidoActualEnBD == null) {
                pedidoActualEnBD = new Pedido();
                pedidoActualEnBD.setCodigo(generarCodigoPedido());
                pedidoActualEnBD.setTipo("LOCAL");
                pedidoActualEnBD.setMesaId(mesaSeleccionada.getId());
                pedidoActualEnBD.setUsuarioId(usuarioActual.getId());
                pedidoActualEnBD.setCreatedAt(LocalDateTime.now());
            }

            if (txtObservacion != null) {
                pedidoActualEnBD.setObservacionExtra(txtObservacion.getText());
            }
            pedidoActualEnBD.setEstado("EN_PROCESO");
            pedidoActualEnBD.setCerrado(false);
            pedidoActualEnBD.setUpdatedAt(LocalDateTime.now());

            BigDecimal subtotal = pedidoActual.stream()
                    .map(PedidoDetalle::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            pedidoActualEnBD.setSubtotal(subtotal);
            pedidoActualEnBD.setTotal(subtotal);

            pedidoActualEnBD = pedidoRepository.save(pedidoActualEnBD);

            pedidoDetalleRepository.deleteByPedidoId(pedidoActualEnBD.getId());
            for (PedidoDetalle detalle : pedidoActual) {
                detalle.setPedidoId(pedidoActualEnBD.getId());
                detalle.setCreatedAt(LocalDateTime.now());
                pedidoDetalleRepository.save(detalle);
            }

            mostrarAlerta("Éxito", "Pedido enviado a cocina", Alert.AlertType.INFORMATION);

        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "Error al enviar pedido: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @Transactional
    @FXML
    protected void terminarPedido() {
        if (pedidoActual.isEmpty()) {
            mostrarAlerta("Error", "El pedido está vacío", Alert.AlertType.ERROR);
            return;
        }

        if (mesaSeleccionada == null) {
            mostrarAlerta("Error", "Seleccione una mesa", Alert.AlertType.ERROR);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Terminar Pedido");
        confirm.setHeaderText("¿Terminar pedido de la Mesa " + mesaSeleccionada.getNumero() + "?");
        confirm.setContentText("El pedido será enviado a CAJA para el cobro.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    if (pedidoActualEnBD == null) {
                        pedidoActualEnBD = new Pedido();
                        pedidoActualEnBD.setCodigo(generarCodigoPedido());
                        pedidoActualEnBD.setTipo("LOCAL");
                        pedidoActualEnBD.setMesaId(mesaSeleccionada.getId());
                        pedidoActualEnBD.setUsuarioId(usuarioActual.getId());
                        pedidoActualEnBD.setCreatedAt(LocalDateTime.now());
                    }

                    if (txtObservacion != null) {
                        pedidoActualEnBD.setObservacionExtra(txtObservacion.getText());
                    }
                    pedidoActualEnBD.setEstado("TERMINADO");
                    pedidoActualEnBD.setCerrado(true);
                    pedidoActualEnBD.setUpdatedAt(LocalDateTime.now());
                    pedidoActualEnBD.setFechaCierre(LocalDateTime.now());

                    BigDecimal subtotal = pedidoActual.stream()
                            .map(PedidoDetalle::getSubtotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    pedidoActualEnBD.setSubtotal(subtotal);
                    pedidoActualEnBD.setTotal(subtotal);

                    pedidoActualEnBD = pedidoRepository.save(pedidoActualEnBD);

                    pedidoDetalleRepository.deleteByPedidoId(pedidoActualEnBD.getId());
                    for (PedidoDetalle detalle : pedidoActual) {
                        detalle.setPedidoId(pedidoActualEnBD.getId());
                        detalle.setCreatedAt(LocalDateTime.now());
                        pedidoDetalleRepository.save(detalle);
                    }

                    mesaSeleccionada.setEstado("DISPONIBLE");
                    mesaRepository.save(mesaSeleccionada);

                    pedidoActual.clear();
                    pedidoActualEnBD = null;
                    actualizarSubtotal();
                    if (txtObservacion != null) {
                        txtObservacion.clear();
                    }
                    cargarMesas();
                    if (txtMesaSeleccionada != null) {
                        txtMesaSeleccionada.setText("Mesa: Ninguna");
                    }
                    mesaSeleccionada = null;

                    mostrarAlerta("Éxito", "Pedido enviado a CAJA para el cobro", Alert.AlertType.INFORMATION);

                } catch (Exception e) {
                    e.printStackTrace();
                    mostrarAlerta("Error", "Error al terminar pedido: " + e.getMessage(), Alert.AlertType.ERROR);
                }
            }
        });
    }

    @FXML
    private void cancelarPedido() {
        if (pedidoActual.isEmpty()) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Cancelar Pedido");
        confirm.setHeaderText("¿Cancelar el pedido actual?");
        confirm.setContentText("Se eliminarán todos los platos agregados.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                pedidoActual.clear();
                actualizarSubtotal();
                if (txtObservacion != null) {
                    txtObservacion.clear();
                }
                if (pedidoActualEnBD != null && !pedidoActualEnBD.getEstado().equals("ENTREGADO")) {
                    pedidoActualEnBD.setEstado("CANCELADO");
                    pedidoActualEnBD.setCerrado(true);
                    pedidoRepository.save(pedidoActualEnBD);
                    pedidoActualEnBD = null;
                }
                mostrarAlerta("Información", "Pedido cancelado", Alert.AlertType.INFORMATION);
            }
        });
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

    private void cargarPedidoExistente(Mesa mesa) {
        try {
            Optional<Pedido> pedidoOpt = pedidoRepository.findByMesaIdAndEstadoNot(mesa.getId(), "ENTREGADO");
            if (pedidoOpt.isPresent()) {
                pedidoActualEnBD = pedidoOpt.get();
                List<PedidoDetalle> detalles = pedidoDetalleRepository.findByPedidoId(pedidoActualEnBD.getId());
                pedidoActual.clear();
                pedidoActual.addAll(detalles);
                actualizarSubtotal();
                if (txtObservacion != null) {
                    txtObservacion.setText(pedidoActualEnBD.getObservacionExtra());
                }
            } else {
                pedidoActualEnBD = null;
                pedidoActual.clear();
                actualizarSubtotal();
                if (txtObservacion != null) {
                    txtObservacion.clear();
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error al cargar pedido existente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String generarCodigoPedido() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        return "PED-" + LocalDateTime.now().format(formatter);
    }

    private void actualizarSubtotal() {
        if (lblSubtotal == null) return;
        BigDecimal total = pedidoActual.stream()
                .map(PedidoDetalle::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblSubtotal.setText("S/ " + total.toString());
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
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