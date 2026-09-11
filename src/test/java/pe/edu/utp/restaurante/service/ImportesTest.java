package pe.edu.utp.restaurante.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImportesTest {
    @Test void separaIgvSinAumentarTotal() {
        Importes i = Importes.desdeTotal(new BigDecimal("118.00"));
        assertEquals(new BigDecimal("100.00"), i.subtotal());
        assertEquals(new BigDecimal("18.00"), i.igv());
        assertEquals(new BigDecimal("118.00"), i.total());
    }
    @Test void conservaCentimosEnTodosLosImportes() {
        for (int centimos = 0; centimos <= 100000; centimos++) {
            BigDecimal total = BigDecimal.valueOf(centimos, 2);
            Importes i = Importes.desdeTotal(total);
            assertEquals(total, i.subtotal().add(i.igv()));
        }
    }
}
