package com.dessti.crm.platform.monetizacion.application;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.dessti.crm.platform.empresas.Empresa;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.monetizacion.domain.FacturaRenta;
import com.dessti.crm.platform.monetizacion.domain.FacturaRentaLinea;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Generador del <strong>comprobante PREMIUM de renta de modulos</strong> en PDF
 * (Fase A). A partir de una {@link FacturaRenta}, la {@link Empresa} receptora y
 * los datos del emisor ({@link EmisorProperties}) produce un {@code byte[]} con
 * un PDF de aspecto enterprise: un unico color de acento (azul corporativo
 * {@code #35507a}), una sola familia tipografica (Helvetica) en pocos tamanos,
 * jerarquia clara y un total prominente.
 *
 * <h2>Diseno</h2>
 * <ul>
 *   <li>Banda de cabecera con el wordmark del emisor, el titulo del documento y
 *       el folio (forma corta del id de la factura).</li>
 *   <li>Bloque Emisor (Dess-TI) y bloque Receptor (Empresa) en dos columnas.</li>
 *   <li>Fila de metadatos: periodo (MMMM yyyy es-MX), fecha de emision y moneda.</li>
 *   <li>Tabla de conceptos (Modulo | Importe) con encabezado en color de acento
 *       y filas cebra sutiles.</li>
 *   <li>Total prominente alineado a la derecha.</li>
 *   <li>Pie con la nota legal (NO es un CFDI) y numero de pagina.</li>
 * </ul>
 *
 * <p>El servicio es <strong>defensivo</strong>: nunca lanza {@code NullPointerException}
 * por datos ausentes de la Empresa; los campos vacios simplemente se omiten.</p>
 */
@Service
public class FacturaRentaPdfService {

    /** Azul corporativo (primario de la app) usado como unico color de acento. */
    private static final Color ACENTO = new Color(0x35, 0x50, 0x7a);
    /** Gris de texto secundario (etiquetas, notas). */
    private static final Color GRIS_TEXTO = new Color(0x5b, 0x63, 0x6e);
    /** Gris muy claro para las filas cebra de la tabla. */
    private static final Color GRIS_CEBRA = new Color(0xf2, 0xf4, 0xf7);
    /** Blanco para el texto sobre la banda/encabezados de acento. */
    private static final Color BLANCO = Color.WHITE;

    // Familia unica (Helvetica) en 2-3 tamanos, con variantes de peso/color.
    private static final Font FUENTE_WORDMARK = new Font(Font.HELVETICA, 20f, Font.BOLD, BLANCO);
    private static final Font FUENTE_TITULO_DOC = new Font(Font.HELVETICA, 11f, Font.NORMAL, BLANCO);
    private static final Font FUENTE_FOLIO = new Font(Font.HELVETICA, 10f, Font.BOLD, BLANCO);
    private static final Font FUENTE_SECCION = new Font(Font.HELVETICA, 9f, Font.BOLD, ACENTO);
    private static final Font FUENTE_ETIQUETA = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, GRIS_TEXTO);
    private static final Font FUENTE_VALOR = new Font(Font.HELVETICA, 10f, Font.NORMAL, Color.BLACK);
    private static final Font FUENTE_VALOR_FUERTE = new Font(Font.HELVETICA, 10.5f, Font.BOLD, Color.BLACK);
    private static final Font FUENTE_TABLA_ENCABEZADO = new Font(Font.HELVETICA, 9.5f, Font.BOLD, BLANCO);
    private static final Font FUENTE_TABLA_CELDA = new Font(Font.HELVETICA, 10f, Font.NORMAL, Color.BLACK);
    private static final Font FUENTE_TOTAL_ETIQUETA = new Font(Font.HELVETICA, 11f, Font.BOLD, GRIS_TEXTO);
    private static final Font FUENTE_TOTAL_VALOR = new Font(Font.HELVETICA, 16f, Font.BOLD, ACENTO);
    private static final Font FUENTE_NOTA = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, GRIS_TEXTO);

    /** Locale es-MX para nombres de mes y formato de fecha/numero. */
    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter FORMATO_PERIODO =
            DateTimeFormatter.ofPattern("MMMM yyyy", ES_MX);
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(ES_MX);

    private static final float MARGEN = 42f;

    /**
     * Genera el PDF del comprobante de renta.
     *
     * @param factura factura de renta a representar; obligatoria.
     * @param empresa Empresa receptora; puede ser {@code null} (offboarding: la
     *                fila ya no existe), en cuyo caso el bloque Receptor se
     *                degrada a mostrar solo el identificador del tenant.
     * @param emisor  datos del emisor (Dess-TI); obligatorios.
     * @return los bytes del PDF (empieza con la firma {@code %PDF}).
     * @throws ReglaNegocioException si faltan la factura o el emisor, o si ocurre
     *                               un error al construir el documento.
     */
    public byte[] generar(FacturaRenta factura, Empresa empresa, EmisorProperties emisor) {
        if (factura == null) {
            throw new ReglaNegocioException("La factura de renta es obligatoria para generar el PDF.");
        }
        if (emisor == null) {
            throw new ReglaNegocioException("Los datos del emisor son obligatorios para generar el PDF.");
        }

        Document documento = new Document(PageSize.A4, MARGEN, MARGEN, MARGEN, MARGEN + 24f);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, salida);
            writer.setPageEvent(new PieDePagina(emisor));
            documento.open();

            documento.add(construirCabecera(factura, emisor));
            documento.add(espacio(14f));
            documento.add(construirEmisorReceptor(factura, empresa, emisor));
            documento.add(espacio(12f));
            documento.add(construirMetadatos(factura));
            documento.add(espacio(16f));
            documento.add(construirTablaConceptos(factura));
            documento.add(espacio(4f));
            documento.add(construirTotal(factura));

            documento.close();
        } catch (DocumentException ex) {
            throw new ReglaNegocioException("No se pudo generar el PDF del comprobante de renta.");
        }
        return salida.toByteArray();
    }

    // ------------------------------------------------------------------
    // Secciones del documento
    // ------------------------------------------------------------------

    /** Banda de cabecera con wordmark del emisor, titulo del documento y folio. */
    private PdfPTable construirCabecera(FacturaRenta factura, EmisorProperties emisor) {
        PdfPTable banda = tablaAncho(new float[] {60f, 40f});
        banda.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        // Columna izquierda: wordmark del emisor + titulo del documento.
        PdfPCell celdaIzq = celdaBanda();
        celdaIzq.addElement(new Paragraph(emisor.nombre(), FUENTE_WORDMARK));
        Paragraph subtitulo = new Paragraph("Comprobante de renta de modulos", FUENTE_TITULO_DOC);
        subtitulo.setSpacingBefore(2f);
        celdaIzq.addElement(subtitulo);
        banda.addCell(celdaIzq);

        // Columna derecha: folio.
        PdfPCell celdaDer = celdaBanda();
        celdaDer.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph etiquetaFolio = new Paragraph("FOLIO",
                new Font(Font.HELVETICA, 8f, Font.NORMAL, BLANCO));
        etiquetaFolio.setAlignment(Element.ALIGN_RIGHT);
        celdaDer.addElement(etiquetaFolio);
        Paragraph folio = new Paragraph(folioCorto(factura), FUENTE_FOLIO);
        folio.setAlignment(Element.ALIGN_RIGHT);
        celdaDer.addElement(folio);
        banda.addCell(celdaDer);

        return banda;
    }

    /** Bloques Emisor y Receptor en dos columnas. */
    private PdfPTable construirEmisorReceptor(FacturaRenta factura, Empresa empresa,
                                              EmisorProperties emisor) {
        PdfPTable tabla = tablaAncho(new float[] {50f, 50f});

        PdfPCell emisorCell = celdaBloque();
        emisorCell.addElement(tituloSeccion("EMISOR"));
        for (Paragraph p : lineasEmisor(emisor)) {
            emisorCell.addElement(p);
        }
        tabla.addCell(emisorCell);

        PdfPCell receptorCell = celdaBloque();
        receptorCell.addElement(tituloSeccion("RECEPTOR"));
        for (Paragraph p : lineasReceptor(factura, empresa)) {
            receptorCell.addElement(p);
        }
        tabla.addCell(receptorCell);

        return tabla;
    }

    /** Fila de metadatos: periodo, fecha de emision y moneda. */
    private PdfPTable construirMetadatos(FacturaRenta factura) {
        PdfPTable tabla = tablaAncho(new float[] {34f, 33f, 33f});
        tabla.addCell(celdaMeta("PERIODO", formatearPeriodo(factura.getPeriodo())));
        tabla.addCell(celdaMeta("FECHA DE EMISION", formatearFechaEmision(factura.getEmitidaEn())));
        tabla.addCell(celdaMeta("MONEDA", seguro(factura.getMonedaCodigo())));
        return tabla;
    }

    /** Tabla de conceptos: una fila por modulo, importe alineado a la derecha. */
    private PdfPTable construirTablaConceptos(FacturaRenta factura) {
        PdfPTable tabla = tablaAncho(new float[] {70f, 30f});

        tabla.addCell(celdaEncabezadoTabla("Modulo", Element.ALIGN_LEFT));
        tabla.addCell(celdaEncabezadoTabla("Importe", Element.ALIGN_RIGHT));

        String moneda = seguro(factura.getMonedaCodigo());
        List<FacturaRentaLinea> lineas = factura.getLineas();
        boolean cebra = false;
        for (FacturaRentaLinea linea : lineas) {
            Color fondo = cebra ? GRIS_CEBRA : BLANCO;
            tabla.addCell(celdaConcepto(seguro(linea.getModuloNombre()), Element.ALIGN_LEFT, fondo));
            tabla.addCell(celdaConcepto(
                    formatearMonto(linea.getPrecioAplicado(), moneda), Element.ALIGN_RIGHT, fondo));
            cebra = !cebra;
        }
        return tabla;
    }

    /** Total prominente alineado a la derecha. */
    private PdfPTable construirTotal(FacturaRenta factura) {
        PdfPTable tabla = tablaAncho(new float[] {62f, 38f});
        String moneda = seguro(factura.getMonedaCodigo());

        PdfPCell vacio = new PdfPCell();
        vacio.setBorder(Rectangle.NO_BORDER);
        tabla.addCell(vacio);

        PdfPCell totalCell = new PdfPCell();
        totalCell.setBorder(Rectangle.TOP);
        totalCell.setBorderColor(ACENTO);
        totalCell.setBorderWidthTop(1.4f);
        totalCell.setPaddingTop(8f);
        totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph etiqueta = new Paragraph("TOTAL", FUENTE_TOTAL_ETIQUETA);
        etiqueta.setAlignment(Element.ALIGN_RIGHT);
        totalCell.addElement(etiqueta);

        Paragraph valor = new Paragraph(formatearMonto(factura.getTotal(), moneda), FUENTE_TOTAL_VALOR);
        valor.setAlignment(Element.ALIGN_RIGHT);
        totalCell.addElement(valor);

        tabla.addCell(totalCell);
        return tabla;
    }

    // ------------------------------------------------------------------
    // Construccion de lineas de texto (defensivas ante nulos)
    // ------------------------------------------------------------------

    private List<Paragraph> lineasEmisor(EmisorProperties emisor) {
        List<Paragraph> lineas = new ArrayList<>();
        lineas.add(valorFuerte(emisor.nombre()));
        agregarSiPresente(lineas, "RFC", emisor.rfc());
        agregarSiPresente(lineas, null, emisor.direccion());
        agregarSiPresente(lineas, null, emisor.email());
        agregarSiPresente(lineas, null, emisor.sitioWeb());
        return lineas;
    }

    private List<Paragraph> lineasReceptor(FacturaRenta factura, Empresa empresa) {
        List<Paragraph> lineas = new ArrayList<>();
        if (empresa == null) {
            // Degradacion controlada: la Empresa ya no existe (offboarding).
            lineas.add(valorFuerte("Empresa (tenant): " + seguro(factura.getTenantId())));
            return lineas;
        }
        lineas.add(valorFuerte(seguro(empresa.getNombre())));
        if (presente(empresa.getNombreComercial())) {
            lineas.add(valor("Nombre comercial: " + empresa.getNombreComercial().strip()));
        }
        agregarSiPresente(lineas, "RFC", empresa.getRfc());
        String direccion = componerDireccion(empresa);
        if (!direccion.isBlank()) {
            lineas.add(valor(direccion));
        }
        agregarSiPresente(lineas, null, empresa.getEmailContacto());
        agregarSiPresente(lineas, "Tel.", empresa.getTelefono());
        return lineas;
    }

    /** Compone la direccion omitiendo las partes vacias. */
    private String componerDireccion(Empresa empresa) {
        List<String> partes = new ArrayList<>();
        agregarParte(partes, empresa.getDireccionCalle());
        agregarParte(partes, empresa.getDireccionCiudad());
        agregarParte(partes, empresa.getDireccionEstado());
        agregarParte(partes, empresa.getDireccionCp());
        agregarParte(partes, empresa.getDireccionPais());
        return String.join(", ", partes);
    }

    private void agregarParte(List<String> partes, String valor) {
        if (presente(valor)) {
            partes.add(valor.strip());
        }
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

    /** Periodo como "Junio 2025" (mes capitalizado, es-MX). */
    String formatearPeriodo(LocalDate periodo) {
        if (periodo == null) {
            return "";
        }
        return capitalizar(periodo.format(FORMATO_PERIODO));
    }

    /** Fecha de emision en formato largo es-MX (zona America/Mexico_City). */
    String formatearFechaEmision(Instant emitidaEn) {
        if (emitidaEn == null) {
            return "";
        }
        return emitidaEn.atZone(ZoneId.of("America/Mexico_City")).toLocalDate().format(FORMATO_FECHA);
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
        // Validacion suave: si el codigo no es una moneda ISO conocida, igual se
        // usa como prefijo (defensivo, sin lanzar excepcion).
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

    private static String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
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

    private PdfPCell celdaConcepto(String texto, int alineacion, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(seguro(texto), FUENTE_TABLA_CELDA));
        celda.setBackgroundColor(fondo);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(6.5f);
        return celda;
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

    /** Folio: primeros 8 caracteres del id en mayusculas. */
    private String folioCorto(FacturaRenta factura) {
        if (factura.getId() == null) {
            return "";
        }
        String id = factura.getId().toString().replace("-", "");
        return id.substring(0, Math.min(8, id.length())).toUpperCase(Locale.ROOT);
    }

    /**
     * Evento de pagina que dibuja el pie: nota legal (NO es CFDI), instante de
     * generacion y numero de pagina. Se ejecuta al finalizar cada pagina.
     */
    private static final class PieDePagina extends PdfPageEventHelper {

        private static final String NOTA_LEGAL =
                "Este documento es un comprobante interno de renta de modulos y NO constituye un "
                        + "Comprobante Fiscal Digital (CFDI); no tiene validez fiscal.";

        private final EmisorProperties emisor;

        private PieDePagina(EmisorProperties emisor) {
            this.emisor = emisor;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document documento) {
            Rectangle pagina = documento.getPageSize();
            float x = documento.leftMargin();
            float ancho = pagina.getWidth() - documento.leftMargin() - documento.rightMargin();
            float y = documento.bottomMargin() - 6f;

            // Nota legal (centrada, ocupa el ancho util).
            Phrase nota = new Phrase(NOTA_LEGAL, FUENTE_NOTA);
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_CENTER,
                    nota, x + ancho / 2f, y + 8f, 0);

            // "Generado el ..." a la izquierda.
            String generado = "Generado el "
                    + java.time.LocalDateTime.now(ZoneId.of("America/Mexico_City"))
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ES_MX));
            Phrase izquierda = new Phrase(generado, FUENTE_NOTA);
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_LEFT,
                    izquierda, x, y - 4f, 0);

            // Numero de pagina a la derecha.
            Phrase derecha = new Phrase("Pagina " + writer.getPageNumber(), FUENTE_NOTA);
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_RIGHT,
                    derecha, x + ancho, y - 4f, 0);
        }
    }
}
