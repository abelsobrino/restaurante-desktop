package pe.edu.utp.restaurante.controller;

import java.io.File;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import pe.edu.utp.restaurante.service.ExportadorTablasService;
import pe.edu.utp.restaurante.service.InventarioService;
import pe.edu.utp.restaurante.service.InventarioService.IngredienteFila;
import pe.edu.utp.restaurante.service.ProveedorService;
import pe.edu.utp.restaurante.service.ProveedorService.*;

@Component
@Scope("prototype")
public class ProveedoresController {
    private Long usuarioId;
    @Autowired private ProveedorService proveedorService;
    @Autowired private InventarioService inventarioService;
    @Autowired private ExportadorTablasService exportador;

    @FXML private TableView<ProveedorFila> tblProveedores;
    @FXML private TableView<CompraItem> tblCompra;
    @FXML private TableView<PrecioProveedor> tblPrecios;
    @FXML private TableView<CompraFila> tblHistorial;
    @FXML private TableView<CompraDetalleFila> tblHistorialDetalle;
    @FXML private TextField txtNombre, txtRuc, txtTelefono, txtEmail, txtDireccion;
    @FXML private ComboBox<ProveedorFila> cmbProveedorCompra, cmbProveedorPrecio;
    @FXML private ComboBox<IngredienteFila> cmbIngredienteCompra, cmbIngredientePrecio;
    @FXML private ComboBox<String> cmbUnidadCompra;
    @FXML private TextField txtCantidadCompra, txtCostoTotal, txtDocumento;
    @FXML private TextField txtPrecioReferencia, txtCantidadReferencia, txtUnidadCompra;
    @FXML private TextArea txtObservacion;
    @FXML private Label lblTotalCompra, lblModoProveedor, lblHistorialCompra;
    @FXML private Button btnGuardarProveedor, btnEstadoProveedor;
    private final ObservableList<CompraItem> borrador=FXCollections.observableArrayList();
    private ProveedorFila proveedorEditando;

    @FXML public void initialize(){
        cmbUnidadCompra.setItems(FXCollections.observableArrayList("KG","G","L","ML","UNIDAD"));
        cmbUnidadCompra.setValue("KG");
        configurarTablas(); tblCompra.setItems(borrador);
        cmbProveedorPrecio.valueProperty().addListener((o,a,b)->cargarPrecios());
        tblProveedores.getSelectionModel().selectedItemProperty().addListener((o,a,p)->cargarProveedor(p));
        tblHistorial.getSelectionModel().selectedItemProperty().addListener((o,a,c)->cargarDetalleCompra(c));
        actualizar();
    }

    private void configurarTablas(){
        tblProveedores.getColumns().setAll(
                col("Proveedor",220,ProveedorFila::nombre), col("RUC",110,p->nvl(p.ruc())),
                col("Teléfono",120,p->nvl(p.telefono())), col("Correo",190,p->nvl(p.email())),
                col("Dirección",220,p->nvl(p.direccion())), col("Estado",90,p->p.activo()?"ACTIVO":"INACTIVO"));
        tblCompra.getColumns().setAll(
                col("Ingrediente",210,CompraItem::ingrediente),
                col("Cantidad",150,i->i.cantidadCompra().stripTrailingZeros().toPlainString()+" "+i.unidadCompra()),
                col("Costo total",120,i->"S/ "+money(i.costoTotal())));
        tblPrecios.getColumns().setAll(
                col("Ingrediente",220,PrecioProveedor::ingrediente),
                col("Precio ref.",120,p->"S/ "+money(p.precioReferencia())),
                col("Cantidad ref.",120,p->p.cantidadReferencia().stripTrailingZeros().toPlainString()),
                col("Unidad compra",120,p->nvl(p.unidadCompra())));
        tblHistorial.getColumns().setAll(
                col("Fecha",140,c->c.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))),
                col("Proveedor",220,CompraFila::proveedor), col("Documento",140,c->nvl(c.documento())),
                col("Total",110,c->"S/ "+money(c.total())), col("Estado",100,CompraFila::estado));
        tblHistorialDetalle.getColumns().setAll(
                col("Producto / ingrediente",220,CompraDetalleFila::ingrediente),
                col("Compra",150,d->nvl(d.cantidadCompra()==null?null:d.cantidadCompra().stripTrailingZeros().toPlainString()+" "+nvl(d.unidadCompra()))),
                col("Ingreso a almacén",180,d->InventarioService.formatearCantidad(d.cantidadBase(),d.unidadBase())),
                col("Costo",110,d->"S/ "+money(d.costoTotal())));
    }

    private static <T> TableColumn<T,String> col(String titulo,double ancho,java.util.function.Function<T,String> f){
        TableColumn<T,String> c=new TableColumn<>(titulo); c.setCellValueFactory(v->new SimpleStringProperty(f.apply(v.getValue()))); c.setPrefWidth(ancho); return c;
    }

    @FXML private void guardarProveedor(){
        try{
            if(proveedorEditando==null){ proveedorService.crear(txtNombre.getText(),txtRuc.getText(),txtTelefono.getText(),txtEmail.getText(),txtDireccion.getText()); aviso("Proveedor registrado."); }
            else { proveedorService.actualizar(proveedorEditando.id(),txtNombre.getText(),txtRuc.getText(),txtTelefono.getText(),txtEmail.getText(),txtDireccion.getText()); aviso("Proveedor actualizado."); }
            limpiarProveedor(); actualizar();
        }catch(Exception e){error(e);}
    }
    @FXML private void nuevoProveedor(){limpiarProveedor();}
    @FXML private void cambiarEstadoProveedor(){
        if(proveedorEditando==null){aviso("Selecciona un proveedor.");return;}
        try{proveedorService.cambiarActivo(proveedorEditando.id(),!proveedorEditando.activo());limpiarProveedor();actualizar();}catch(Exception e){error(e);}
    }
    private void cargarProveedor(ProveedorFila p){
        proveedorEditando=p; if(p==null)return;
        txtNombre.setText(p.nombre());txtRuc.setText(nvl(p.ruc()));txtTelefono.setText(nvl(p.telefono()));txtEmail.setText(nvl(p.email()));txtDireccion.setText(nvl(p.direccion()));
        lblModoProveedor.setText("Editando: "+p.nombre());btnGuardarProveedor.setText("Guardar cambios");btnEstadoProveedor.setText(p.activo()?"Desactivar":"Reactivar");
    }
    private void limpiarProveedor(){
        proveedorEditando=null;tblProveedores.getSelectionModel().clearSelection();txtNombre.clear();txtRuc.clear();txtTelefono.clear();txtEmail.clear();txtDireccion.clear();
        lblModoProveedor.setText("Nuevo proveedor");btnGuardarProveedor.setText("Registrar proveedor");btnEstadoProveedor.setText("Desactivar / Reactivar");
    }

    @FXML private void agregarItem(){
        IngredienteFila i=cmbIngredienteCompra.getValue(); if(i==null){aviso("Selecciona un ingrediente.");return;}
        try{
            BigDecimal cantidad=decimal(txtCantidadCompra.getText()), costo=decimal(txtCostoTotal.getText()); String unidad=cmbUnidadCompra.getValue();
            if(cantidad.signum()<=0||costo.signum()<0)throw new IllegalArgumentException("Cantidad/costo inválidos.");
            BigDecimal base=convertirABase(cantidad,unidad,i.unidadBase());
            borrador.removeIf(x->x.ingredienteId().equals(i.id())); borrador.add(new CompraItem(i.id(),i.nombre(),cantidad,unidad,base,costo));
            txtCantidadCompra.clear();txtCostoTotal.clear();actualizarTotal();
        }catch(Exception e){error(e);}
    }
    @FXML private void quitarItem(){CompraItem i=tblCompra.getSelectionModel().getSelectedItem();if(i!=null){borrador.remove(i);actualizarTotal();}}

    @FXML private void guardarCompra(){
        ProveedorFila p=cmbProveedorCompra.getValue();
        try{
            long id=proveedorService.registrarCompra(p==null?null:p.id(),usuarioId,txtDocumento.getText(),txtObservacion.getText(),borrador);
            borrador.clear();txtDocumento.clear();txtObservacion.clear();actualizarTotal();actualizar();
            tblHistorial.getItems().stream().filter(c->c.id()==id).findFirst().ifPresent(c->tblHistorial.getSelectionModel().select(c));
            aviso("Compra #"+id+" finalizada. Los productos ya ingresaron al inventario y el monto se refleja como egreso en Reportes/Caja.");
        }catch(Exception e){error(e);}
    }

    @FXML private void guardarPrecio(){
        ProveedorFila p=cmbProveedorPrecio.getValue();IngredienteFila i=cmbIngredientePrecio.getValue();
        try{proveedorService.guardarPrecio(p==null?null:p.id(),i==null?null:i.id(),decimal(txtPrecioReferencia.getText()),decimal(txtCantidadReferencia.getText()),txtUnidadCompra.getText());txtPrecioReferencia.clear();txtCantidadReferencia.clear();txtUnidadCompra.clear();cargarPrecios();aviso("Precio de referencia guardado.");}catch(Exception e){error(e);}
    }

    public void setUsuarioId(Long usuarioId){this.usuarioId=usuarioId;}

    @FXML private void actualizar(){
        try{
            ProveedorFila pc=cmbProveedorCompra.getValue(),pp=cmbProveedorPrecio.getValue();
            var proveedores=proveedorService.listar();tblProveedores.setItems(FXCollections.observableArrayList(proveedores));
            var activos=proveedores.stream().filter(ProveedorFila::activo).toList();cmbProveedorCompra.setItems(FXCollections.observableArrayList(activos));cmbProveedorPrecio.setItems(FXCollections.observableArrayList(activos));
            restaurarProveedor(cmbProveedorCompra,pc);restaurarProveedor(cmbProveedorPrecio,pp);
            var ings=inventarioService.listarIngredientes().stream().filter(IngredienteFila::activo).toList();cmbIngredienteCompra.setItems(FXCollections.observableArrayList(ings));cmbIngredientePrecio.setItems(FXCollections.observableArrayList(ings));
            tblHistorial.setItems(FXCollections.observableArrayList(proveedorService.comprasRecientes(300)));tblHistorialDetalle.setItems(FXCollections.observableArrayList());lblHistorialCompra.setText("Selecciona una compra para ver su voucher.");
            cargarPrecios();actualizarTotal();
        }catch(Exception e){error(e);}
    }

    private void cargarPrecios(){ProveedorFila p=cmbProveedorPrecio.getValue();try{tblPrecios.setItems(FXCollections.observableArrayList(p==null?List.of():proveedorService.precios(p.id())));}catch(Exception e){tblPrecios.setItems(FXCollections.observableArrayList());error(e);}}
    private void cargarDetalleCompra(CompraFila c){
        try{tblHistorialDetalle.setItems(FXCollections.observableArrayList(c==null?List.of():proveedorService.detallesCompra(c.id())));lblHistorialCompra.setText(c==null?"Selecciona una compra para ver su voucher.":"Compra #"+c.id()+" — "+c.proveedor()+" — S/ "+money(c.total()));}catch(Exception e){error(e);}
    }
    private void actualizarTotal(){BigDecimal total=borrador.stream().map(CompraItem::costoTotal).reduce(BigDecimal.ZERO,BigDecimal::add);lblTotalCompra.setText("Total compra: S/ "+money(total));}

    @FXML private void exportarProveedoresPdf(){exportarProveedores(true);}@FXML private void exportarProveedoresExcel(){exportarProveedores(false);}
    private void exportarProveedores(boolean pdf){
        var filas=tblProveedores.getItems().stream().map(p->List.of(p.nombre(),nvl(p.ruc()),nvl(p.telefono()),nvl(p.email()),nvl(p.direccion()),p.activo()?"ACTIVO":"INACTIVO")).toList();
        exportar("Proveedores_LaFonda",List.of("Proveedor","RUC","Teléfono","Correo","Dirección","Estado"),filas,pdf);
    }
    @FXML private void exportarCompraPdf(){exportarCompra(true);}@FXML private void exportarCompraExcel(){exportarCompra(false);}
    private void exportarCompra(boolean pdf){
        CompraFila c=tblHistorial.getSelectionModel().getSelectedItem();if(c==null){aviso("Selecciona una compra del historial.");return;}
        var filas=proveedorService.detallesCompra(c.id()).stream().map(d->List.of(d.ingrediente(),d.cantidadCompra()==null?"":d.cantidadCompra().stripTrailingZeros().toPlainString()+" "+nvl(d.unidadCompra()),InventarioService.formatearCantidad(d.cantidadBase(),d.unidadBase()),"S/ "+money(d.costoTotal()))).toList();
        exportar("Compra_"+c.id()+"_"+c.proveedor().replaceAll("[^A-Za-z0-9_-]","_"),List.of("Producto","Cantidad comprada","Ingreso inventario","Costo"),filas,pdf);
    }
    private void exportar(String nombre,List<String> cols,List<List<String>> filas,boolean pdf){
        FileChooser fc=new FileChooser();fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(pdf?"PDF":"Excel",pdf?"*.pdf":"*.xlsx"));fc.setInitialFileName(nombre+(pdf?".pdf":".xlsx"));File f=fc.showSaveDialog(tblProveedores.getScene().getWindow());if(f==null)return;
        try{if(pdf)exportador.exportarPdf(nombre.replace('_',' '),cols,filas,f.toPath());else exportador.exportarXlsx(nombre.replace('_',' '),cols,filas,f.toPath());abrir(f);aviso("Archivo guardado en:\n"+f.getAbsolutePath());}catch(Exception e){error(e);}
    }

    @FXML private void cerrar(){((Stage)tblProveedores.getScene().getWindow()).close();}
    private static BigDecimal convertirABase(BigDecimal c,String compra,String base){
        if(compra==null)throw new IllegalArgumentException("Selecciona la unidad de compra.");
        return switch(base){
            case "GRAMO"->switch(compra){case "KG"->c.multiply(new BigDecimal("1000"));case "G"->c;default->throw new IllegalArgumentException("Compra este ingrediente en KG o G.");};
            case "KILOGRAMO"->switch(compra){case "KG"->c;case "G"->c.divide(new BigDecimal("1000"));default->throw new IllegalArgumentException("Compra este ingrediente en KG o G.");};
            case "MILILITRO"->switch(compra){case "L"->c.multiply(new BigDecimal("1000"));case "ML"->c;default->throw new IllegalArgumentException("Compra este ingrediente en L o ML.");};
            case "LITRO"->switch(compra){case "L"->c;case "ML"->c.divide(new BigDecimal("1000"));default->throw new IllegalArgumentException("Compra este ingrediente en L o ML.");};
            default->{if(!"UNIDAD".equals(compra))throw new IllegalArgumentException("Este ingrediente se almacena por unidades.");yield c;}
        };
    }
    private static void restaurarProveedor(ComboBox<ProveedorFila> c,ProveedorFila p){if(p!=null)c.getItems().stream().filter(x->x.id().equals(p.id())).findFirst().ifPresent(c::setValue);}
    private static BigDecimal decimal(String s){if(s==null||s.isBlank())throw new IllegalArgumentException("Completa cantidad y costo.");try{return new BigDecimal(s.trim().replace(',','.'));}catch(Exception e){throw new IllegalArgumentException("Número inválido: "+s);}}
    private static String money(BigDecimal v){return(v==null?BigDecimal.ZERO:v).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
    private static String nvl(String s){return s==null?"":s;}
    private static void abrir(File f) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(f);
                return;
            }
        } catch (Exception ignored) { }
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win"))
                new ProcessBuilder("cmd", "/c", "start", "", f.getAbsolutePath()).start();
        } catch (Exception ignored) { }
    }
    private void aviso(String s){Alert a=new Alert(Alert.AlertType.INFORMATION,s,ButtonType.OK);a.setHeaderText(null);a.showAndWait();}
    private void error(Exception e){aviso(e instanceof IllegalArgumentException||e instanceof IllegalStateException?e.getMessage():"No se pudo completar la operación: "+e.getMessage());}
}
