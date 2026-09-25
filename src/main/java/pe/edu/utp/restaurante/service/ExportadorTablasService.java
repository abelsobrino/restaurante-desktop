package pe.edu.utp.restaurante.service;

import java.io.IOException;
import java.io.OutputStream;
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

@Service
public class ExportadorTablasService {
    private static final float MARGEN = 28f;
    private static final float ALTO_ENCABEZADO = 24f;
    private static final float ALTO_FILA = 22f;
    private static final PDFont FUENTE = PDType1Font.HELVETICA;
    private static final PDFont FUENTE_NEGRITA = PDType1Font.HELVETICA_BOLD;

    public void exportarPdf(String titulo, List<String> columnas, List<List<String>> filas, Path destino) throws IOException {
        Files.createDirectories(destino.toAbsolutePath().getParent());
        List<String> headers = columnas == null ? List.of() : columnas.stream().map(ExportadorTablasService::limpiar).toList();
        List<List<String>> datos = filas == null ? List.of() : filas;
        if (headers.isEmpty()) throw new IllegalArgumentException("El reporte necesita al menos una columna.");

        PDRectangle horizontalA4 = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        float anchoUtil = horizontalA4.getWidth() - (MARGEN * 2);
        float[] anchos = calcularAnchos(headers, datos, anchoUtil);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = nuevaPagina(doc, horizontalA4);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = horizontalA4.getHeight() - MARGEN;
            int pagina = 1;

            y = dibujarTitulo(cs, limpiar(titulo), y, pagina);
            y = dibujarEncabezado(cs, headers, anchos, y);

            if (datos.isEmpty()) {
                dibujarFila(cs, List.of("Sin registros."), anchos, y, false);
            } else {
                for (int i = 0; i < datos.size(); i++) {
                    if (y - ALTO_FILA < MARGEN + 18) {
                        dibujarPie(cs, horizontalA4, pagina);
                        cs.close();
                        pagina++;
                        page = nuevaPagina(doc, horizontalA4);
                        cs = new PDPageContentStream(doc, page);
                        y = horizontalA4.getHeight() - MARGEN;
                        y = dibujarTitulo(cs, limpiar(titulo), y, pagina);
                        y = dibujarEncabezado(cs, headers, anchos, y);
                    }
                    y = dibujarFila(cs, datos.get(i), anchos, y, i % 2 == 1);
                }
            }

            dibujarPie(cs, horizontalA4, pagina);
            cs.close();
            doc.save(destino.toFile());
        }
    }

    private static PDPage nuevaPagina(PDDocument doc, PDRectangle tamano) {
        PDPage page = new PDPage(tamano);
        doc.addPage(page);
        return page;
    }

    private static float dibujarTitulo(PDPageContentStream cs, String titulo, float y, int pagina) throws IOException {
        cs.beginText();
        cs.setFont(FUENTE_NEGRITA, 15);
        cs.setNonStrokingColor(32, 44, 55);
        cs.newLineAtOffset(MARGEN, y - 15);
        cs.showText(recortarPorAncho(titulo, FUENTE_NEGRITA, 15, 660));
        cs.endText();

        cs.beginText();
        cs.setFont(FUENTE, 8);
        cs.setNonStrokingColor(90, 100, 110);
        cs.newLineAtOffset(730, y - 14);
        cs.showText("Página " + pagina);
        cs.endText();
        return y - 34;
    }

    private static float dibujarEncabezado(PDPageContentStream cs, List<String> columnas, float[] anchos, float y) throws IOException {
        float x = MARGEN;
        cs.setNonStrokingColor(45, 71, 86);
        cs.addRect(MARGEN, y - ALTO_ENCABEZADO, suma(anchos), ALTO_ENCABEZADO);
        cs.fill();

        for (int i = 0; i < anchos.length; i++) {
            cs.setStrokingColor(255, 255, 255);
            cs.addRect(x, y - ALTO_ENCABEZADO, anchos[i], ALTO_ENCABEZADO);
            cs.stroke();
            escribirCelda(cs, i < columnas.size() ? columnas.get(i) : "", x, y, anchos[i], FUENTE_NEGRITA, 8.5f, true);
            x += anchos[i];
        }
        return y - ALTO_ENCABEZADO;
    }

    private static float dibujarFila(PDPageContentStream cs, List<String> fila, float[] anchos, float y, boolean alterna) throws IOException {
        float x = MARGEN;
        if (alterna) {
            cs.setNonStrokingColor(246, 248, 250);
            cs.addRect(MARGEN, y - ALTO_FILA, suma(anchos), ALTO_FILA);
            cs.fill();
        }

        for (int i = 0; i < anchos.length; i++) {
            cs.setStrokingColor(205, 212, 218);
            cs.addRect(x, y - ALTO_FILA, anchos[i], ALTO_FILA);
            cs.stroke();
            String valor = i < fila.size() ? limpiar(fila.get(i)) : "";
            escribirCelda(cs, valor, x, y, anchos[i], FUENTE, 8f, false);
            x += anchos[i];
        }
        return y - ALTO_FILA;
    }

    private static void escribirCelda(PDPageContentStream cs, String texto, float x, float y, float ancho,
                                       PDFont fuente, float tamano, boolean blanco) throws IOException {
        cs.beginText();
        cs.setFont(fuente, tamano);
        if (blanco) cs.setNonStrokingColor(255, 255, 255);
        else cs.setNonStrokingColor(30, 41, 50);
        cs.newLineAtOffset(x + 5, y - 15);
        cs.showText(recortarPorAncho(limpiar(texto), fuente, tamano, Math.max(10, ancho - 10)));
        cs.endText();
    }

    private static void dibujarPie(PDPageContentStream cs, PDRectangle pagina, int numero) throws IOException {
        cs.beginText();
        cs.setFont(FUENTE, 7.5f);
        cs.setNonStrokingColor(105, 115, 125);
        cs.newLineAtOffset(MARGEN, 14);
        cs.showText("La Fonda - reporte generado por el sistema");
        cs.endText();

        cs.beginText();
        cs.setFont(FUENTE, 7.5f);
        cs.newLineAtOffset(pagina.getWidth() - 74, 14);
        cs.showText("Pág. " + numero);
        cs.endText();
    }

    private static float[] calcularAnchos(List<String> columnas, List<List<String>> filas, float anchoDisponible) {
        float[] pesos = new float[columnas.size()];
        for (int i = 0; i < columnas.size(); i++) {
            int max = columnas.get(i) == null ? 6 : columnas.get(i).length();
            for (List<String> fila : filas) {
                if (fila != null && i < fila.size() && fila.get(i) != null) max = Math.max(max, Math.min(48, fila.get(i).length()));
            }
            pesos[i] = Math.max(55f, Math.min(250f, 18f + max * 5.1f));
        }
        float total = suma(pesos);
        float escala = anchoDisponible / total;
        for (int i = 0; i < pesos.length; i++) pesos[i] *= escala;
        return pesos;
    }

    private static float suma(float[] valores) {
        float total = 0;
        for (float v : valores) total += v;
        return total;
    }

    private static String recortarPorAncho(String texto, PDFont fuente, float tamano, float anchoMax) throws IOException {
        String valor = limpiar(texto);
        if (anchoTexto(valor, fuente, tamano) <= anchoMax) return valor;
        String sufijo = "...";
        while (!valor.isEmpty() && anchoTexto(valor + sufijo, fuente, tamano) > anchoMax) {
            valor = valor.substring(0, valor.length() - 1);
        }
        return valor + sufijo;
    }

    private static float anchoTexto(String texto, PDFont fuente, float tamano) throws IOException {
        return fuente.getStringWidth(texto) / 1000f * tamano;
    }

    public void exportarXlsx(String titulo, List<String> columnas, List<List<String>> filas, Path destino) throws IOException {
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
            put(zip, "xl/worksheets/sheet1.xml", hoja(titulo, columnas, filas));
        }
    }

    private static String hoja(String titulo, List<String> columnas, List<List<String>> filas) {
        int totalColumnas = Math.max(1, columnas.size());
        int ultimaFila = 2 + filas.size();
        String ultimaColumna = col(totalColumnas);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"2\" topLeftCell=\"A3\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        xml.append("<cols>");
        for (int i = 0; i < totalColumnas; i++) {
            int max = i < columnas.size() && columnas.get(i) != null ? columnas.get(i).length() : 8;
            for (List<String> fila : filas) if (i < fila.size() && fila.get(i) != null) max = Math.max(max, Math.min(55, fila.get(i).length()));
            double ancho = Math.max(12, Math.min(45, max + 3));
            xml.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(ancho).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");

        xml.append("<row r=\"1\" ht=\"24\" customHeight=\"1\"><c r=\"A1\" t=\"inlineStr\" s=\"1\"><is><t>")
                .append(xml(limpiar(titulo))).append("</t></is></c></row>");
        xml.append("<row r=\"2\" ht=\"22\" customHeight=\"1\">");
        for (int c = 0; c < columnas.size(); c++) {
            xml.append("<c r=\"").append(col(c + 1)).append("2\" t=\"inlineStr\" s=\"2\"><is><t>")
                    .append(xml(limpiar(columnas.get(c)))).append("</t></is></c>");
        }
        xml.append("</row>");

        int r = 3;
        for (List<String> fila : filas) {
            xml.append("<row r=\"").append(r).append("\">");
            for (int c = 0; c < totalColumnas; c++) {
                String valor = c < fila.size() ? limpiar(fila.get(c)) : "";
                xml.append("<c r=\"").append(col(c + 1)).append(r).append("\" t=\"inlineStr\" s=\"3\"><is><t>")
                        .append(xml(valor)).append("</t></is></c>");
            }
            xml.append("</row>");
            r++;
        }
        xml.append("</sheetData>");
        if (totalColumnas > 1) xml.append("<mergeCells count=\"1\"><mergeCell ref=\"A1:").append(ultimaColumna).append("1\"/></mergeCells>");
        xml.append("<autoFilter ref=\"A2:").append(ultimaColumna).append(Math.max(2, ultimaFila)).append("\"/>");
        xml.append("</worksheet>");
        return xml.toString();
    }

    private static String estilosExcel() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="3">
                    <font><sz val="11"/><name val="Calibri"/></font>
                    <font><b/><sz val="15"/><name val="Calibri"/><color rgb="FF20303C"/></font>
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
                    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment vertical="center"/></xf>
                    <xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
                  </cellXfs>
                </styleSheet>
                """;
    }

    private static void put(ZipOutputStream z, String n, String c) throws IOException {
        z.putNextEntry(new ZipEntry(n));
        z.write(c.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static String col(int n) {
        StringBuilder s = new StringBuilder();
        while (n > 0) {
            int x = (n - 1) % 26;
            s.insert(0, (char) ('A' + x));
            n = (n - 1) / 26;
        }
        return s.toString();
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String limpiar(String s) {
        if (s == null) return "";
        String limpio = s.replace('–', '-').replace('—', '-').replace('•', '-').replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        StringBuilder out = new StringBuilder(limpio.length());
        for (char ch : limpio.toCharArray()) out.append(ch >= 32 && ch <= 255 ? ch : '?');
        return out.toString();
    }
}
