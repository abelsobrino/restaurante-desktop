package pe.edu.utp.restaurante.controller;

import java.time.*;
import java.awt.EventQueue;
import java.awt.print.PrinterJob;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import org.apache.pdfbox.printing.PDFPageable;
import pe.edu.utp.restaurante.service.AgendaReservasPdf;
import pe.edu.utp.restaurante.service.AgendaReservasPdf.Agenda;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.model.Mesa;
import pe.edu.utp.restaurante.repository.MesaRepository;
import pe.edu.utp.restaurante.service.ReservaService;
import pe.edu.utp.restaurante.service.ReservaService.Reserva;
import pe.edu.utp.restaurante.util.RefrescoVisible;

@Component @Scope("prototype")
public class ReservaPanel {
    private final ReservaService servicio;
    private final MesaRepository mesas;
    private final AgendaReservasPdf exportador;
    private Button imprimirHoy;
    private final TableView<Reserva> tabla=new TableView<>();
    private final DatePicker fecha=new DatePicker(ReservaService.ahora().toLocalDate());
    private final Label resumen=new Label(), detalle=new Label();
    private final ComboBox<Mesa> opciones=new ComboBox<>();
    private Tab tab; private Label encabezado; private Runnable cambioMesa;
    private boolean instalado, actualizando;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ReservaPanel.class);
    private static String estadoSql(Throwable e) {
        for(Throwable t=e;t!=null;t=t.getCause()) if(t instanceof java.sql.SQLException sql) return java.util.Objects.toString(sql.getSQLState(), "sin código");
        return "sin código";
    }
    public ReservaPanel(ReservaService servicio,MesaRepository mesas,AgendaReservasPdf exportador) { this.servicio=servicio; this.mesas=mesas; this.exportador=exportador; }
    public void instalar(Tab tab,Label encabezado,Runnable cambioMesa) {
        this.tab=tab; this.encabezado=encabezado; this.cambioMesa=cambioMesa;
        Label titulo=new Label("AGENDA DE RESERVAS"); titulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0d47a1;");
        resumen.setWrapText(true); detalle.setWrapText(true);
        columna("Hora",85,r->r.hora().format(DateTimeFormatter.ofPattern("HH:mm")));
        columna("Titular",160,Reserva::nombre); columna("Personas",82,r->String.valueOf(r.personas()));
        columna("Teléfono",120,Reserva::telefono); columna("Mesa",75,r->r.mesaNumero()==null?"Sin asignar":String.valueOf(r.mesaNumero()));
        columna("Estado",135,r->r.estado().replace('_',' ')); columna("Comentario",220,Reserva::descripcion);
        tabla.setPlaceholder(new Label("No hay reservas para la fecha."));
        VBox.setVgrow(tabla,Priority.ALWAYS);
        opciones.setPromptText("Seleccionar mesa"); opciones.setPrefWidth(260);
        opciones.setConverter(new javafx.util.StringConverter<Mesa>() {
            public String toString(Mesa m){return m==null?"":"Mesa "+m.getNumero()+" · "+m.getCapacidad()+" personas · "+m.getEstado();}
            public Mesa fromString(String s){return null;}
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(!actualizando) actualizarOpciones();});
        fecha.setOnAction(e->actualizar());
        FlowPane filtros=new FlowPane(10,8,new Label("Fecha (Lima):"),fecha,boton("HOY",()->{fecha.setValue(ReservaService.ahora().toLocalDate()); actualizar();}),boton("ACTUALIZAR",this::actualizar));
        FlowPane acciones=new FlowPane(10,8,opciones,boton("ASIGNAR / CAMBIAR MESA",()->operar("ASIGNAR")),boton("REGISTRAR LLEGADA",()->operar("LLEGADA")),boton("FINALIZAR",()->operar("FINALIZADA")),boton("NO ASISTIÓ",()->operar("NO_ASISTIO")),boton("CANCELAR",()->operar("CANCELADA")));
        Label nota=new Label("Bloqueo: 30 min antes. Duración prevista: 90 min. Si no hay mesas compatibles, crea una mesa física en MESAS y actualiza. No se desplaza una cuenta abierta."); nota.setWrapText(true);
        imprimirHoy=boton("IMPRIMIR RESERVAS DE HOY",this::imprimirAgenda);
        FlowPane documentos=new FlowPane(10,8,boton("GUARDAR PDF DE HOY",this::guardarAgenda),imprimirHoy);
        Label notaImpresion=new Label("Recepción: guarda o imprime las reservas del día actual (hora de Lima), aunque estés consultando otra fecha. No incluye teléfonos.");
        notaImpresion.setWrapText(true);
        VBox caja=new VBox(12,titulo,filtros,resumen,documentos,notaImpresion,tabla,detalle,acciones,nota); caja.setPadding(new Insets(16));
        tab.setContent(caja); tab.selectedProperty().addListener((o,a,b)->{if(b)actualizar();});
        RefrescoVisible.cada30Segundos(encabezado,()->{actualizar(); if(tab.isSelected()) cambioMesa.run();});
        actualizar();
    }
    private Button boton(String texto,Runnable accion) { Button b=new Button(texto); b.setStyle("-fx-background-color: #1565c0; -fx-text-fill: white; -fx-font-weight: bold;"); b.setOnAction(e->accion.run()); return b; }
    private void columna(String titulo,int ancho,Function<Reserva,String> valor){ TableColumn<Reserva,String> c=new TableColumn<>(titulo); c.setPrefWidth(ancho); c.setCellValueFactory(v->new SimpleStringProperty(valor.apply(v.getValue()))); tabla.getColumns().add(c); }
    public void actualizar() {
        try {
            instalado=servicio.instalado();
            if(!instalado){ encabezado.setText("Reservas: sin configurar"); resumen.setText("Ejecuta la migración 01_reservas.sql para activar la agenda."); tabla.getItems().clear(); opciones.getItems().clear(); tab.setText("RESERVAS"); return; }
            LocalDate hoy=ReservaService.ahora().toLocalDate(); var delDia=servicio.delDia(hoy);
            long activas=delDia.stream().filter(Reserva::activa).count();
            long pendientes=delDia.stream().filter(r->r.estado().equals("PENDIENTE")).count();
            long urgentes=delDia.stream().filter(r->r.estado().equals("ASIGNADA")&&!ReservaService.ahora().isBefore(r.inicio().minusMinutes(30))).count();
            encabezado.setText("Reservas hoy: "+activas+" · sin mesa: "+pendientes);
            tab.setText("RESERVAS ("+activas+")"); resumen.setText("Hoy: "+activas+" activas · "+pendientes+" sin asignar · "+urgentes+" próximas o esperando llegada. Actualización cada 30 s.");
            Reserva sel=tabla.getSelectionModel().getSelectedItem(); Long mesa=opciones.getValue()==null?null:opciones.getValue().getId();
            actualizando=true;
            LocalDate seleccion=fecha.getValue()==null?hoy:fecha.getValue();
            var lista=hoy.equals(seleccion)?delDia:servicio.delDia(seleccion);
            tabla.setItems(FXCollections.observableArrayList(lista));
            resumen.setText(resumen.getText()+" Fecha consultada: "+seleccion+" · "+lista.size()+" registros.");
            tabla.setPlaceholder(new Label("No hay reservas registradas para "+seleccion+"."));
            if(sel!=null) tabla.getItems().stream().filter(r->r.id()==sel.id()).findFirst().ifPresent(r->tabla.getSelectionModel().select(r));
            actualizando=false; actualizarOpciones(); if(mesa!=null) opciones.getItems().stream().filter(m->mesa.equals(m.getId())).findFirst().ifPresent(opciones::setValue);
        } catch(Exception e) { instalado=false; String codigo=estadoSql(e); LOG.warn("No se pudo consultar reservas. SQLSTATE: {}",codigo); encabezado.setText("Reservas: no disponibles"); tab.setText("RESERVAS"); resumen.setText("No se pudo consultar Supabase (SQLSTATE: "+codigo+"). Verifica conexión, permisos y la migración."); tabla.getItems().clear(); opciones.getItems().clear(); detalle.setText("Agenda no disponible."); tabla.setPlaceholder(new Label("No se pudo consultar la agenda.")); }
        finally { actualizando=false; }
    }
    // Consultar nuevamente: nunca exportar una tabla vacía por desconexión ni una fecha histórica seleccionada.
    private Agenda agendaHoy(){
        if(!servicio.instalado()) throw new IllegalStateException("El módulo de reservas todavía no está configurado.");
        LocalDateTime momento=ReservaService.ahora();
        return new Agenda(momento,servicio.delDia(momento.toLocalDate()));
    }
    private void guardarAgenda(){
        Path temporal=null;
        try {
            Agenda agenda=agendaHoy();
            FileChooser selector=new FileChooser(); selector.setTitle("Guardar reservas del día");
            selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)","*.pdf"));
            selector.setInitialFileName(agenda.archivo());
            var archivo=selector.showSaveDialog(encabezado.getScene().getWindow());
            if(archivo==null)return;
            Path destino=archivo.toPath().toAbsolutePath();
            if(!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) destino=destino.resolveSibling(destino.getFileName()+".pdf");
            if(Files.exists(destino)){
                Alert confirmar=new Alert(Alert.AlertType.CONFIRMATION,"¿Reemplazar el archivo existente?",ButtonType.OK,ButtonType.CANCEL);
                if(confirmar.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
            }
            temporal=Files.createTempFile(destino.getParent(),"reservas-",".tmp");
            try(OutputStream out=Files.newOutputStream(temporal)){exportador.guardar(agenda,out);}
            Files.move(temporal,destino,StandardCopyOption.REPLACE_EXISTING);
            aviso("Agenda guardada correctamente:\n"+destino,Alert.AlertType.INFORMATION);
        } catch(Exception e){aviso("No se pudo guardar la agenda. Verifica la conexión, la migración y los permisos de la carpeta.",Alert.AlertType.WARNING);}
        finally{if(temporal!=null)try{Files.deleteIfExists(temporal);}catch(java.io.IOException ignored){}}
    }
    private void imprimirAgenda(){
        final Agenda agenda;
        try{agenda=agendaHoy();}catch(Exception e){aviso("No se pudo consultar la agenda de hoy. Verifica Supabase.",Alert.AlertType.WARNING);return;}
        imprimirHoy.setDisable(true);
        // El diálogo nativo de impresión y el envío al spooler no bloquean la interfaz JavaFX.
        EventQueue.invokeLater(()->{
            try(var doc=exportador.generar(agenda)){
                PrinterJob trabajo=PrinterJob.getPrinterJob();
                trabajo.setJobName("La Fonda - "+agenda.archivo());
                trabajo.setPageable(new PDFPageable(doc));
                if(trabajo.printDialog()){
                    trabajo.print();
                    Platform.runLater(()->aviso("Agenda enviada a la impresora.",Alert.AlertType.INFORMATION));
                }
            }catch(Exception e){Platform.runLater(()->aviso("No se pudo imprimir. Verifica que haya una impresora disponible o guarda el PDF.",Alert.AlertType.WARNING));}
            finally{Platform.runLater(()->imprimirHoy.setDisable(false));}
        });
    }
    private void actualizarOpciones(){
        Reserva r=tabla.getSelectionModel().getSelectedItem(); opciones.getItems().clear();
        if(r==null){detalle.setText("Selecciona una reserva para ver su detalle."); return;}
        detalle.setText(r.nombre()+" · "+r.fecha()+" "+r.hora()+" · "+r.personas()+" personas · "+r.telefono()+" · "+r.descripcion());
        try { opciones.setItems(FXCollections.observableArrayList(servicio.candidatas(r)));
            opciones.getItems().stream().filter(m->Objects.equals(m.getId(),r.mesaId())).findFirst().ifPresent(opciones::setValue);
            if(opciones.getItems().isEmpty() && Set.of("PENDIENTE","ASIGNADA").contains(r.estado())) detalle.setText(detalle.getText()+" · No hay mesas compatibles; revisa MESAS.");
        } catch(Exception e){detalle.setText("No se pudieron consultar las mesas.");}
    }
    private void operar(String accion){
        Reserva r=tabla.getSelectionModel().getSelectedItem();
        if(!instalado || r==null){aviso("Selecciona una reserva y verifica la conexión.",Alert.AlertType.WARNING); return;}
        try {
            if(accion.equals("ASIGNAR")){ if(opciones.getValue()==null)throw new IllegalStateException("Selecciona una mesa con capacidad suficiente o agrégala en MESAS."); servicio.asignar(r.id(),opciones.getValue().getId()); }
            else if(accion.equals("LLEGADA")){if(r.mesaId()==null)throw new IllegalStateException("Asigna una mesa primero."); servicio.llegada(r.id(),r.mesaId());}
            else { Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Confirmar "+accion.replace('_',' ')+" para "+r.nombre()+"?",ButtonType.OK,ButtonType.CANCEL); if(a.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return; servicio.cerrar(r.id(),accion); }
            actualizar(); cambioMesa.run();
        } catch(Exception e){aviso(e instanceof IllegalStateException || e instanceof IllegalArgumentException?e.getMessage():"No se pudo completar la operación. Actualiza e intenta nuevamente.",Alert.AlertType.WARNING); actualizar();}
    }
    public void avisoInicio(){ try { if(instalado){ var hoy=servicio.delDia(ReservaService.ahora().toLocalDate()).stream().filter(Reserva::activa).toList(); if(!hoy.isEmpty()) aviso("Hay "+hoy.size()+" reservas activas para hoy. Revisa la pestaña RESERVAS.",Alert.AlertType.INFORMATION); } } catch(Exception e){ encabezado.setText("Reservas: no disponibles"); } }
    private void aviso(String mensaje,Alert.AlertType tipo){ Alert a=new Alert(tipo,mensaje); a.setTitle("Reservas · La Fonda"); a.setHeaderText(null); a.showAndWait(); }
}
