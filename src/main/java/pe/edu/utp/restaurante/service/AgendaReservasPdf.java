package pe.edu.utp.restaurante.service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import pe.edu.utp.restaurante.service.ReservaService.Reserva;

/** Agenda de recepción: documento de solo lectura, independiente de las tablas operativas. */
@Service
public class AgendaReservasPdf {
    private static final PDRectangle A4 = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float M = 32, FONT = 9.5f, LINE = 13, PAD = 6, BOTTOM = 48;
    private static final float[] WIDTHS = {48, 165, 60, 75, 108, A4.getWidth()-2*M-456};
    private static final String[] HEADERS = {"Hora", "Titular de la reserva", "Personas", "Mesa", "Estado", "Observaciones"};
    private static final PDFont REG = PDType1Font.HELVETICA, BOLD = PDType1Font.HELVETICA_BOLD;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public record Agenda(LocalDateTime emitida, List<Reserva> reservas) {
        public Agenda {
            reservas = reservas.stream().filter(r -> r.fecha().equals(emitida.toLocalDate()))
                    .sorted(Comparator.comparing(Reserva::hora).thenComparingLong(Reserva::id)).toList();
        }
        public LocalDate fecha() { return emitida.toLocalDate(); }
        public String archivo() { return "reservas-" + fecha() + ".pdf"; }
    }

    public void guardar(Agenda agenda, OutputStream destino) throws IOException {
        try (PDDocument doc = generar(agenda)) { doc.save(destino); }
    }

    /** El llamador debe cerrar el documento. Se utiliza igual para guardar y para imprimir. */
    public PDDocument generar(Agenda agenda) throws IOException {
        List<Fila> filas = new ArrayList<>();
        for (Reserva r : agenda.reservas()) {
            filas.add(fila(List.of(r.hora().format(TIME), seguro(r.nombre()), String.valueOf(r.personas()),
                    r.mesaNumero()==null ? "Sin asignar" : "Mesa " + r.mesaNumero(), estado(r.estado()),
                    r.descripcion()==null || r.descripcion().isBlank() ? "Sin observaciones" : seguro(r.descripcion()))));
        }
        if (filas.isEmpty()) filas.add(fila(List.of("", "No hay reservas para hoy", "", "", "", "")));
        PDDocument doc = new PDDocument();
        try {
            doc.getDocumentInformation().setTitle("La Fonda - Reservas del día " + agenda.fecha().format(DATE));
            int i=0, pagina=1;
            while (i<filas.size()) {
                PDPage page = new PDPage(A4); doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc,page)) {
                    float y = encabezado(cs,agenda);
                    while (i<filas.size()) {
                        Fila fila = filas.get(i);
                        if (y-fila.alto()<BOTTOM) {
                            // Los límites del formulario (120/500 caracteres) garantizan que una fila cabe en una hoja.
                            if (y==A4.getHeight()-M-108) throw new IOException("Una reserva supera el espacio de impresión.");
                            break;
                        }
                        dibujarFila(cs,fila,y,i%2==1); y-=fila.alto(); i++;
                    }
                    color(cs,80,80,80); texto(cs,REG,8,M,27,"Uso interno - Recepción. Verificar cambios en el sistema antes de asignar una mesa.");
                    texto(cs,REG,8,A4.getWidth()-M-65,27,"Página " + pagina++);
                }
            }
            return doc;
        } catch (IOException | RuntimeException e) { doc.close(); throw e; }
    }

    private static float encabezado(PDPageContentStream cs, Agenda a) throws IOException {
        float y=A4.getHeight()-M;
        color(cs,13,71,161); texto(cs,BOLD,13,M,y-13,"LA FONDA | RECEPCIÓN");
        color(cs,27,37,49); texto(cs,BOLD,20,M,y-37,"Reservas del día - " + a.fecha().format(DATE));
        long activas=a.reservas().stream().filter(Reserva::activa).count();
        long pendientes=a.reservas().stream().filter(r->"PENDIENTE".equals(r.estado())).count();
        color(cs,65,75,85); texto(cs,REG,10,M,y-56,"Total: "+a.reservas().size()+"   |   Activas: "+activas+"   |   Sin asignar: "+pendientes);
        texto(cs,REG,9,M,y-72,"Emitido: "+a.emitida().format(DATE)+"  "+a.emitida().format(TIME)+" (Lima)  |  Orden por hora");
        float top=y-84; color(cs,13,71,161); cs.addRect(M,top-24,A4.getWidth()-2*M,24); cs.fill();
        float x=M; color(cs,255,255,255);
        for(int j=0;j<WIDTHS.length;j++){texto(cs,BOLD,9,x+PAD,top-15,HEADERS[j]); x+=WIDTHS[j];}
        return top-24;
    }

    private record Fila(List<List<String>> celdas, float alto) {}
    private static Fila fila(List<String> datos) throws IOException {
        List<List<String>> celdas=new ArrayList<>(); int max=1;
        for(int j=0;j<WIDTHS.length;j++){
            List<String> lineas=envolver(seguro(datos.get(j)),WIDTHS[j]-2*PAD); celdas.add(lineas); max=Math.max(max,lineas.size());
        }
        return new Fila(celdas,2*PAD+max*LINE);
    }
    private static void dibujarFila(PDPageContentStream cs,Fila fila,float y,boolean alterna) throws IOException {
        if(alterna){color(cs,242,245,249);cs.addRect(M,y-fila.alto(),A4.getWidth()-2*M,fila.alto());cs.fill();}
        cs.setStrokingColor(190,200,210); cs.setLineWidth(.35f);
        float x=M;
        for(int j=0;j<WIDTHS.length;j++){
            cs.addRect(x,y-fila.alto(),WIDTHS[j],fila.alto());cs.stroke();
            color(cs,27,37,49); float baseline=y-PAD-FONT;
            for(String s:fila.celdas().get(j)){texto(cs,REG,FONT,x+PAD,baseline,s);baseline-=LINE;}
            x+=WIDTHS[j];
        }
    }
    private static List<String> envolver(String s,float ancho) throws IOException {
        List<String> result=new ArrayList<>(); String remaining=s.strip();
        while(!remaining.isEmpty()){
            int end=0, espacio=-1;
            while(end<remaining.length() && REG.getStringWidth(remaining.substring(0,end+1))*FONT/1000f<=ancho){
                if(remaining.charAt(end)==' ') espacio=end; end++;
            }
            if(end==remaining.length()){result.add(remaining);break;}
            int cut=espacio>0?espacio:Math.max(1,end);
            result.add(remaining.substring(0,cut));remaining=remaining.substring(cut).stripLeading();
        }
        if(result.isEmpty()) result.add("");return result;
    }
    // Helvetica admite los acentos habituales del español. Sustituye glifos no disponibles sin abortar todo el informe.
    private static String seguro(String valor) {
        if(valor==null)return "";
        StringBuilder s=new StringBuilder();
        valor.codePoints().forEach(cp->{
            if(Character.isWhitespace(cp)||Character.isISOControl(cp)){s.append(' ');return;}
            String c=new String(Character.toChars(cp));
            try{REG.encode(c);s.append(c);}catch(IllegalArgumentException|IOException ex){s.append('?');}
        });
        return s.toString();
    }
    private static String estado(String e){return switch(e){
        case "PENDIENTE" -> "Pendiente"; case "ASIGNADA" -> "Asignada"; case "EN_ATENCION" -> "En atención";
        case "FINALIZADA" -> "Finalizada"; case "CANCELADA" -> "Cancelada"; case "NO_ASISTIO" -> "No asistió"; default -> seguro(e);
    };}
    private static void color(PDPageContentStream cs,int r,int g,int b)throws IOException{cs.setNonStrokingColor(r,g,b);}
    private static void texto(PDPageContentStream cs,PDFont fuente,float size,float x,float y,String s)throws IOException{
        cs.beginText();cs.setFont(fuente,size);cs.newLineAtOffset(x,y);cs.showText(s);cs.endText();
    }
}
