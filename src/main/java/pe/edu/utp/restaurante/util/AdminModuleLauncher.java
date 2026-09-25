package pe.edu.utp.restaurante.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import pe.edu.utp.restaurante.config.ApplicationContextProvider;

public final class AdminModuleLauncher {
    private AdminModuleLauncher() {}

    public static Object abrir(String fxml, String titulo, double ancho, double alto) {
        try {
            FXMLLoader loader = new FXMLLoader(AdminModuleLauncher.class.getResource(fxml));
            loader.setControllerFactory(ApplicationContextProvider.getApplicationContext()::getBean);
            Stage stage = new Stage();
            stage.initModality(Modality.NONE);
            stage.setTitle(titulo);
            stage.setScene(new Scene(loader.load(), ancho, alto));
            stage.setMinWidth(Math.min(ancho, 900));
            stage.setMinHeight(Math.min(alto, 600));
            stage.show();
            return loader.getController();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo abrir " + titulo + ".", e);
        }
    }
}
