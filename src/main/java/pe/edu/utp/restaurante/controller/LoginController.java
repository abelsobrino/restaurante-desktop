package pe.edu.utp.restaurante.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.config.ApplicationContextProvider;
import pe.edu.utp.restaurante.model.Usuario;
import pe.edu.utp.restaurante.service.UsuarioService;

@Component
public class LoginController {

    @Autowired
    private UsuarioService usuarioService;

    @FXML
    private Text txtTitulo;

    @FXML
    private Text txtSubtitulo;

    @FXML
    private TextField txtDni;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private HBox passwordContainer;

    @FXML
    private Label lblError;

    @FXML
    private Label lblAyuda;

    @FXML
    private Button btnLogin;

    private String rolSeleccionado;

    public void setRolSeleccionado(String rol) {
        this.rolSeleccionado = rol;
        txtTitulo.setText("ACCESO - " + rol);
        txtSubtitulo.setText("Ingrese sus credenciales");

        if (rol.equals("MOZO")) {
            passwordContainer.setVisible(false);
            passwordContainer.setManaged(false);
            lblAyuda.setText("Los mozos solo necesitan su DNI");
            btnLogin.setText("INGRESAR COMO MOZO");
            txtDni.requestFocus();
        } else {
            passwordContainer.setVisible(true);
            passwordContainer.setManaged(true);
            lblAyuda.setText("Ingrese su DNI y clave");
            btnLogin.setText("INICIAR SESION");
            txtDni.requestFocus();
        }
    }

    @FXML
    public void handleLogin() {
        try {
            realizarLogin();
        } catch (org.springframework.dao.DataAccessException e) {
            mostrarError("No se pudo conectar con la base de datos. Intenta nuevamente.");
        } catch (IllegalArgumentException e) {
            mostrarError("Credenciales no válidas.");
        }
    }

    private void realizarLogin() {
        String dni = txtDni.getText().trim();

        if (dni.isEmpty()) {
            mostrarError("Ingrese su DNI");
            return;
        }

        if (!dni.matches("\\d{8}")) {
            mostrarError("El DNI debe tener 8 digitos numericos");
            return;
        }

        usuarioService.buscarPorDni(dni).ifPresentOrElse(
                usuario -> {
                    if (!Boolean.TRUE.equals(usuario.getActivo())) {
                        mostrarError("Este trabajador está inactivo. Consulta al administrador.");
                        return;
                    }
                    if (!usuario.getRol().equals(rolSeleccionado)) {
                        mostrarError("Este DNI no corresponde al rol seleccionado");
                        return;
                    }

                    if (rolSeleccionado.equals("MOZO")) {
                        System.out.println("[AUTH] Mozo autenticado: " + usuario.getDni());
                        abrirPanel(usuario);
                        return;
                    }

                    String password = txtPassword.getText();
                    if (password.isEmpty()) {
                        mostrarError("Ingrese su clave");
                        return;
                    }

                    if (usuarioService.autenticar(dni, password).isPresent()) {
                        System.out.println("[AUTH] Usuario autenticado: " + usuario.getRol());
                        abrirPanel(usuario);
                    } else {
                        mostrarError("Clave incorrecta");
                    }
                },
                () -> mostrarError("DNI no registrado")
        );
    }

    private void mostrarError(String mensaje) {
        lblError.setStyle("-fx-text-fill: #c62828;");
        lblError.setText(mensaje);
    }

    private void abrirPanel(Usuario usuario) {
        try {
            String fxmlPath;
            String titulo;
            switch (usuario.getRol()) {
                case "MOZO":
                    fxmlPath = "/fxml/MozoView.fxml";
                    titulo = "MOZO";
                    break;
                case "CAJERO":
                    fxmlPath = "/fxml/CajaView.fxml";
                    titulo = "CAJA";
                    break;
                case "ADMIN":
                    fxmlPath = "/fxml/AdminView.fxml";
                    titulo = "ADMINISTRADOR";
                    break;
                default:
                    fxmlPath = "/fxml/PanelPrincipalView.fxml";
                    titulo = "PRINCIPAL";
                    break;
            }

            System.out.println("[DEBUG] Cargando FXML: " + fxmlPath);

            java.net.URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                System.err.println("[ERROR] No se encontró el FXML: " + fxmlPath);
                mostrarError("No se encontró la vista: " + fxmlPath);
                return;
            }

            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(ApplicationContextProvider.getApplicationContext()::getBean);
            Parent root = loader.load();

            if (usuario.getRol().equals("MOZO")) {
                MozoController controller = loader.getController();
                controller.setUsuario(usuario);
            } else if (usuario.getRol().equals("CAJERO")) {
                CajaController controller = loader.getController();
                controller.setUsuario(usuario);
            } else if (usuario.getRol().equals("ADMIN")) {
                AdminController controller = loader.getController();
                controller.setUsuario(usuario);
            }

            Stage stage = new Stage();
            stage.setTitle("Restaurante LA FONDA - " + titulo);
            Scene scene = new Scene(root, 1024, 768);
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            stage.show();

            Stage loginStage = (Stage) btnLogin.getScene().getWindow();
            loginStage.close();

        } catch (Exception e) {
            e.printStackTrace();
            mostrarError("Error al abrir el panel: " + e.getMessage());
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
            ((Stage) btnLogin.getScene().getWindow()).close();
        } catch (Exception e) {
            mostrarError("No se pudo regresar al menú principal.");
        }
    }

}
