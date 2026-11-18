package pe.edu.utp.restaurante.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import pe.edu.utp.restaurante.service.AgendaReservasPdf.Agenda;
import pe.edu.utp.restaurante.service.ReservaService.Reserva;

class AgendaReservasPdfTest {
    private final LocalDate hoy=LocalDate.of(2026,11,17);
    private final LocalDateTime emitida=hoy.atTime(11,15);
    private Reserva reserva(long id,LocalDate fecha,int hora,String nombre,String descripcion,String estado,Integer mesa){
        return new Reserva(id,nombre,"987654321",fecha,LocalTime.of(hora,0),4,descripcion,estado,mesa==null?null:mesa.longValue(),mesa,null);
    }
    @Test void soloHoyEnOrdenPorHoraEId(){
        Agenda a=new Agenda(emitida,List.of(reserva(3,hoy,19,"Tercera","","PENDIENTE",null),reserva(4,hoy.plusDays(1),12,"Mañana","","PENDIENTE",null),reserva(2,hoy,12,"Segunda","","ASIGNADA",2),reserva(1,hoy,12,"Primera","","CANCELADA",null)));
        assertEquals(List.of(1L,2L,3L),a.reservas().stream().map(Reserva::id).toList());
        assertEquals("reservas-2026-11-17.pdf",a.archivo());
    }
    @Test void multipaginaConEncabezadosYObservacionesSinTelefono() throws Exception {
        var lista=new ArrayList<Reserva>();
        for(int j=1;j<=45;j++)lista.add(reserva(j,hoy,18,"Cliente "+j,"Celebración: "+("comentario largo ".repeat(25))+" FIN"+j,"ASIGNADA",j));
        var exporter=new AgendaReservasPdf();
        var out=new ByteArrayOutputStream(); exporter.guardar(new Agenda(emitida,lista),out);
        try(PDDocument doc=PDDocument.load(out.toByteArray())){
            assertTrue(doc.getNumberOfPages()>1);
            PDFTextStripper s=new PDFTextStripper();
            String all=s.getText(doc);
            assertFalse(all.contains("987654321"));
            assertTrue(all.contains("FIN45"));
            assertTrue(all.contains("Celebración"));
            for(int i=1;i<=doc.getNumberOfPages();i++){
                s.setStartPage(i);s.setEndPage(i);String page=s.getText(doc);
                assertTrue(page.contains("17/11/2026")); assertTrue(page.contains("Observaciones")); assertTrue(page.contains("Página "+i));
            }
        }
    }
    @Test void sinReservasGeneraUnaHoja() throws Exception {
        try(PDDocument doc=new AgendaReservasPdf().generar(new Agenda(emitida,List.of()))){
            assertEquals(1,doc.getNumberOfPages()); assertTrue(new PDFTextStripper().getText(doc).contains("No hay reservas para hoy"));
        }
    }
}
