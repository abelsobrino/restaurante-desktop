package pe.edu.utp.restaurante.service;

import java.nio.file.Path;
import java.util.ArrayList;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ComprobanteServiceTest {
    @TempDir Path temp;
    @Test void paginaPedidosLargosSinPerderElTotal() throws Exception {
        var lineas = new ArrayList<String>();
        lineas.add("RESTAURANTE LA FONDA");
        for (int i = 0; i < 100; i++) lineas.add("1 x Lomo saltado con descripción larga y acentos " + i);
        lineas.add("TOTAL PAGADO: S/ 118.00");
        Path pdf = temp.resolve("comprobante.pdf");
        ComprobanteService.escribir(pdf, lineas);
        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            assertTrue(doc.getNumberOfPages() >= 3);
            String texto = new PDFTextStripper().getText(doc);
            assertTrue(texto.contains("TOTAL PAGADO: S/ 118.00"));
            assertTrue(texto.contains("acentos 99"));
        }
    }
}
