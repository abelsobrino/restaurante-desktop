package pe.edu.utp.restaurante.service;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.IntStream;

public final class ConfiguradorReportes {
    private ConfiguradorReportes() {}
    public record Seleccion(String titulo, List<String> columnas, List<List<String>> filas) {}

    public static Optional<Seleccion> configurar(Window owner, String titulo, List<String> columnas, List<List<String>> filas) {
        if (columnas == null || columnas.isEmpty()) return Optional.empty();
        List<List<String>> originales = filas == null ? List.of() : filas;
        Dialog<Seleccion> dialogo = new Dialog<>();
        if (owner != null) dialogo.initOwner(owner);
        dialogo.setTitle("Personalizar reporte - La Fonda");
        dialogo.setHeaderText("Elige qué información incluir en tu " + titulo.replace('_', ' '));
        ButtonType generar = new ButtonType("Continuar con descarga", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(generar, ButtonType.CANCEL);
        dialogo.getDialogPane().setPrefWidth(650);
        dialogo.getDialogPane().setStyle("-fx-background-color:#f8fafc;");

        TextField nombre = new TextField(titulo.replace('_', ' '));
        nombre.setPromptText("Título que aparecerá en el PDF / Excel");
        ComboBox<String> campo = new ComboBox<>(FXCollections.observableArrayList(columnas));
        campo.getItems().add(0, "Todas las columnas");
        campo.getSelectionModel().selectFirst();
        TextField texto = new TextField();
        texto.setPromptText("Escribe un nombre o palabra para buscar...");
        ComboBox<String> valor = new ComboBox<>();
        valor.setMaxWidth(Double.MAX_VALUE);
        valor.getItems().add("Todos los valores");
        valor.getSelectionModel().selectFirst();
        campo.valueProperty().addListener((obs, anterior, nuevo) -> {
            valor.getItems().setAll("Todos los valores");
            int idx = columnas.indexOf(nuevo);
            if (idx >= 0) {
                originales.stream().filter(f -> idx < f.size()).map(f -> f.get(idx))
                        .filter(Objects::nonNull).distinct().sorted(String.CASE_INSENSITIVE_ORDER).limit(200)
                        .forEach(valor.getItems()::add);
            }
            valor.getSelectionModel().selectFirst();
        });
        VBox checks = new VBox(4);
        List<CheckBox> seleccion = new ArrayList<>();
        for (String c : columnas) {
            CheckBox check = new CheckBox(c);
            check.setSelected(true);
            seleccion.add(check);
            checks.getChildren().add(check);
        }
        ScrollPane desplazamiento = new ScrollPane(checks);
        desplazamiento.setFitToWidth(true);
        desplazamiento.setPrefViewportHeight(105);
        desplazamiento.setStyle("-fx-background-color:transparent;");
        Label contador = new Label();
        contador.setStyle("-fx-font-weight:bold;-fx-text-fill:#24465a;");
        Runnable contar = () -> contador.setText("Coincidencias: " + filtrar(originales, columnas, campo.getValue(), texto.getText(), valor.getValue()).size() + " de " + originales.size());
        campo.valueProperty().addListener((o,a,b) -> contar.run());
        texto.textProperty().addListener((o,a,b) -> contar.run());
        valor.valueProperty().addListener((o,a,b) -> contar.run());
        contar.run();

        Label etiquetaBuscar = etiqueta("Buscar contenido");
        Label etiquetaValores = etiqueta("Seleccionar valor");
        VBox raiz = new VBox(9,
                etiqueta("Título del documento"), nombre,
                etiqueta("Filtrar por columna"), campo,
                etiquetaBuscar, texto,
                etiquetaValores, valor,
                etiqueta("Columnas que aparecerán en el archivo"), desplazamiento, contador);
        Runnable ajustarFiltros = () -> {
            String columna = campo.getValue();
            int idx = columnas.indexOf(columna);
            long distintos = idx < 0 ? 0 : originales.stream()
                    .filter(f -> idx < f.size())
                    .map(f -> f.get(idx))
                    .filter(Objects::nonNull)
                    .distinct().limit(21).count();
            String clave = normal(columna);
            boolean categoria = idx >= 0 && (clave.contains("alerta") || clave.contains("estado")
                    || clave.contains("tipo") || clave.contains("metodo") || clave.contains("consumo")
                    || (distintos > 0 && distintos <= 8));
            etiquetaBuscar.setVisible(!categoria);
            etiquetaBuscar.setManaged(!categoria);
            texto.setVisible(!categoria);
            texto.setManaged(!categoria);
            if (categoria) texto.clear();
            etiquetaValores.setVisible(idx >= 0);
            etiquetaValores.setManaged(idx >= 0);
            valor.setVisible(idx >= 0);
            valor.setManaged(idx >= 0);
            if (idx < 0) valor.getSelectionModel().selectFirst();
            etiquetaValores.setText(categoria ? "Selecciona una opción" : "Mostrar solamente");
        };
        campo.valueProperty().addListener((obs, anterior, nuevo) -> ajustarFiltros.run());
        ajustarFiltros.run();
        raiz.setPadding(new Insets(18));
        dialogo.getDialogPane().setContent(raiz);
        Node boton = dialogo.getDialogPane().lookupButton(generar);
        boton.disableProperty().bind(nombre.textProperty().isEmpty());
        dialogo.setResultConverter(b -> {
            if (b != generar) return null;
            List<Integer> indices = IntStream.range(0, seleccion.size()).filter(i -> seleccion.get(i).isSelected()).boxed().toList();
            if (indices.isEmpty()) return null;
            List<String> cols = indices.stream().map(columnas::get).toList();
            List<List<String>> datos = filtrar(originales, columnas, campo.getValue(), texto.getText(), valor.getValue())
                    .stream().map(f -> indices.stream().map(i -> i < f.size() ? f.get(i) : "").toList()).toList();
            return new Seleccion(nombre.getText().trim(), cols, datos);
        });
        return dialogo.showAndWait();
    }

    private static Label etiqueta(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");
        return l;
    }
    private static String normal(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
    static List<List<String>> filtrar(List<List<String>> filas, List<String> columnas, String campo, String buscar, String exacto) {
        int idx = columnas.indexOf(campo);
        String q = normal(buscar).trim();
        return filas.stream().filter(f -> {
            List<String> valores = idx < 0 ? f : idx < f.size() ? List.of(f.get(idx) == null ? "" : f.get(idx)) : List.of("");
            boolean coincide = q.isBlank() || valores.stream().anyMatch(v -> normal(v).contains(q));
            return coincide && (exacto == null || exacto.equals("Todos los valores") ||
                    (idx >= 0 && idx < f.size() && exacto.equals(f.get(idx))));
        }).toList();
    }
}
