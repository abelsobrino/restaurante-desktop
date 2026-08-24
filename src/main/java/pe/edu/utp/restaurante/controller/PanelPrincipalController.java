package pe.edu.utp.restaurante.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.config.SpringContextHolder;

import java.io.IOException;

@Component
public class PanelPrincipalController {

    @FXML
    private Button btnMozo;

    @FXML
    private Button btnCajero;

    @FXML
    private Button btnAdmin;

    @FXML
    public void initialize() {
        System.out.println("[PANEL] Pantalla principal cargada");
    }

    @FXML
    private void seleccionarMozo() {
        System.out.println("[ACCESO] Seleccionado MOZO");
        abrirLogin("MOZO");
    }

    @FXML
    private void seleccionarCajero() {
        System.out.println("[ACCESO] Seleccionado CAJA");
        abrirLogin("CAJERO");
    }

    @FXML
    private void seleccionarAdmin() {
        System.out.println("[ACCESO] Seleccionado ADMINISTRADOR");
        abrirLogin("ADMIN");
    }

    private void abrirLogin(String rol) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            loader.setControllerFactory(SpringContextHolder.getContext()::getBean);
            Parent root = loader.load();

            LoginController controller = loader.getController();
            controller.setRolSeleccionado(rol);

            Stage loginStage = new Stage();
            loginStage.setTitle("Acceso - " + rol);
            loginStage.setScene(new Scene(root, 380, 350));
            loginStage.setResizable(false);
            loginStage.show();

            Stage stage = (Stage) btnMozo.getScene().getWindow();
            stage.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}