package pe.edu.utp.restaurante.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Importes(BigDecimal subtotal, BigDecimal igv, BigDecimal total) {
    public static Importes desdeTotal(BigDecimal importe) {
        BigDecimal total = importe.setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = total.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
        return new Importes(base, total.subtract(base), total);
    }
}
