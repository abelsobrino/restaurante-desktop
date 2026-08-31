package pe.edu.utp.restaurante.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.config.ApplicationContextProvider;

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
    private Button btnCocina;

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

    @FXML
    private void seleccionarCocina() {
        System.out.println("[ACCESO] Seleccionado COCINA");
        abrirCocina();
    }

    private void abrirLogin(String rol) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            loader.setControllerFactory(ApplicationContextProvider.getApplicationContext()::getBean);
            Parent root = loader.load();

            LoginController controller = loader.getController();
            controller.setRolSeleccionado(rol);

            Stage loginStage = new Stage();
            loginStage.setTitle("Acceso - " + rol);

            Scene scene = new Scene(root, 400, 380);
            loginStage.setScene(scene);
            loginStage.setMinWidth(350);
            loginStage.setMinHeight(350);
            loginStage.show();

            Stage stage = (Stage) btnMozo.getScene().getWindow();
            stage.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void abrirCocina() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CocinaView.fxml"));
            loader.setControllerFactory(ApplicationContextProvider.getApplicationContext()::getBean);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Restaurante LA FONDA - COCINA");

            Scene scene = new Scene(root, 1024, 768);
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            stage.show();

            Stage mainStage = (Stage) btnMozo.getScene().getWindow();
            mainStage.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}