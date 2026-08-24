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
import pe.edu.utp.restaurante.config.SpringContextHolder;
import pe.edu.utp.restaurante.model.Usuario;
import pe.edu.utp.restaurante.service.UsuarioService;

import java.io.IOException;

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
        String dni = txtDni.getText().trim();

        if (dni.isEmpty()) {
            mostrarError("Ingrese su DNI");
            return;
        }

        if (!dni.matches("\\d{8}")) {
            mostrarError("El DNI debe tener 8 digitos numericos");
            return;
        }

        // Buscar usuario
        usuarioService.buscarPorDni(dni).ifPresentOrElse(
                usuario -> {
                    if (!usuario.getRol().equals(rolSeleccionado)) {
                        mostrarError("Este DNI no corresponde al rol seleccionado");
                        return;
                    }

                    if (rolSeleccionado.equals("MOZO")) {
                        System.out.println("[AUTH] Mozo autenticado: " + usuario.getDni());
                        abrirPanel(usuario);
                        return;
                    }

                    String password = txtPassword.getText().trim();
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
            String fxml;
            switch (usuario.getRol()) {
                case "MOZO":
                    fxml = "/fxml/MozoView.fxml";
                    break;
                case "CAJERO":
                    fxml = "/fxml/CajaView.fxml";
                    break;
                case "ADMIN":
                    fxml = "/fxml/AdminView.fxml";
                    break;
                default:
                    fxml = "/fxml/PanelPrincipalView.fxml";
                    break;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            loader.setControllerFactory(SpringContextHolder.getContext()::getBean);
            Parent root = loader.load();

            // Pasar usuario al controlador
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
            stage.setTitle("Restaurante UTP - " + usuario.getRol());
            stage.setScene(new Scene(root, 700, 550));
            stage.setResizable(false);
            stage.show();

            Stage loginStage = (Stage) btnLogin.getScene().getWindow();
            loginStage.close();

        } catch (IOException e) {
            e.printStackTrace();
            mostrarError("Error al abrir el panel: " + e.getMessage());
        }
    }
}