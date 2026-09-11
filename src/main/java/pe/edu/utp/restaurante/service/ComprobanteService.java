package pe.edu.utp.restaurante.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.edu.utp.restaurante.model.*;
import pe.edu.utp.restaurante.repository.*;

@Service
public class ComprobanteService {
    @Autowired private PedidoRepository pedidos;
    @Autowired private PedidoDetalleRepository detalles;
    @Autowired private MesaRepository mesas;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PlatoRepository platos;

    public void generar(Pago pago, Path archivo) throws IOException {
        Pedido pedido = pedidos.findById(pago.getPedidoId()).orElseThrow();
        String mesa = pedido.getMesaId() == null ? "Sin mesa" : mesas.findById(pedido.getMesaId()).map(m -> String.valueOf(m.getNumero())).orElse("No disponible");
        String mozo = pedido.getUsuarioId() == null ? "No disponible" : usuarios.findById(pedido.getUsuarioId()).map(u -> u.getNombre() + " " + u.getApellido()).orElse("No disponible");
        if (pedido.getMesaNumero() != null) mesa = String.valueOf(pedido.getMesaNumero());
        if (pedido.getMozoNombre() != null) mozo = pedido.getMozoNombre();
        List<String> lineas = new ArrayList<>();
        lineas.add("RESTAURANTE LA FONDA");
        lineas.add("COMPROBANTE INTERNO DE PAGO - " + pago.getId());
        lineas.add("");
        lineas.add("Fecha: " + pago.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        lineas.add("Pedido: " + pedido.getCodigo() + "    Mesa: " + mesa);
        lineas.add("Mozo: " + mozo);
        lineas.add("Pago: " + pago.getMetodo());
        if (pago.getReferencia() != null && !pago.getReferencia().isBlank()) lineas.add("Referencia: " + pago.getReferencia());
        lineas.add("--------------------------------------------------------------------------");
        lineas.add("CANT.  DESCRIPCION / PRECIO UNITARIO / IMPORTE (S/)");
        lineas.add("--------------------------------------------------------------------------");
        for (PedidoDetalle d : detalles.findByPedidoId(pedido.getId())) {
            String nombre = platos.findById(d.getPlatoId()).map(Plato::getNombre).orElse("Plato " + d.getPlatoId());
            if (d.getPlatoNombre() != null) nombre = d.getPlatoNombre();
            lineas.add(d.getCantidad() + " x " + nombre);
            lineas.add("      P. unitario: S/ " + d.getPrecioUnitario() + "     Importe: S/ " + d.getSubtotal());
        }
        Importes importes = Importes.desdeTotal(pago.getMonto());
        lineas.add("--------------------------------------------------------------------------");
        lineas.add("SUBTOTAL SIN IGV: S/ " + importes.subtotal());
        lineas.add("IGV (18%):        S/ " + importes.igv());
        lineas.add("TOTAL PAGADO:     S/ " + importes.total());
        lineas.add("");
        lineas.add("Precios de carta con IGV incluido.");
        lineas.add("Gracias por su visita.");
        escribir(archivo, lineas);
    }

    public static void escribir(Path archivo, List<String> contenido) throws IOException {
        List<String> lineas = new ArrayList<>();
        for (String texto : contenido) {
            String seguro = normalizar(texto);
            if (seguro.isEmpty()) lineas.add("");
            while (!seguro.isEmpty()) {
                int fin = Math.min(76, seguro.length());
                if (fin < seguro.length() && seguro.lastIndexOf(' ', fin) > 20) fin = seguro.lastIndexOf(' ', fin);
                lineas.add(seguro.substring(0, fin));
                seguro = seguro.substring(fin).stripLeading();
            }
        }
        try (PDDocument documento = new PDDocument()) {
            for (int inicio = 0; inicio < lineas.size(); inicio += 45) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                documento.addPage(pagina);
                try (PDPageContentStream out = new PDPageContentStream(documento, pagina)) {
                    out.beginText();
                    out.setFont(PDType1Font.COURIER, 10);
                    out.setLeading(16);
                    out.newLineAtOffset(48, 790);
                    for (int i = inicio; i < Math.min(inicio + 45, lineas.size()); i++) {
                        out.showText(lineas.get(i));
                        out.newLine();
                    }
                    out.endText();
                    out.beginText();
                    out.setFont(PDType1Font.COURIER, 9);
                    out.newLineAtOffset(48, 35);
                    out.showText("La Fonda | Comprobante interno | Pagina " + (inicio / 45 + 1));
                    out.endText();
                }
            }
            documento.save(archivo.toFile());
        }
    }

    private static String normalizar(String texto) {
        StringBuilder resultado = new StringBuilder();
        for (int cp : texto.codePoints().toArray()) {
            String letra = new String(Character.toChars(cp));
            if (Character.isISOControl(cp)) { resultado.append(' '); continue; }
            try { PDType1Font.COURIER.encode(letra); resultado.append(letra); }
            catch (Exception e) { resultado.append('?'); }
        }
        return resultado.toString();
    }
}
