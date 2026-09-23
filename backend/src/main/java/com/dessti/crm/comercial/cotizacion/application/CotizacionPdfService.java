package com.dessti.crm.comercial.cotizacion.application;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.dessti.crm.comercial.cotizacion.application.DatosClientePort.DatosCliente;
import com.dessti.crm.comercial.cotizacion.domain.Cotizacion;
import com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Generador del <strong>documento PREMIUM de Cotizacion</strong> en PDF (V60,
 * Req 6). A partir de una {@link Cotizacion}, los datos del Cliente
 * ({@link DatosCliente}) y del emisor ({@link DatosEmisor}, la Empresa/tenant)
 * produce un {@code byte[]} con un PDF de aspecto enterprise, replicando el estilo del
 * comprobante de renta ({@code FacturaRentaPdfService}): un unico color de acento
 * (azul corporativo {@code #35507a}), una sola familia tipografica (Helvetica),
 * jerarquia clara y un total prominente.
 *
 * <h2>Contenido</h2>
 * <ul>
 *   <li>Banda de cabecera con el wordmark del emisor, el titulo "COTIZACION" y el
 *       folio legible (p. ej. {@code COT-2026-0001}).</li>
 *   <li>Bloque Emisor y bloque Cliente (nombre/RFC/correo) en dos columnas.</li>
 *   <li>Metadatos: fecha de emision, valido hasta y moneda.</li>
 *   <li>Tabla de partidas (Descripcion | Cantidad | P. unitario | Subtotal).</li>
 *   <li>Subtotal y total prominente, en la moneda de la Cotizacion.</li>
 *   <li>Condiciones y notas (si las hay).</li>
 *   <li>Pie con nota informativa y numero de pagina.</li>
 * </ul>
 *
 * <p>El servicio es <strong>defensivo</strong>: nunca lanza
 * {@code NullPointerException} por datos ausentes; los campos vacios se omiten.</p>
 */
@Service
public class CotizacionPdfService {

    /** Azul corporativo (primario de la app) usado como unico color de acento. */
    private static final Color ACENTO = new Color(0x35, 0x50, 0x7a);
    /** Gris de texto secundario (etiquetas, notas). */
    private static final Color GRIS_TEXTO = new Color(0x5b, 0x63, 0x6e);
    /** Gris muy claro para las filas cebra de la tabla. */
    private static final Color GRIS_CEBRA = new Color(0xf2, 0xf4, 0xf7);
    /** Blanco para el texto sobre la banda/encabezados de acento. */
    private static final Color BLANCO = Color.WHITE;

    // Familia unica (Helvetica) en pocos tamanos, con variantes de peso/color.
    private static final Font FUENTE_WORDMARK = new Font(Font.HELVETICA, 20f, Font.BOLD, BLANCO);
    private static final Font FUENTE_TITULO_DOC = new Font(Font.HELVETICA, 11f, Font.NORMAL, BLANCO);
    private static final Font FUENTE_FOLIO = new Font(Font.HELVETICA, 10f, Font.BOLD, BLANCO);
    private static final Font FUENTE_SECCION = new Font(Font.HELVETICA, 9f, Font.BOLD, ACENTO);
    private static final Font FUENTE_ETIQUETA = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, GRIS_TEXTO);
    private static final Font FUENTE_VALOR = new Font(Font.HELVETICA, 10f, Font.NORMAL, Color.BLACK);
    private static final Font FUENTE_VALOR_FUERTE = new Font(Font.HELVETICA, 10.5f, Font.BOLD, Color.BLACK);
    private static final Font FUENTE_TABLA_ENCABEZADO = new Font(Font.HELVETICA, 9.5f, Font.BOLD, BLANCO);
    private static final Font FUENTE_TABLA_CELDA = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, Color.BLACK);
    private static final Font FUENTE_TOTAL_ETIQUETA = new Font(Font.HELVETICA, 11f, Font.BOLD, GRIS_TEXTO);
    private static final Font FUENTE_TOTAL_VALOR = new Font(Font.HELVETICA, 16f, Font.BOLD, ACENTO);
    private static final Font FUENTE_SUBTOTAL_ETIQUETA = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, GRIS_TEXTO);
    private static final Font FUENTE_SUBTOTAL_VALOR = new Font(Font.HELVETICA, 10f, Font.NORMAL, Color.BLACK);
    private static final Font FUENTE_NOTA = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, GRIS_TEXTO);

    /** Locale es-MX para formato de fecha/numero. */
    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(ES_MX);

    private static final float MARGEN = 42f;

    /**
     * Genera el PDF de la Cotizacion.
     *
     * @param cotizacion Cotizacion a representar; obligatoria.
     * @param cliente    datos visibles del Cliente; puede ser {@code null} (se
     *                   degrada a mostrar solo el identificador del Cliente).
     * @param emisor     datos del emisor (la Empresa/tenant); obligatorios.
     * @return los bytes del PDF (empieza con la firma {@code %PDF}).
     * @throws ReglaNegocioException si faltan la Cotizacion o el emisor, o si
     *                               ocurre un error al construir el documento.
     */
    public byte[] generar(Cotizacion cotizacion, DatosCliente cliente, DatosEmisor emisor) {
        if (cotizacion == null) {
            throw new ReglaNegocioException("La Cotizacion es obligatoria para generar el PDF.");
        }
        if (emisor == null) {
            throw new ReglaNegocioException("Los datos del emisor son obligatorios para generar el PDF.");
        }

        Document documento = new Document(PageSize.A4, MARGEN, MARGEN, MARGEN, MARGEN + 24f);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, salida);
            writer.setPageEvent(new PieDePagina());
            documento.open();

            documento.add(construirCabecera(cotizacion, emisor));
            documento.add(espacio(14f));
            documento.add(construirEmisorCliente(cotizacion, cliente, emisor));
            documento.add(espacio(12f));
            documento.add(construirMetadatos(cotizacion));
            documento.add(espacio(16f));
            documento.add(construirTablaPartidas(cotizacion));
            documento.add(espacio(4f));
            documento.add(construirTotales(cotizacion));
            agregarCondicionesYNotas(documento, cotizacion);

            documento.close();
        } catch (DocumentException ex) {
            throw new ReglaNegocioException("No se pudo generar el PDF de la Cotizacion.");
        }
        return salida.toByteArray();
    }

    // ------------------------------------------------------------------
    // Secciones del documento
    // ------------------------------------------------------------------

    /** Banda de cabecera con wordmark del emisor, titulo "COTIZACION" y folio. */
    private PdfPTable construirCabecera(Cotizacion cotizacion, DatosEmisor emisor) {
        PdfPTable banda = tablaAncho(new float[] {60f, 40f});
        banda.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell celdaIzq = celdaBanda();
        celdaIzq.addElement(new Paragraph(emisor.nombre(), FUENTE_WORDMARK));
        Paragraph subtitulo = new Paragraph("COTIZACION", FUENTE_TITULO_DOC);
        subtitulo.setSpacingBefore(2f);
        celdaIzq.addElement(subtitulo);
        banda.addCell(celdaIzq);

        PdfPCell celdaDer = celdaBanda();
        celdaDer.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph etiquetaFolio = new Paragraph("FOLIO",
                new Font(Font.HELVETICA, 8f, Font.NORMAL, BLANCO));
        etiquetaFolio.setAlignment(Element.ALIGN_RIGHT);
        celdaDer.addElement(etiquetaFolio);
        Paragraph folio = new Paragraph(seguro(cotizacion.getFolio()), FUENTE_FOLIO);
        folio.setAlignment(Element.ALIGN_RIGHT);
        celdaDer.addElement(folio);
        banda.addCell(celdaDer);

        return banda;
    }

    /** Bloques Emisor y Cliente en dos columnas. */
    private PdfPTable construirEmisorCliente(Cotizacion cotizacion, DatosCliente cliente,
                                             DatosEmisor emisor) {
        PdfPTable tabla = tablaAncho(new float[] {50f, 50f});

        PdfPCell emisorCell = celdaBloque();
        emisorCell.addElement(tituloSeccion("EMISOR"));
        for (Paragraph p : lineasEmisor(emisor)) {
            emisorCell.addElement(p);
        }
        tabla.addCell(emisorCell);

        PdfPCell clienteCell = celdaBloque();
        clienteCell.addElement(tituloSeccion("CLIENTE"));
        for (Paragraph p : lineasCliente(cotizacion, cliente)) {
            clienteCell.addElement(p);
        }
        tabla.addCell(clienteCell);

        return tabla;
    }

    /** Fila de metadatos: fecha de emision, valido hasta y moneda. */
    private PdfPTable construirMetadatos(Cotizacion cotizacion) {
        PdfPTable tabla = tablaAncho(new float[] {34f, 33f, 33f});
        tabla.addCell(celdaMeta("FECHA DE EMISION", formatearFecha(cotizacion.getFechaEmision())));
        tabla.addCell(celdaMeta("VALIDO HASTA", formatearFecha(cotizacion.getValidoHasta())));
        tabla.addCell(celdaMeta("MONEDA", seguro(cotizacion.getMoneda())));
        return tabla;
    }

    /** Tabla de partidas: descripcion, cantidad, precio unitario y subtotal. */
    private PdfPTable construirTablaPartidas(Cotizacion cotizacion) {
        PdfPTable tabla = tablaAncho(new float[] {52f, 12f, 18f, 18f});

        tabla.addCell(celdaEncabezadoTabla("Descripcion", Element.ALIGN_LEFT));
        tabla.addCell(celdaEncabezadoTabla("Cantidad", Element.ALIGN_CENTER));
        tabla.addCell(celdaEncabezadoTabla("P. unitario", Element.ALIGN_RIGHT));
        tabla.addCell(celdaEncabezadoTabla("Subtotal", Element.ALIGN_RIGHT));

        String moneda = seguro(cotizacion.getMoneda());
        List<PartidaCotizacion> partidas = cotizacion.getPartidas();
        boolean cebra = false;
        for (PartidaCotizacion partida : partidas) {
            Color fondo = cebra ? GRIS_CEBRA : BLANCO;
            tabla.addCell(celdaCelda(seguro(partida.getDescripcion()), Element.ALIGN_LEFT, fondo));
            tabla.addCell(celdaCelda(String.valueOf(partida.getCantidad()), Element.ALIGN_CENTER, fondo));
            tabla.addCell(celdaCelda(
                    formatearMonto(partida.getPrecioUnitario(), moneda), Element.ALIGN_RIGHT, fondo));
            tabla.addCell(celdaCelda(
                    formatearMonto(partida.getSubtotal(), moneda), Element.ALIGN_RIGHT, fondo));
            cebra = !cebra;
        }
        return tabla;
    }

    /** Subtotal y total prominente alineados a la derecha. */
    private PdfPTable construirTotales(Cotizacion cotizacion) {
        PdfPTable tabla = tablaAncho(new float[] {62f, 38f});
        String moneda = seguro(cotizacion.getMoneda());

        // Subtotal.
        tabla.addCell(celdaVacia());
        PdfPCell subtotalCell = new PdfPCell();
        subtotalCell.setBorder(Rectangle.NO_BORDER);
        subtotalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        subtotalCell.setPaddingTop(6f);
        Phrase subtotalPh = new Phrase();
        subtotalPh.add(new com.lowagie.text.Chunk("Subtotal   ", FUENTE_SUBTOTAL_ETIQUETA));
        subtotalPh.add(new com.lowagie.text.Chunk(
                formatearMonto(cotizacion.getSubtotal(), moneda), FUENTE_SUBTOTAL_VALOR));
        Paragraph subtotalPar = new Paragraph(subtotalPh);
        subtotalPar.setAlignment(Element.ALIGN_RIGHT);
        subtotalCell.addElement(subtotalPar);
        tabla.addCell(subtotalCell);

        // Total prominente.
        tabla.addCell(celdaVacia());
        PdfPCell totalCell = new PdfPCell();
        totalCell.setBorder(Rectangle.TOP);
        totalCell.setBorderColor(ACENTO);
        totalCell.setBorderWidthTop(1.4f);
        totalCell.setPaddingTop(8f);
        totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph etiqueta = new Paragraph("TOTAL", FUENTE_TOTAL_ETIQUETA);
        etiqueta.setAlignment(Element.ALIGN_RIGHT);
        totalCell.addElement(etiqueta);
        Paragraph valor = new Paragraph(formatearMonto(cotizacion.getTotal(), moneda), FUENTE_TOTAL_VALOR);
        valor.setAlignment(Element.ALIGN_RIGHT);
        totalCell.addElement(valor);
        tabla.addCell(totalCell);

        return tabla;
    }

    /** Agrega los bloques de condiciones y notas al documento, si los hay. */
    private void agregarCondicionesYNotas(Document documento, Cotizacion cotizacion)
            throws DocumentException {
        if (presente(cotizacion.getCondiciones())) {
            documento.add(espacio(16f));
            documento.add(tituloSeccion("CONDICIONES"));
            documento.add(valor(cotizacion.getCondiciones()));
        }
        if (presente(cotizacion.getNotas())) {
            documento.add(espacio(10f));
            documento.add(tituloSeccion("NOTAS"));
            documento.add(valor(cotizacion.getNotas()));
        }
    }

    // ------------------------------------------------------------------
    // Construccion de lineas de texto (defensivas ante nulos)
    // ------------------------------------------------------------------

    private List<Paragraph> lineasEmisor(DatosEmisor emisor) {
        List<Paragraph> lineas = new ArrayList<>();
        lineas.add(valorFuerte(emisor.nombre()));
        agregarSiPresente(lineas, null, emisor.nombreComercial());
        agregarSiPresente(lineas, "RFC", emisor.rfc());
        agregarSiPresente(lineas, null, emisor.direccion());
        agregarSiPresente(lineas, null, emisor.email());
        agregarSiPresente(lineas, null, emisor.sitioWeb());
        return lineas;
    }

    private List<Paragraph> lineasCliente(Cotizacion cotizacion, DatosCliente cliente) {
        List<Paragraph> lineas = new ArrayList<>();
        if (cliente == null) {
            // Degradacion controlada: el Cliente no es resoluble en este contexto.
            lineas.add(valorFuerte("Cliente: " + seguro(cotizacion.getClienteId())));
            return lineas;
        }
        lineas.add(valorFuerte(seguro(cliente.nombre())));
        agregarSiPresente(lineas, "RFC", cliente.rfc());
        agregarSiPresente(lineas, null, cliente.email());
        return lineas;
    }

    private void agregarSiPresente(List<Paragraph> lineas, String etiqueta, String valor) {
        if (!presente(valor)) {
            return;
        }
        String texto = (etiqueta == null || etiqueta.isBlank())
                ? valor.strip()
                : etiqueta + ": " + valor.strip();
        lineas.add(valor(texto));
    }

    // ------------------------------------------------------------------
    // Formateo de fechas y montos (es-MX, defensivo)
    // ------------------------------------------------------------------

    /** Fecha en formato largo es-MX; cadena vacia si es nula. */
    String formatearFecha(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return fecha.format(FORMATO_FECHA);
    }

    /**
     * Formatea un monto con 2 decimales en es-MX, prefijado con el codigo de
     * moneda (por ejemplo {@code MXN 1,234.50}). Nunca falla si la moneda no es
     * una moneda conocida por la JVM: solo se usa el codigo como prefijo.
     */
    String formatearMonto(BigDecimal monto, String monedaCodigo) {
        BigDecimal valor = (monto == null ? BigDecimal.ZERO : monto)
                .setScale(2, RoundingMode.HALF_UP);
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(ES_MX);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        String numero = nf.format(valor);
        String codigo = seguro(monedaCodigo).trim();
        if (!codigo.isEmpty()) {
            try {
                Currency.getInstance(codigo);
            } catch (IllegalArgumentException ignorada) {
                // No es una moneda ISO reconocida: se conserva como prefijo textual.
            }
            return codigo + " " + numero;
        }
        return numero;
    }

    // ------------------------------------------------------------------
    // Utilidades de construccion (celdas, fuentes, espacios)
    // ------------------------------------------------------------------

    private Paragraph tituloSeccion(String texto) {
        Paragraph p = new Paragraph(texto, FUENTE_SECCION);
        p.setSpacingAfter(4f);
        return p;
    }

    private Paragraph valor(String texto) {
        Paragraph p = new Paragraph(seguro(texto), FUENTE_VALOR);
        p.setSpacingAfter(1.5f);
        return p;
    }

    private Paragraph valorFuerte(String texto) {
        Paragraph p = new Paragraph(seguro(texto), FUENTE_VALOR_FUERTE);
        p.setSpacingAfter(2f);
        return p;
    }

    private PdfPTable tablaAncho(float[] proporciones) {
        PdfPTable tabla = new PdfPTable(proporciones.length);
        tabla.setWidthPercentage(100f);
        try {
            tabla.setWidths(proporciones);
        } catch (DocumentException ex) {
            // Proporciones estaticas siempre validas; no ocurre en la practica.
            throw new IllegalStateException(ex);
        }
        return tabla;
    }

    private PdfPCell celdaBanda() {
        PdfPCell celda = new PdfPCell();
        celda.setBackgroundColor(ACENTO);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(14f);
        return celda;
    }

    private PdfPCell celdaBloque() {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPaddingRight(12f);
        celda.setPaddingBottom(4f);
        return celda;
    }

    private PdfPCell celdaMeta(String etiqueta, String valor) {
        PdfPCell celda = new PdfPCell();
        celda.setBackgroundColor(GRIS_CEBRA);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(8f);
        celda.addElement(new Paragraph(etiqueta, FUENTE_ETIQUETA));
        Paragraph v = new Paragraph(seguro(valor), FUENTE_VALOR_FUERTE);
        v.setSpacingBefore(1f);
        celda.addElement(v);
        return celda;
    }

    private PdfPCell celdaEncabezadoTabla(String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, FUENTE_TABLA_ENCABEZADO));
        celda.setBackgroundColor(ACENTO);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(7f);
        return celda;
    }

    private PdfPCell celdaCelda(String texto, int alineacion, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(seguro(texto), FUENTE_TABLA_CELDA));
        celda.setBackgroundColor(fondo);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(6f);
        return celda;
    }

    private PdfPCell celdaVacia() {
        PdfPCell vacio = new PdfPCell();
        vacio.setBorder(Rectangle.NO_BORDER);
        return vacio;
    }

    private Paragraph espacio(float altura) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(altura);
        return p;
    }

    private static boolean presente(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String seguro(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    /**
     * Evento de pagina que dibuja el pie: nota informativa, instante de generacion
     * y numero de pagina. Se ejecuta al finalizar cada pagina.
     */
    private static final class PieDePagina extends PdfPageEventHelper {

        private static final String NOTA =
                "Documento de cotizacion comercial. Los precios estan expresados en la moneda "
                        + "indicada y pueden estar sujetos a vigencia.";

        @Override
        public void onEndPage(PdfWriter writer, Document documento) {
            Rectangle pagina = documento.getPageSize();
            float x = documento.leftMargin();
            float ancho = pagina.getWidth() - documento.leftMargin() - documento.rightMargin();
            float y = documento.bottomMargin() - 6f;

            Phrase nota = new Phrase(NOTA, FUENTE_NOTA);
            ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_CENTER,
                    nota, x + ancho / 2f, y + 8f, 0);

            String generado = "Generado el "
                    + java.time.LocalDateTime.now(ZoneId.of("America/Mexico_City"))
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ES_MX));
            Phrase izquierda = new Phrase(generado, FUENTE_NOTA);
            ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_LEFT,
                    izquierda, x, y - 4f, 0);

            Phrase derecha = new Phrase("Pagina " + writer.getPageNumber(), FUENTE_NOTA);
            ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_RIGHT,
                    derecha, x + ancho, y - 4f, 0);
        }
    }
}
