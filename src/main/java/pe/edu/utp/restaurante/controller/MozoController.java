package pe.edu.utp.restaurante.controller;

import javafx.fxml.FXML;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.model.Usuario;

@Component
public class MozoController {
    @FXML private Text txtUsuario;
    private Usuario usuarioActual;

    public void setUsuario(Usuario usuario) {
        this.usuarioActual = usuario;
        txtUsuario.setText("Usuario: " + usuario.getNombre() + " " + usuario.getApellido());
    }

    @FXML private void cerrarSesion() {
        Stage stage = (Stage) txtUsuario.getScene().getWindow();
        stage.close();
    }
}