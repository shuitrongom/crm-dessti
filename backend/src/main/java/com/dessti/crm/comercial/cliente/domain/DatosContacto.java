package com.dessti.crm.comercial.cliente.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion y normalizacion de los datos de contacto compartidas
 * por {@link Cliente} y {@link Contacto} (Req 5.1, 5.2). Centraliza las reglas
 * de formato para evitar duplicarlas y garantizar un comportamiento consistente
 * entre entidades del modulo comercial-crm.
 *
 * <h2>Reglas (Req 5.1, 5.2)</h2>
 * <ul>
 *   <li><strong>Nombre/razon social:</strong> obligatorio, entre 1 y 200
 *       caracteres (tras recortar espacios).</li>
 *   <li><strong>RFC (identificador fiscal):</strong> obligatorio, entre 12 y 13
 *       caracteres tras normalizar a mayusculas; formato alfanumerico del RFC
 *       mexicano (persona moral: 12; persona fisica: 13). Un formato invalido se
 *       rechaza (HTTP 422).</li>
 *   <li><strong>Email:</strong> opcional, pero si se proporciona debe tener un
 *       formato valido y no exceder 320 caracteres.</li>
 *   <li><strong>Telefono:</strong> opcional, pero si se proporciona debe constar
 *       de 10 a 15 digitos.</li>
 *   <li><strong>Al menos un dato de contacto:</strong> el Cliente exige un email
 *       valido o un telefono valido (Req 5.1).</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422), coherente con el resto del dominio.</p>
 */
public final class DatosContacto {

    /** Longitud maxima del nombre/razon social (coincide con VARCHAR(200) de V11). */
    public static final int LONGITUD_MAXIMA_NOMBRE = 200;

    /** Longitud minima del RFC (persona moral). */
    public static final int LONGITUD_MINIMA_RFC = 12;

    /** Longitud maxima del RFC (persona fisica; coincide con VARCHAR(13) de V11). */
    public static final int LONGITUD_MAXIMA_RFC = 13;

    /** Longitud maxima del email (coincide con VARCHAR(320) de V11). */
    public static final int LONGITUD_MAXIMA_EMAIL = 320;

    /** Longitud maxima del nombre comercial (coincide con VARCHAR(200) de V59). */
    public static final int LONGITUD_MAXIMA_NOMBRE_COMERCIAL = 200;

    /** Longitud maxima de la calle de la direccion (coincide con VARCHAR(200) de V59). */
    public static final int LONGITUD_MAXIMA_DIRECCION_CALLE = 200;

    /** Longitud maxima de la ciudad de la direccion (coincide con VARCHAR(120) de V59). */
    public static final int LONGITUD_MAXIMA_DIRECCION_CIUDAD = 120;

    /** Longitud maxima del estado de la direccion (coincide con VARCHAR(120) de V59). */
    public static final int LONGITUD_MAXIMA_DIRECCION_ESTADO = 120;

    /** Longitud maxima del codigo postal de la direccion (coincide con VARCHAR(10) de V59). */
    public static final int LONGITUD_MAXIMA_DIRECCION_CP = 10;

    /** Longitud maxima del pais de la direccion (coincide con VARCHAR(80) de V59). */
    public static final int LONGITUD_MAXIMA_DIRECCION_PAIS = 80;

    /** Longitud maxima de las notas libres (coincide con VARCHAR(1000) de V59). */
    public static final int LONGITUD_MAXIMA_NOTAS = 1000;

    /**
     * Formato del RFC mexicano (ya en mayusculas): 3-4 letras (o &amp;/Ñ) de la
     * clave, 6 digitos de fecha (AAMMDD) y 3 caracteres de homoclave
     * alfanumericos. Admite 12 (moral) o 13 (fisica) caracteres.
     */
    private static final Pattern PATRON_RFC =
            Pattern.compile("^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$");

    /** Formato de email pragmatico (no exhaustivo) suficiente para el Req 5.1. */
    private static final Pattern PATRON_EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Telefono: entre 10 y 15 digitos (Req 5.1). */
    private static final Pattern PATRON_TELEFONO = Pattern.compile("^[0-9]{10,15}$");

    private DatosContacto() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida y normaliza el nombre/razon social (Req 5.1, 5.2).
     *
     * @param valor nombre a normalizar.
     * @return el nombre recortado.
     * @throws ReglaNegocioException si es nulo/vacio o excede el maximo.
     */
    public static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre o razon social es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre no puede exceder " + LONGITUD_MAXIMA_NOMBRE + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza el RFC a mayusculas (Req 5.1, 5.2).
     *
     * @param valor RFC a normalizar.
     * @return el RFC normalizado a mayusculas.
     * @throws ReglaNegocioException si es nulo/vacio, su longitud no esta entre
     *                               12 y 13, o su formato es invalido.
     */
    public static String normalizarRfc(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El identificador fiscal (RFC) es obligatorio.");
        }
        String normalizado = valor.strip().toUpperCase(Locale.ROOT);
        if (normalizado.length() < LONGITUD_MINIMA_RFC
                || normalizado.length() > LONGITUD_MAXIMA_RFC) {
            throw new ReglaNegocioException(
                    "El identificador fiscal (RFC) debe tener entre " + LONGITUD_MINIMA_RFC
                            + " y " + LONGITUD_MAXIMA_RFC + " caracteres.");
        }
        if (!PATRON_RFC.matcher(normalizado).matches()) {
            throw new ReglaNegocioException(
                    "El formato del identificador fiscal (RFC) es invalido.");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza un email opcional (Req 5.1). Un valor nulo/en blanco se
     * interpreta como ausencia de email y devuelve {@code null}.
     *
     * @param valor email a normalizar; puede ser {@code null}.
     * @return el email recortado, o {@code null} si no se proporciono.
     * @throws ReglaNegocioException si el formato es invalido o excede el maximo.
     */
    public static String normalizarEmail(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_EMAIL) {
            throw new ReglaNegocioException(
                    "El correo electronico no puede exceder " + LONGITUD_MAXIMA_EMAIL + " caracteres.");
        }
        if (!PATRON_EMAIL.matcher(normalizado).matches()) {
            throw new ReglaNegocioException("El formato del correo electronico es invalido.");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza un telefono opcional (Req 5.1). Un valor nulo/en blanco
     * se interpreta como ausencia de telefono y devuelve {@code null}.
     *
     * @param valor telefono a normalizar; puede ser {@code null}.
     * @return el telefono recortado (solo digitos), o {@code null} si no se
     *         proporciono.
     * @throws ReglaNegocioException si no consta de 10 a 15 digitos.
     */
    public static String normalizarTelefono(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (!PATRON_TELEFONO.matcher(normalizado).matches()) {
            throw new ReglaNegocioException(
                    "El telefono debe constar de 10 a 15 digitos.");
        }
        return normalizado;
    }

    /**
     * Exige al menos un dato de contacto consistente: email valido o telefono
     * valido (Req 5.1). Debe invocarse con los valores ya normalizados.
     *
     * @param email    email normalizado (puede ser {@code null}).
     * @param telefono telefono normalizado (puede ser {@code null}).
     * @throws ReglaNegocioException si ambos son {@code null}.
     */
    public static void exigirAlMenosUnContacto(String email, String telefono) {
        if (email == null && telefono == null) {
            throw new ReglaNegocioException(
                    "Debe proporcionarse al menos un dato de contacto: un correo "
                            + "electronico valido o un telefono de 10 a 15 digitos.");
        }
    }

    /**
     * Valida y normaliza un texto libre OPCIONAL acotado por longitud maxima
     * (Req 5), sin imponer formato. Un valor nulo/en blanco se interpreta como
     * ausencia de dato y devuelve {@code null}; en otro caso se recortan los
     * espacios y se comprueba que no exceda {@code longitudMaxima}.
     *
     * <p>Se reutiliza para el nombre comercial, los campos de direccion y las
     * notas del Cliente (todos opcionales, ver V59).</p>
     *
     * @param valor          texto a normalizar; puede ser {@code null}.
     * @param etiqueta       nombre legible del campo para el mensaje de error
     *                       (p. ej. "nombre comercial").
     * @param longitudMaxima longitud maxima permitida tras recortar.
     * @return el texto recortado, o {@code null} si no se proporciono.
     * @throws ReglaNegocioException si excede {@code longitudMaxima} (HTTP 422).
     */
    public static String normalizarTextoOpcional(String valor, String etiqueta, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > longitudMaxima) {
            throw new ReglaNegocioException(
                    "El campo " + etiqueta + " no puede exceder " + longitudMaxima + " caracteres.");
        }
        return normalizado;
    }
}
