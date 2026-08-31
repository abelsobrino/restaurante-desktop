package pe.edu.utp.restaurante;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class RestauranteApplication extends Application {

    private static ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        springContext = SpringApplication.run(RestauranteApplication.class, args);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Restaurante LaFonda");
        primaryStage.setResizable(false);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PanelPrincipalView.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();

        Scene scene = new Scene(root, 450, 450);
        primaryStage.setScene(scene);
        primaryStage.show();

        System.out.println("[SISTEMA] Restaurante LaFonda iniciado");
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
    }
}