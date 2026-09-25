package pe.edu.utp.restaurante.service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import pe.edu.utp.restaurante.service.ReporteAvanzadoService.*;

@Service
public class ExportadorReportesService {

    public void exportarPdf(Reporte r, Path destino) throws IOException {
        Files.createDirectories(destino.toAbsolutePath().getParent());
        try (PDDocument doc = new PDDocument(); PdfCursor pdf = new PdfCursor(doc)) {
            pdf.titulo("LA FONDA - REPORTE DE VENTAS");
            pdf.subtitulo("Periodo: " + r.desde() + " al " + r.hasta());

            Resumen s = r.resumen();
            pdf.tabla("Resumen", List.of("Indicador", "Valor"), List.of(
                    List.of("Ventas cobradas", "S/ " + money(s.ventas())),
                    List.of("Subtotal", "S/ " + money(s.subtotal())),
                    List.of("IGV", "S/ " + money(s.igv())),
                    List.of("Descuentos", "S/ " + money(s.descuentos())),
                    List.of("Delivery", "S/ " + money(s.delivery())),
                    List.of("Pedidos", String.valueOf(s.pedidos())),
                    List.of("Ticket promedio", "S/ " + money(s.ticketPromedio())),
                    List.of("Compras a proveedores", "S/ " + money(s.compras())),
                    List.of("Otros gastos", "S/ " + money(s.gastos())),
                    List.of("Resultado estimado", "S/ " + money(s.resultadoEstimado())),
                    List.of("Saldo acumulado caja", "S/ " + money(s.saldoCajaAcumulado()))
            ));

            List<List<String>> porDia = new ArrayList<>();
            for (VentaDia d : r.porDia()) porDia.add(List.of(d.fecha().toString(), String.valueOf(d.pedidos()), "S/ " + money(d.total())));
            pdf.tabla("Ventas por día", List.of("Fecha", "Pedidos", "Total"), porDia);

            List<List<String>> metodos = new ArrayList<>();
            for (MetodoPago m : r.metodos()) metodos.add(List.of(m.metodo(), String.valueOf(m.operaciones()), "S/ " + money(m.total())));
            pdf.tabla("Medios de pago", List.of("Método", "Operaciones", "Total"), metodos);

            List<List<String>> movimientos = new ArrayList<>();
            for (MovimientoCaja m : r.movimientosCaja()) movimientos.add(List.of(m.fecha().toString(), m.tipo(), m.descripcion(), "S/ " + money(m.monto())));
            pdf.tabla("Ingresos y egresos", List.of("Fecha", "Tipo", "Descripción", "Monto"), movimientos);

            List<List<String>> platos = new ArrayList<>();
            for (PlatoVendido p : r.topPlatos()) platos.add(List.of(p.plato(), String.valueOf(p.unidades()), "S/ " + money(p.importe())));
            pdf.tabla("Top platos", List.of("Plato", "Unidades", "Importe"), platos);

            doc.save(destino.toFile());
        }
    }

    public void exportarXlsx(Reporte r, Path destino) throws IOException {
        Files.createDirectories(destino.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(destino); ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                      <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>
                    """);
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="Reporte" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """);
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/styles.xml", estilosExcel());
            put(zip, "xl/worksheets/sheet1.xml", hojaXml(r));
        }
    }

    private record ExcelFila(List<Object> valores, int estilo) {}

    private static String hojaXml(Reporte r) {
        List<ExcelFila> filas = new ArrayList<>();
        filas.add(new ExcelFila(List.of("LA FONDA - REPORTE DE VENTAS"), 1));
        filas.add(new ExcelFila(List.of("Desde", r.desde().toString(), "Hasta", r.hasta().toString()), 3));
        filas.add(new ExcelFila(List.of(), 0));

        Resumen s = r.resumen();
        filas.add(new ExcelFila(List.of("RESUMEN", "VALOR"), 2));
        filas.add(new ExcelFila(List.of("Ventas cobradas", s.ventas()), 3));
        filas.add(new ExcelFila(List.of("Subtotal", s.subtotal()), 3));
        filas.add(new ExcelFila(List.of("IGV", s.igv()), 3));
        filas.add(new ExcelFila(List.of("Descuentos", s.descuentos()), 3));
        filas.add(new ExcelFila(List.of("Delivery", s.delivery()), 3));
        filas.add(new ExcelFila(List.of("Pedidos", s.pedidos()), 3));
        filas.add(new ExcelFila(List.of("Ticket promedio", s.ticketPromedio()), 3));
        filas.add(new ExcelFila(List.of("Compras proveedores", s.compras()), 3));
        filas.add(new ExcelFila(List.of("Otros gastos", s.gastos()), 3));
        filas.add(new ExcelFila(List.of("Resultado estimado", s.resultadoEstimado()), 3));
        filas.add(new ExcelFila(List.of("Saldo acumulado caja", s.saldoCajaAcumulado()), 3));
        filas.add(new ExcelFila(List.of(), 0));

        filas.add(new ExcelFila(List.of("VENTAS POR DÍA", "PEDIDOS", "TOTAL"), 2));
        for (VentaDia d : r.porDia()) filas.add(new ExcelFila(List.of(d.fecha().toString(), d.pedidos(), d.total()), 3));
        filas.add(new ExcelFila(List.of(), 0));

        filas.add(new ExcelFila(List.of("MÉTODO DE PAGO", "OPERACIONES", "TOTAL"), 2));
        for (MetodoPago m : r.metodos()) filas.add(new ExcelFila(List.of(m.metodo(), m.operaciones(), m.total()), 3));
        filas.add(new ExcelFila(List.of(), 0));

        filas.add(new ExcelFila(List.of("MOVIMIENTOS CAJA", "TIPO", "DESCRIPCIÓN", "MONTO"), 2));
        for (MovimientoCaja m : r.movimientosCaja()) filas.add(new ExcelFila(List.of(m.fecha().toString(), m.tipo(), m.descripcion(), m.monto()), 3));
        filas.add(new ExcelFila(List.of(), 0));

        filas.add(new ExcelFila(List.of("TOP PLATOS", "UNIDADES", "IMPORTE"), 2));
        for (PlatoVendido p : r.topPlatos()) filas.add(new ExcelFila(List.of(p.plato(), p.unidades(), p.importe()), 3));

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"2\" topLeftCell=\"A3\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        xml.append("<cols><col min=\"1\" max=\"1\" width=\"27\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"20\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"55\" customWidth=\"1\"/><col min=\"4\" max=\"4\" width=\"20\" customWidth=\"1\"/></cols>");
        xml.append("<sheetData>");
        int row = 1;
        for (ExcelFila fila : filas) {
            xml.append("<row r=\"").append(row).append("\">");
            for (int col = 0; col < fila.valores().size(); col++) {
                Object v = fila.valores().get(col);
                String ref = colName(col + 1) + row;
                if (v instanceof Number) {
                    xml.append("<c r=\"").append(ref).append("\" s=\"").append(fila.estilo()).append("\"><v>").append(v).append("</v></c>");
                } else {
                    xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\" s=\"").append(fila.estilo()).append("\"><is><t>")
                            .append(escapeXml(String.valueOf(v))).append("</t></is></c>");
                }
            }
            xml.append("</row>");
            row++;
        }
        xml.append("</sheetData><mergeCells count=\"1\"><mergeCell ref=\"A1:D1\"/></mergeCells></worksheet>");
        return xml.toString();
    }

    private static String estilosExcel() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="3">
                    <font><sz val="11"/><name val="Calibri"/></font>
                    <font><b/><sz val="16"/><name val="Calibri"/><color rgb="FF20303C"/></font>
                    <font><b/><sz val="11"/><name val="Calibri"/><color rgb="FFFFFFFF"/></font>
                  </fonts>
                  <fills count="3">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                    <fill><patternFill patternType="solid"><fgColor rgb="FF2D4756"/><bgColor indexed="64"/></patternFill></fill>
                  </fills>
                  <borders count="2">
                    <border><left/><right/><top/><bottom/><diagonal/></border>
                    <border><left style="thin"><color rgb="FFD0D7DD"/></left><right style="thin"><color rgb="FFD0D7DD"/></right><top style="thin"><color rgb="FFD0D7DD"/></top><bottom style="thin"><color rgb="FFD0D7DD"/></bottom><diagonal/></border>
                  </borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="4">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                    <xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
                  </cellXfs>
                </styleSheet>
                """;
    }

    private static final class PdfCursor implements AutoCloseable {
        private static final float MARGEN = 28f;
        private static final float ALTO_HEADER = 22f;
        private static final float ALTO_FILA = 20f;
        private static final PDFont REGULAR = PDType1Font.HELVETICA;
        private static final PDFont BOLD = PDType1Font.HELVETICA_BOLD;
        private final PDDocument doc;
        private final PDRectangle tamano = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        private PDPageContentStream stream;
        private float y;
        private int pagina;

        private PdfCursor(PDDocument doc) throws IOException {
            this.doc = doc;
            nuevaPagina();
        }

        private void nuevaPagina() throws IOException {
            if (stream != null) {
                pie();
                stream.close();
            }
            PDPage page = new PDPage(tamano);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            pagina++;
            y = tamano.getHeight() - MARGEN;
        }

        private void titulo(String texto) throws IOException {
            texto(MARGEN, y - 16, BOLD, 16, 32, 44, 55, recortar(texto, BOLD, 16, 650));
            y -= 30;
        }

        private void subtitulo(String texto) throws IOException {
            texto(MARGEN, y - 10, REGULAR, 9, 90, 100, 110, recortar(texto, REGULAR, 9, 700));
            y -= 24;
        }

        private void tabla(String nombre, List<String> columnas, List<List<String>> filas) throws IOException {
            asegurar(58);
            texto(MARGEN, y - 12, BOLD, 11, 32, 44, 55, sanitizePdf(nombre));
            y -= 20;
            float[] anchos = anchos(columnas, filas, tamano.getWidth() - MARGEN * 2);
            y = encabezado(columnas, anchos, y);
            if (filas.isEmpty()) {
                y = fila(List.of("Sin registros."), anchos, y, false);
            } else {
                for (int i = 0; i < filas.size(); i++) {
                    if (y - ALTO_FILA < MARGEN + 16) {
                        nuevaPagina();
                        texto(MARGEN, y - 12, BOLD, 10, 32, 44, 55, sanitizePdf(nombre + " (continuación)"));
                        y -= 18;
                        y = encabezado(columnas, anchos, y);
                    }
                    y = fila(filas.get(i), anchos, y, i % 2 == 1);
                }
            }
            y -= 14;
        }

        private float encabezado(List<String> columnas, float[] anchos, float top) throws IOException {
            float x = MARGEN;
            stream.setNonStrokingColor(45, 71, 86);
            stream.addRect(MARGEN, top - ALTO_HEADER, suma(anchos), ALTO_HEADER);
            stream.fill();
            for (int i = 0; i < anchos.length; i++) {
                stream.setStrokingColor(255, 255, 255);
                stream.addRect(x, top - ALTO_HEADER, anchos[i], ALTO_HEADER);
                stream.stroke();
                texto(x + 4, top - 14, BOLD, 8.3f, 255, 255, 255,
                        recortar(i < columnas.size() ? columnas.get(i) : "", BOLD, 8.3f, anchos[i] - 8));
                x += anchos[i];
            }
            return top - ALTO_HEADER;
        }

        private float fila(List<String> valores, float[] anchos, float top, boolean alterna) throws IOException {
            float x = MARGEN;
            if (alterna) {
                stream.setNonStrokingColor(246, 248, 250);
                stream.addRect(MARGEN, top - ALTO_FILA, suma(anchos), ALTO_FILA);
                stream.fill();
            }
            for (int i = 0; i < anchos.length; i++) {
                stream.setStrokingColor(205, 212, 218);
                stream.addRect(x, top - ALTO_FILA, anchos[i], ALTO_FILA);
                stream.stroke();
                String valor = i < valores.size() ? valores.get(i) : "";
                texto(x + 4, top - 13, REGULAR, 8, 30, 41, 50, recortar(valor, REGULAR, 8, anchos[i] - 8));
                x += anchos[i];
            }
            return top - ALTO_FILA;
        }

        private void asegurar(float requerido) throws IOException {
            if (y - requerido < MARGEN + 18) nuevaPagina();
        }

        private void texto(float x, float yy, PDFont fuente, float size, int r, int g, int b, String valor) throws IOException {
            stream.beginText();
            stream.setFont(fuente, size);
            stream.setNonStrokingColor(r, g, b);
            stream.newLineAtOffset(x, yy);
            stream.showText(sanitizePdf(valor));
            stream.endText();
        }

        private void pie() throws IOException {
            if (stream == null) return;
            texto(MARGEN, 14, REGULAR, 7.5f, 105, 115, 125, "La Fonda - reporte generado por el sistema");
            texto(tamano.getWidth() - 70, 14, REGULAR, 7.5f, 105, 115, 125, "Pág. " + pagina);
        }

        @Override public void close() throws IOException {
            if (stream != null) {
                pie();
                stream.close();
                stream = null;
            }
        }

        private static float[] anchos(List<String> columnas, List<List<String>> filas, float totalDisponible) {
            float[] pesos = new float[columnas.size()];
            for (int i = 0; i < columnas.size(); i++) {
                int max = columnas.get(i) == null ? 6 : columnas.get(i).length();
                for (List<String> fila : filas) if (i < fila.size() && fila.get(i) != null) max = Math.max(max, Math.min(50, fila.get(i).length()));
                pesos[i] = Math.max(70, Math.min(260, 20 + max * 5f));
            }
            float total = suma(pesos);
            float escala = totalDisponible / total;
            for (int i = 0; i < pesos.length; i++) pesos[i] *= escala;
            return pesos;
        }

        private static float suma(float[] valores) {
            float total = 0;
            for (float v : valores) total += v;
            return total;
        }

        private static String recortar(String valor, PDFont fuente, float size, float max) throws IOException {
            String s = sanitizePdf(valor);
            if (fuente.getStringWidth(s) / 1000f * size <= max) return s;
            while (!s.isEmpty() && fuente.getStringWidth(s + "...") / 1000f * size > max) s = s.substring(0, s.length() - 1);
            return s + "...";
        }
    }

    private static String sanitizePdf(String s) {
        if (s == null) return "";
        String limpio = s.replace('–', '-').replace('—', '-').replace('•', '-').replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        StringBuilder out = new StringBuilder(limpio.length());
        for (char ch : limpio.toCharArray()) out.append(ch >= 32 && ch <= 255 ? ch : '?');
        return out.toString();
    }

    private static String money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String colName(int n) {
        StringBuilder s = new StringBuilder();
        while (n > 0) {
            int r = (n - 1) % 26;
            s.insert(0, (char) ('A' + r));
            n = (n - 1) / 26;
        }
        return s.toString();
    }
}
