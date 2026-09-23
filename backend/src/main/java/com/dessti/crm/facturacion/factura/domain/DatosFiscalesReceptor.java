package com.dessti.crm.facturacion.factura.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Datos fiscales del receptor de una Factura (CFDI 4.0), validados y normalizados
 * (Req 34.1, 34.3). Objeto de valor inmutable del dominio de facturacion.
 *
 * <h2>Reglas de validacion (Req 34.3)</h2>
 * <ul>
 *   <li><strong>RFC:</strong> obligatorio; formato del SAT
 *       {@code /^[A-ZÑ&]{3,4}\d{6}[A-Z0-9]{3}$/} (12 caracteres persona moral, 13
 *       persona fisica). Se normaliza a mayusculas.</li>
 *   <li><strong>Nombre/razon social:</strong> obligatorio, hasta 300 caracteres.</li>
 *   <li><strong>Codigo postal:</strong> obligatorio, exactamente 5 digitos.</li>
 *   <li><strong>Regimen fiscal:</strong> obligatorio (clave del catalogo del SAT,
 *       hasta 5 caracteres).</li>
 *   <li><strong>Uso de CFDI:</strong> obligatorio (clave del catalogo del SAT,
 *       hasta 4 caracteres).</li>
 * </ul>
 *
 * <p>Un dato faltante o con formato invalido se rechaza con
 * {@link ReglaNegocioException} (422) indicando el dato afectado, de modo que la
 * emision no cree la Factura (Req 34.3).</p>
 *
 * @param rfc            RFC del receptor, ya en mayusculas (Req 34.1).
 * @param nombre         nombre o razon social del receptor (Req 34.1).
 * @param codigoPostal   codigo postal del domicilio fiscal (5 digitos, Req 34.1).
 * @param regimenFiscal  clave del regimen fiscal del receptor (Req 34.1).
 * @param usoCfdi        clave del uso de CFDI (Req 34.1).
 */
public record DatosFiscalesReceptor(
        String rfc,
        String nombre,
        String codigoPostal,
        String regimenFiscal,
        String usoCfdi) {

    /** Longitud maxima del nombre/razon social (coincide con VARCHAR(300) de V30). */
    public static final int LONGITUD_MAXIMA_NOMBRE = 300;

    /** Longitud maxima de la clave de regimen fiscal (VARCHAR(5) de V30). */
    public static final int LONGITUD_MAXIMA_REGIMEN = 5;

    /** Longitud maxima de la clave de uso de CFDI (VARCHAR(4) de V30). */
    public static final int LONGITUD_MAXIMA_USO_CFDI = 4;

    /** Patron de RFC del SAT: 3-4 letras (incluye N y &amp;), 6 digitos y 3 alfanumericos. */
    private static final Pattern PATRON_RFC =
            Pattern.compile("^[A-ZN&]{3,4}\\d{6}[A-Z0-9]{3}$");

    /** Patron de codigo postal mexicano: exactamente 5 digitos. */
    private static final Pattern PATRON_CP = Pattern.compile("^\\d{5}$");

    /**
     * Valida y normaliza los datos fiscales del receptor, o los rechaza (Req 34.3).
     *
     * @param rfc           RFC del receptor; obligatorio y con formato del SAT.
     * @param nombre        nombre o razon social; obligatorio.
     * @param codigoPostal  codigo postal; obligatorio (5 digitos).
     * @param regimenFiscal clave del regimen fiscal; obligatoria.
     * @param usoCfdi       clave del uso de CFDI; obligatoria.
     * @return el objeto de valor validado y normalizado.
     * @throws ReglaNegocioException si falta un dato o su formato es invalido (422).
     */
    public static DatosFiscalesReceptor validar(String rfc, String nombre, String codigoPostal,
                                                String regimenFiscal, String usoCfdi) {
        String rfcNormalizado = normalizarRfc(rfc);
        String nombreNormalizado = normalizarNombre(nombre);
        String cpNormalizado = normalizarCodigoPostal(codigoPostal);
        String regimenNormalizado = normalizarClave(
                regimenFiscal, "regimen fiscal", LONGITUD_MAXIMA_REGIMEN);
        String usoNormalizado = normalizarClave(usoCfdi, "uso de CFDI", LONGITUD_MAXIMA_USO_CFDI);
        return new DatosFiscalesReceptor(
                rfcNormalizado, nombreNormalizado, cpNormalizado, regimenNormalizado, usoNormalizado);
    }

    private static String normalizarRfc(String rfc) {
        if (rfc == null || rfc.isBlank()) {
            throw new ReglaNegocioException("El RFC del receptor es obligatorio.");
        }
        String normalizado = rfc.strip().toUpperCase(Locale.ROOT);
        if (!PATRON_RFC.matcher(normalizado).matches()) {
            throw new ReglaNegocioException("El RFC del receptor tiene un formato invalido: " + rfc);
        }
        return normalizado;
    }

    private static String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre o razon social del receptor es obligatorio.");
        }
        String normalizado = nombre.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre del receptor no puede exceder " + LONGITUD_MAXIMA_NOMBRE + " caracteres.");
        }
        return normalizado;
    }

    private static String normalizarCodigoPostal(String codigoPostal) {
        if (codigoPostal == null || codigoPostal.isBlank()) {
            throw new ReglaNegocioException("El codigo postal del receptor es obligatorio.");
        }
        String normalizado = codigoPostal.strip();
        if (!PATRON_CP.matcher(normalizado).matches()) {
            throw new ReglaNegocioException(
                    "El codigo postal del receptor debe tener 5 digitos: " + codigoPostal);
        }
        return normalizado;
    }

    private static String normalizarClave(String valor, String etiqueta, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("La clave de " + etiqueta + " del receptor es obligatoria.");
        }
        String normalizado = valor.strip().toUpperCase(Locale.ROOT);
        if (normalizado.length() > longitudMaxima) {
            throw new ReglaNegocioException(
                    "La clave de " + etiqueta + " no puede exceder " + longitudMaxima + " caracteres.");
        }
        return normalizado;
    }
}
