package pe.edu.utp.restaurante.controller;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.service.ExportadorReportesService;
import pe.edu.utp.restaurante.service.ReporteAvanzadoService;
import pe.edu.utp.restaurante.service.ReporteAvanzadoService.*;

@Component
@Scope("prototype")
public class ReportesAvanzadosController {
    private Long usuarioId;
    @Autowired private ReporteAvanzadoService reporteService;
    @Autowired private ExportadorReportesService exportador;

    @FXML private DatePicker dpDesde, dpHasta, dpCompDesde, dpCompHasta;
    @FXML private ComboBox<String> cmbMesReporte;
    @FXML private Spinner<Integer> spnAnioReporte;
    @FXML private CheckBox chkComparar;
    @FXML private Label lblVentas,lblSubtotal,lblIgv,lblDescuentos,lblPedidos,lblTicket,lblCompras,lblGastos,lblResultado,lblSaldoCaja,lblComparacion;
    @FXML private TableView<VentaDia> tblDias;
    @FXML private TableView<MetodoPago> tblMetodos;
    @FXML private TableView<PlatoVendido> tblPlatos;
    @FXML private TableView<MovimientoCaja> tblCaja;
    @FXML private TextField txtCategoriaGasto, txtDescripcionGasto, txtMontoGasto;
    private Reporte actual;

    @FXML public void initialize() {
        configurarTablas();
        cmbMesReporte.setItems(FXCollections.observableArrayList("Enero","Febrero","Marzo","Abril","Mayo","Junio","Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"));
        cmbMesReporte.getSelectionModel().select(LocalDate.now().getMonthValue()-1);
        spnAnioReporte.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2020, 2100, LocalDate.now().getYear()));
        mesActual();
        chkComparar.selectedProperty().addListener((o,a,b) -> {
            dpCompDesde.setDisable(!b); dpCompHasta.setDisable(!b);
            if (b && dpCompDesde.getValue() == null && dpDesde.getValue() != null && dpHasta.getValue() != null) {
                long dias = ChronoUnit.DAYS.between(dpDesde.getValue(), dpHasta.getValue()) + 1;
                LocalDate hastaAnterior = dpDesde.getValue().minusDays(1);
                dpCompHasta.setValue(hastaAnterior);
                dpCompDesde.setValue(hastaAnterior.minusDays(dias - 1));
            }
        });
        dpCompDesde.setDisable(true); dpCompHasta.setDisable(true);
    }

    private void configurarTablas() {
        TableColumn<VentaDia,String> fecha = new TableColumn<>("Fecha");
        fecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fecha().toString()));
        TableColumn<VentaDia,Number> pedidos = new TableColumn<>("Pedidos");
        pedidos.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().pedidos()));
        TableColumn<VentaDia,BigDecimal> total = new TableColumn<>("Total");
        total.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().total()));
        tblDias.getColumns().setAll(fecha,pedidos,total);

        TableColumn<MetodoPago,String> metodo = new TableColumn<>("Método");
        metodo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().metodo()));
        TableColumn<MetodoPago,Number> operaciones = new TableColumn<>("Operaciones");
        operaciones.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().operaciones()));
        TableColumn<MetodoPago,BigDecimal> monto = new TableColumn<>("Total");
        monto.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().total()));
        tblMetodos.getColumns().setAll(metodo,operaciones,monto);

        TableColumn<PlatoVendido,String> plato = new TableColumn<>("Plato");
        plato.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plato())); plato.setPrefWidth(220);
        TableColumn<PlatoVendido,Number> unidades = new TableColumn<>("Unidades");
        unidades.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().unidades()));
        TableColumn<PlatoVendido,BigDecimal> importe = new TableColumn<>("Importe");
        importe.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().importe()));
        tblPlatos.getColumns().setAll(plato,unidades,importe);

        TableColumn<MovimientoCaja,String> mf = new TableColumn<>("Fecha");
        mf.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fecha().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
        mf.setPrefWidth(145);
        TableColumn<MovimientoCaja,String> mt = new TableColumn<>("Tipo");
        mt.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tipo()));
        TableColumn<MovimientoCaja,String> md = new TableColumn<>("Descripción");
        md.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().descripcion())); md.setPrefWidth(430);
        TableColumn<MovimientoCaja,BigDecimal> mm = new TableColumn<>("Monto");
        mm.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().monto()));
        tblCaja.getColumns().setAll(mf,mt,md,mm);
    }

    @FXML private void hoy() {
        dpDesde.setValue(LocalDate.now()); dpHasta.setValue(LocalDate.now()); generar();
    }
    @FXML private void mesActual() {
        LocalDate h = LocalDate.now(); dpDesde.setValue(h.withDayOfMonth(1)); dpHasta.setValue(h); generar();
    }
    @FXML private void mesAnterior() {
        LocalDate m = LocalDate.now().minusMonths(1); dpDesde.setValue(m.withDayOfMonth(1)); dpHasta.setValue(m.withDayOfMonth(m.lengthOfMonth())); generar();
    }



    @FXML private void aplicarMesSeleccionado() {
        int mes = cmbMesReporte.getSelectionModel().getSelectedIndex() + 1;
        if (mes <= 0) { aviso("Selecciona un mes."); return; }
        LocalDate inicio = LocalDate.of(spnAnioReporte.getValue(), mes, 1);
        dpDesde.setValue(inicio); dpHasta.setValue(inicio.withDayOfMonth(inicio.lengthOfMonth())); generar();
    }
    @FXML private void aplicarAnioSeleccionado() {
        LocalDate inicio = LocalDate.of(spnAnioReporte.getValue(), 1, 1);
        dpDesde.setValue(inicio); dpHasta.setValue(inicio.withMonth(12).withDayOfMonth(31)); generar();
    }

    @FXML private void anioActual() {
        LocalDate h=LocalDate.now(); dpDesde.setValue(h.withDayOfYear(1)); dpHasta.setValue(h); generar();
    }
    @FXML private void anioAnterior() {
        LocalDate a=LocalDate.now().minusYears(1); dpDesde.setValue(a.withDayOfYear(1)); dpHasta.setValue(a.withMonth(12).withDayOfMonth(31)); generar();
    }

    @FXML private void generar() {
        try {
            actual = reporteService.generar(dpDesde.getValue(), dpHasta.getValue());
            mostrar(actual);
            if (chkComparar.isSelected()) {
                Reporte anterior = reporteService.generar(dpCompDesde.getValue(), dpCompHasta.getValue());
                BigDecimal v = reporteService.variacionPorcentual(actual, anterior);
                lblComparacion.setText(v == null ? "Comparación: sin base (periodo anterior = S/ 0.00)" :
                        "Variación de ventas: " + (v.signum() > 0 ? "+" : "") + v + "%");
            } else lblComparacion.setText("");
        } catch (Exception e) { error(e); }
    }

    private void mostrar(Reporte r) {
        Resumen s = r.resumen();
        lblVentas.setText("S/ " + money(s.ventas())); lblSubtotal.setText("S/ " + money(s.subtotal()));
        lblIgv.setText("S/ " + money(s.igv())); lblDescuentos.setText("S/ " + money(s.descuentos()));
        lblPedidos.setText(String.valueOf(s.pedidos())); lblTicket.setText("S/ " + money(s.ticketPromedio()));
        lblCompras.setText("S/ " + money(s.compras())); lblGastos.setText("S/ " + money(s.gastos()));
        lblResultado.setText("S/ " + money(s.resultadoEstimado()));
        lblSaldoCaja.setText("S/ " + money(s.saldoCajaAcumulado()));
        tblDias.setItems(FXCollections.observableArrayList(r.porDia()));
        tblMetodos.setItems(FXCollections.observableArrayList(r.metodos()));
        tblPlatos.setItems(FXCollections.observableArrayList(r.topPlatos()));
        tblCaja.setItems(FXCollections.observableArrayList(r.movimientosCaja()));
    }

    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    @FXML private void registrarGasto() {
        try {
            reporteService.registrarGasto(txtCategoriaGasto.getText(), txtDescripcionGasto.getText(), decimal(txtMontoGasto.getText()), usuarioId);
            txtCategoriaGasto.clear(); txtDescripcionGasto.clear(); txtMontoGasto.clear(); generar(); aviso("Gasto registrado.");
        } catch (Exception e) { error(e); }
    }

    @FXML private void exportarPdf() { exportar(true); }
    @FXML private void exportarExcel() { exportar(false); }
    private void exportar(boolean pdf) {
        if (actual == null) { aviso("Primero genera un reporte."); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle(pdf ? "Guardar reporte PDF" : "Guardar reporte Excel");
        String ext = pdf ? "*.pdf" : "*.xlsx";
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(pdf ? "PDF" : "Excel", ext));
        fc.setInitialFileName("LaFonda_Reporte_" + actual.desde() + "_" + actual.hasta() + (pdf ? ".pdf" : ".xlsx"));
        File f = fc.showSaveDialog(tblDias.getScene().getWindow());
        if (f == null) return;
        try {
            if (pdf) {
                exportador.exportarPdf(actual, f.toPath());
                abrirArchivo(f);
            } else {
                exportador.exportarXlsx(actual, f.toPath());
            }
            aviso("Reporte guardado correctamente en:\n" + f.getAbsolutePath());
        } catch (Exception e) { error(e); }
    }


    private void abrirArchivo(File archivo) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(archivo);
                return;
            }
        } catch (Exception ignored) { }
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win"))
                new ProcessBuilder("cmd", "/c", "start", "", archivo.getAbsolutePath()).start();
        } catch (Exception ignored) { }
    }

    @FXML private void cerrar() { ((Stage) tblDias.getScene().getWindow()).close(); }
    private static String money(BigDecimal v) { return (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
    private static BigDecimal decimal(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Ingresa el monto del gasto.");
        try { return new BigDecimal(s.trim().replace(',','.')); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Monto inválido."); }
    }
    private void aviso(String s) { Alert a=new Alert(Alert.AlertType.INFORMATION,s,ButtonType.OK);a.setHeaderText(null);a.showAndWait(); }
    private void error(Exception e) { aviso(e instanceof IllegalArgumentException || e instanceof IllegalStateException ? e.getMessage() : "No se pudo completar la operación: " + e.getMessage()); }
}
