package pe.edu.utp.restaurante.util;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.stage.Window;
import javafx.util.Duration;

public final class RefrescoVisible {
    private RefrescoVisible() {}
    public static void cada30Segundos(Node nodo, Runnable accion) {
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(30), e -> accion.run()));
        timer.setCycleCount(Timeline.INDEFINITE);
        java.util.function.Consumer<Window> conectar = ventana -> {
            if (ventana == null) return;
            ventana.showingProperty().addListener((o, antes, visible) -> {
                if (visible) timer.play(); else timer.stop();
            });
            if (ventana.isShowing()) timer.play();
        };
        nodo.sceneProperty().addListener((o, antes, scene) -> {
            timer.stop();
            if (scene != null) {
                conectar.accept(scene.getWindow());
                scene.windowProperty().addListener((v, antigua, nueva) -> conectar.accept(nueva));
            }
        });
    }
}
