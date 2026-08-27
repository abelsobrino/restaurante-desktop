package pe.edu.utp.restaurante.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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
import pe.edu.utp.restaurante.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdminController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PlatoRepository platoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

    private Usuario usuarioActual;

    @FXML
    private TableView<Usuario> tblUsuarios;
    @FXML
    private TextField txtDni, txtNombre, txtApellido, txtEmail;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private ComboBox<String> cmbRol;
    @FXML
    private Button btnGuardarUsuario, btnEliminarUsuario;

    @FXML
    private TableView<Mesa> tblMesas;
    @FXML
    private TextField txtNumeroMesa, txtCapacidadMesa;
    @FXML
    private ComboBox<String> cmbEstadoMesa;
    @FXML
    private Button btnGuardarMesa, btnEliminarMesa;

    @FXML
    private TableView<Plato> tblPlatos;
    @FXML
    private TextField txtNombrePlato, txtDescripcionPlato, txtPrecioPlato;
    @FXML
    private ComboBox<String> cmbCategoriaPlato;
    @FXML
    private CheckBox chkDisponiblePlato;
    @FXML
    private Button btnGuardarPlato, btnEliminarPlato;

    @FXML
    private Label lblTotalVentas, lblTotalPedidos, lblTotalPlatosVendidos, lblTotalUsuarios;
    @FXML
    private BarChart<String, Number> chartVentas;
    @FXML
    private ComboBox<String> cmbFiltroPeriodo;
    @FXML
    private Button btnRegresar;

    @FXML
    public void initialize() {
        System.out.println("[ADMIN] Inicializando controlador...");

        // Configurar ComboBoxes
        if (cmbRol != null) {
            cmbRol.setItems(FXCollections.observableArrayList("ADMIN", "CAJERO", "MOZO", "COCINERO"));
        }
        if (cmbEstadoMesa != null) {
            cmbEstadoMesa.setItems(FXCollections.observableArrayList("DISPONIBLE", "OCUPADA", "RESERVADA"));
        }
        if (cmbCategoriaPlato != null) {
            cmbCategoriaPlato.setItems(FXCollections.observableArrayList(
                    "Entradas", "Platos Principales", "Bebidas", "Postres", "Carnes", "Pescados"
            ));
        }
        if (cmbFiltroPeriodo != null) {
            cmbFiltroPeriodo.setItems(FXCollections.observableArrayList("Hoy", "Esta Semana", "Este Mes"));
            cmbFiltroPeriodo.setValue("Hoy");
            cmbFiltroPeriodo.setOnAction(e -> cargarEstadisticas());
        }

        configurarTablaUsuarios();
        configurarTablaMesas();
        configurarTablaPlatos();

        cargarUsuarios();
        cargarMesas();
        cargarPlatos();
        cargarEstadisticas();

        if (tblUsuarios != null) {
            tblUsuarios.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (newVal != null) {
                    txtDni.setText(newVal.getDni());
                    txtNombre.setText(newVal.getNombre());
                    txtApellido.setText(newVal.getApellido());
                    txtEmail.setText(newVal.getEmail());
                    cmbRol.setValue(newVal.getRol());
                    txtPassword.clear();
                }
            });
        }
        if (tblMesas != null) {
            tblMesas.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (newVal != null) {
                    txtNumeroMesa.setText(String.valueOf(newVal.getNumero()));
                    txtCapacidadMesa.setText(String.valueOf(newVal.getCapacidad()));
                    cmbEstadoMesa.setValue(newVal.getEstado());
                }
            });
        }
        if (tblPlatos != null) {
            tblPlatos.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (newVal != null) {
                    txtNombrePlato.setText(newVal.getNombre());
                    txtDescripcionPlato.setText(newVal.getDescripcion());
                    txtPrecioPlato.setText(String.valueOf(newVal.getPrecio()));
                    chkDisponiblePlato.setSelected(newVal.getDisponible() != null && newVal.getDisponible());
                }
            });
        }

        System.out.println("[ADMIN] Inicialización completada");
    }

    public void setUsuario(Usuario usuario) {
        this.usuarioActual = usuario;
    }

    private void configurarTablaUsuarios() {
        if (tblUsuarios == null) return;
        tblUsuarios.getColumns().clear();
        TableColumn<Usuario, String> colDni = new TableColumn<>("DNI");
        colDni.setCellValueFactory(new PropertyValueFactory<>("dni"));
        TableColumn<Usuario, String> colNombre = new TableColumn<>("Nombre");
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        TableColumn<Usuario, String> colApellido = new TableColumn<>("Apellido");
        colApellido.setCellValueFactory(new PropertyValueFactory<>("apellido"));
        TableColumn<Usuario, String> colEmail = new TableColumn<>("Email");
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        TableColumn<Usuario, String> colRol = new TableColumn<>("Rol");
        colRol.setCellValueFactory(new PropertyValueFactory<>("rol"));
        tblUsuarios.getColumns().addAll(colDni, colNombre, colApellido, colEmail, colRol);
    }

    private void configurarTablaMesas() {
        if (tblMesas == null) return;
        tblMesas.getColumns().clear();
        TableColumn<Mesa, Integer> colNumero = new TableColumn<>("Número");
        colNumero.setCellValueFactory(new PropertyValueFactory<>("numero"));
        TableColumn<Mesa, Integer> colCapacidad = new TableColumn<>("Capacidad");
        colCapacidad.setCellValueFactory(new PropertyValueFactory<>("capacidad"));
        TableColumn<Mesa, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        tblMesas.getColumns().addAll(colNumero, colCapacidad, colEstado);
    }

    private void configurarTablaPlatos() {
        if (tblPlatos == null) return;
        tblPlatos.getColumns().clear();
        TableColumn<Plato, String> colNombre = new TableColumn<>("Nombre");
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        TableColumn<Plato, BigDecimal> colPrecio = new TableColumn<>("Precio");
        colPrecio.setCellValueFactory(new PropertyValueFactory<>("precio"));
        TableColumn<Plato, Boolean> colDisponible = new TableColumn<>("Disponible");
        colDisponible.setCellValueFactory(new PropertyValueFactory<>("disponible"));
        tblPlatos.getColumns().addAll(colNombre, colPrecio, colDisponible);
    }

    @FXML
    private void guardarUsuario() {
        try {
            String dni = txtDni.getText().trim();
            if (dni.isEmpty()) {
                mostrarAlerta("Error", "Ingrese DNI", Alert.AlertType.ERROR);
                return;
            }

            Usuario usuario = usuarioRepository.findByDni(dni).orElse(new Usuario());
            usuario.setDni(dni);
            usuario.setNombre(txtNombre.getText().trim());
            usuario.setApellido(txtApellido.getText().trim());
            usuario.setEmail(txtEmail.getText().trim());
            usuario.setRol(cmbRol.getValue());

            if (!txtPassword.getText().isEmpty()) {
                usuario.setPassword(txtPassword.getText().trim());
            } else if (usuario.getId() == null) {
                usuario.setPassword("123456");
            }

            if (usuario.getId() == null) {
                usuario.setActivo(true);
                usuario.setCreatedAt(LocalDateTime.now());
            }
            usuario.setUpdatedAt(LocalDateTime.now());

            usuarioRepository.save(usuario);
            cargarUsuarios();
            limpiarFormularioUsuario();
            mostrarAlerta("Éxito", "Usuario guardado", Alert.AlertType.INFORMATION);
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void eliminarUsuario() {
        Usuario usuario = tblUsuarios.getSelectionModel().getSelectedItem();
        if (usuario == null) {
            mostrarAlerta("Error", "Seleccione un usuario", Alert.AlertType.ERROR);
            return;
        }
        usuarioRepository.delete(usuario);
        cargarUsuarios();
        mostrarAlerta("Éxito", "Usuario eliminado", Alert.AlertType.INFORMATION);
    }

    @FXML
    private void guardarMesa() {
        try {
            int numero = Integer.parseInt(txtNumeroMesa.getText().trim());
            Mesa mesa = mesaRepository.findByNumero(numero).orElse(new Mesa());
            mesa.setNumero(numero);
            mesa.setCapacidad(Integer.parseInt(txtCapacidadMesa.getText().trim()));
            mesa.setEstado(cmbEstadoMesa.getValue());
            if (mesa.getId() == null) {
                mesa.setCreatedAt(LocalDateTime.now());
            }
            mesaRepository.save(mesa);
            cargarMesas();
            limpiarFormularioMesa();
            mostrarAlerta("Éxito", "Mesa guardada", Alert.AlertType.INFORMATION);
        } catch (NumberFormatException e) {
            mostrarAlerta("Error", "Ingrese números válidos", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void eliminarMesa() {
        Mesa mesa = tblMesas.getSelectionModel().getSelectedItem();
        if (mesa == null) {
            mostrarAlerta("Error", "Seleccione una mesa", Alert.AlertType.ERROR);
            return;
        }
        mesaRepository.delete(mesa);
        cargarMesas();
        mostrarAlerta("Éxito", "Mesa eliminada", Alert.AlertType.INFORMATION);
    }

    @FXML
    private void guardarPlato() {
        try {
            String nombre = txtNombrePlato.getText().trim();
            if (nombre.isEmpty()) {
                mostrarAlerta("Error", "Ingrese nombre del plato", Alert.AlertType.ERROR);
                return;
            }

            Plato plato = new Plato();
            plato.setNombre(nombre);
            plato.setDescripcion(txtDescripcionPlato.getText().trim());
            plato.setPrecio(new BigDecimal(txtPrecioPlato.getText().trim()));
            plato.setDisponible(chkDisponiblePlato.isSelected());
            plato.setCreatedAt(LocalDateTime.now());
            plato.setUpdatedAt(LocalDateTime.now());

            platoRepository.save(plato);
            cargarPlatos();
            limpiarFormularioPlato();
            mostrarAlerta("Éxito", "Plato guardado", Alert.AlertType.INFORMATION);
        } catch (NumberFormatException e) {
            mostrarAlerta("Error", "Ingrese un precio válido", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void eliminarPlato() {
        Plato plato = tblPlatos.getSelectionModel().getSelectedItem();
        if (plato == null) {
            mostrarAlerta("Error", "Seleccione un plato", Alert.AlertType.ERROR);
            return;
        }
        platoRepository.delete(plato);
        cargarPlatos();
        mostrarAlerta("Éxito", "Plato eliminado", Alert.AlertType.INFORMATION);
    }

    @FXML
    private void actualizarEstadisticas() {
        cargarEstadisticas();
        mostrarAlerta("Éxito", "Estadísticas actualizadas", Alert.AlertType.INFORMATION);
    }

    private void cargarEstadisticas() {
        try {
            String periodo = cmbFiltroPeriodo.getValue();
            if (periodo == null) periodo = "Hoy";

            List<Pedido> pedidos = pedidoRepository.findByEstado("ENTREGADO");
            BigDecimal totalVentas = pedidos.stream()
                    .map(Pedido::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (lblTotalVentas != null) {
                lblTotalVentas.setText("S/ " + totalVentas.toString());
            }
            if (lblTotalPedidos != null) {
                lblTotalPedidos.setText(String.valueOf(pedidos.size()));
            }

            long totalPlatos = 0;
            for (Pedido pedido : pedidos) {
                List<PedidoDetalle> detalles = pedidoDetalleRepository.findByPedidoId(pedido.getId());
                totalPlatos += detalles.stream().mapToInt(PedidoDetalle::getCantidad).sum();
            }
            if (lblTotalPlatosVendidos != null) {
                lblTotalPlatosVendidos.setText(String.valueOf(totalPlatos));
            }
            if (lblTotalUsuarios != null) {
                lblTotalUsuarios.setText(String.valueOf(usuarioRepository.count()));
            }

            cargarGrafico();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[ERROR] Error al cargar estadísticas: " + e.getMessage());
        }
    }

    private void cargarGrafico() {
        try {
            if (chartVentas == null) return;
            chartVentas.getData().clear();

            List<Pedido> pedidos = pedidoRepository.findByEstado("ENTREGADO");
            Map<Long, Integer> conteoPlatos = new HashMap<>();

            for (Pedido pedido : pedidos) {
                List<PedidoDetalle> detalles = pedidoDetalleRepository.findByPedidoId(pedido.getId());
                for (PedidoDetalle detalle : detalles) {
                    conteoPlatos.merge(detalle.getPlatoId(), detalle.getCantidad(), Integer::sum);
                }
            }

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Platos más vendidos");

            conteoPlatos.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(10)
                    .forEach(entry -> {
                        String nombre = platoRepository.findById(entry.getKey())
                                .map(Plato::getNombre)
                                .orElse("Desconocido");
                        series.getData().add(new XYChart.Data<>(nombre, entry.getValue()));
                    });

            chartVentas.getData().add(series);

        } catch (Exception e) {
            System.err.println("[ERROR] Error al cargar gráfico: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void limpiarFormularioUsuario() {
        txtDni.clear();
        txtNombre.clear();
        txtApellido.clear();
        txtEmail.clear();
        txtPassword.clear();
        cmbRol.setValue(null);
        tblUsuarios.getSelectionModel().clearSelection();
    }

    private void limpiarFormularioMesa() {
        txtNumeroMesa.clear();
        txtCapacidadMesa.clear();
        cmbEstadoMesa.setValue(null);
        tblMesas.getSelectionModel().clearSelection();
    }

    private void limpiarFormularioPlato() {
        txtNombrePlato.clear();
        txtDescripcionPlato.clear();
        txtPrecioPlato.clear();
        chkDisponiblePlato.setSelected(true);
        tblPlatos.getSelectionModel().clearSelection();
    }

    private void cargarUsuarios() {
        if (tblUsuarios != null) {
            tblUsuarios.setItems(FXCollections.observableArrayList(usuarioRepository.findAll()));
        }
    }

    private void cargarMesas() {
        if (tblMesas != null) {
            tblMesas.setItems(FXCollections.observableArrayList(mesaRepository.findAll()));
        }
    }

    private void cargarPlatos() {
        if (tblPlatos != null) {
            tblPlatos.setItems(FXCollections.observableArrayList(platoRepository.findAll()));
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

            Stage currentStage = (Stage) tblUsuarios.getScene().getWindow();
            currentStage.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void cerrarSesion() {
        Stage stage = (Stage) tblUsuarios.getScene().getWindow();
        stage.close();
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}