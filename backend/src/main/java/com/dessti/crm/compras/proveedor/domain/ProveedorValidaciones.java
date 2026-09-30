package com.dessti.crm.compras.proveedor.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion y normalizacion de los datos de un {@link Proveedor}
 * (Req 29). Centraliza las reglas de formato del nombre/razon social, del
 * identificador fiscal (RFC) y de los datos de contacto, para evitar duplicarlas
 * y garantizar un comportamiento consistente y verificable en el submodulo de
 * Proveedores. Es analoga a {@code DatosContacto} del submodulo de Clientes, con
 * el que comparte las reglas de contacto.
 *
 * <h2>Reglas (Req 29.1)</h2>
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
 *   <li><strong>Al menos un dato de contacto:</strong> el Proveedor exige un email
 *       valido o un telefono valido (Req 29.1).</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422), coherente con el resto del dominio.</p>
 */
public final class ProveedorValidaciones {

    /** Longitud maxima del nombre/razon social (coincide con VARCHAR(200) de V28). */
    public static final int LONGITUD_MAXIMA_NOMBRE = 200;

    /** Longitud minima del RFC (persona moral). */
    public static final int LONGITUD_MINIMA_RFC = 12;

    /** Longitud maxima del RFC (persona fisica; coincide con VARCHAR(20) de V28). */
    public static final int LONGITUD_MAXIMA_RFC = 13;

    /** Longitud maxima del email (coincide con VARCHAR(320) de V28). */
    public static final int LONGITUD_MAXIMA_EMAIL = 320;

    /** Longitud maxima de la persona de contacto (coincide con VARCHAR(200) de V89). */
    public static final int LONGITUD_MAXIMA_PERSONA_CONTACTO = 200;

    /** Longitud maxima del regimen fiscal (coincide con VARCHAR(10) de V89). */
    public static final int LONGITUD_MAXIMA_REGIMEN_FISCAL = 10;

    /** Longitud maxima de la calle del domicilio (coincide con VARCHAR(300) de V89). */
    public static final int LONGITUD_MAXIMA_CALLE = 300;

    /** Longitud maxima de ciudad y estado del domicilio (coincide con VARCHAR(150) de V89). */
    public static final int LONGITUD_MAXIMA_LOCALIDAD = 150;

    /** Tope de dias de credito admitido (defensa razonable de negocio). */
    public static final int DIAS_CREDITO_MAXIMO = 365;

    /** Codigo postal mexicano: exactamente 5 digitos (Req 29, V89). */
    private static final Pattern PATRON_CODIGO_POSTAL = Pattern.compile("^[0-9]{5}$");

    /**
     * Formato del RFC mexicano (ya en mayusculas): 3-4 letras (o &amp;/Ñ) de la
     * clave, 6 digitos de fecha (AAMMDD) y 3 caracteres de homoclave
     * alfanumericos. Admite 12 (moral) o 13 (fisica) caracteres. Identico al
     * patron de {@code DatosContacto}.
     */
    private static final Pattern PATRON_RFC =
            Pattern.compile("^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$");

    /** Formato de email pragmatico (no exhaustivo) suficiente para el Req 29.1. */
    private static final Pattern PATRON_EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Telefono: entre 10 y 15 digitos (Req 29.1). */
    private static final Pattern PATRON_TELEFONO = Pattern.compile("^[0-9]{10,15}$");

    private ProveedorValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida y normaliza el nombre/razon social del Proveedor (Req 29.1).
     *
     * @param valor nombre a normalizar.
     * @return el nombre recortado.
     * @throws ReglaNegocioException si es nulo/vacio o excede el maximo (422).
     */
    public static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre o razon social del Proveedor es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre no puede exceder " + LONGITUD_MAXIMA_NOMBRE + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza el RFC a mayusculas (Req 29.1).
     *
     * @param valor RFC a normalizar.
     * @return el RFC normalizado a mayusculas.
     * @throws ReglaNegocioException si es nulo/vacio, su longitud no esta entre
     *                               12 y 13, o su formato es invalido (422).
     */
    public static String normalizarRfc(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El identificador fiscal (RFC) del Proveedor es obligatorio.");
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
     * Valida y normaliza un email opcional (Req 29.1). Un valor nulo/en blanco se
     * interpreta como ausencia de email y devuelve {@code null}.
     *
     * @param valor email a normalizar; puede ser {@code null}.
     * @return el email recortado, o {@code null} si no se proporciono.
     * @throws ReglaNegocioException si el formato es invalido o excede el maximo (422).
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
     * Valida y normaliza un telefono opcional (Req 29.1). Un valor nulo/en blanco
     * se interpreta como ausencia de telefono y devuelve {@code null}.
     *
     * @param valor telefono a normalizar; puede ser {@code null}.
     * @return el telefono recortado (solo digitos), o {@code null} si no se
     *         proporciono.
     * @throws ReglaNegocioException si no consta de 10 a 15 digitos (422).
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
     * valido (Req 29.1). Debe invocarse con los valores ya normalizados.
     *
     * @param email    email normalizado (puede ser {@code null}).
     * @param telefono telefono normalizado (puede ser {@code null}).
     * @throws ReglaNegocioException si ambos son {@code null} (422).
     */
    public static void exigirAlMenosUnContacto(String email, String telefono) {
        if (email == null && telefono == null) {
            throw new ReglaNegocioException(
                    "Debe proporcionarse al menos un dato de contacto del Proveedor: un correo "
                            + "electronico valido o un telefono de 10 a 15 digitos.");
        }
    }

    /**
     * Normaliza un texto libre OPCIONAL recortando espacios; un valor nulo/en
     * blanco se interpreta como ausencia y devuelve {@code null}. Rechaza (422) si
     * excede el maximo indicado. Reutilizable para persona de contacto, calle,
     * ciudad y estado del domicilio (Req 29, V89).
     *
     * @param valor   texto a normalizar; puede ser {@code null}.
     * @param maximo  longitud maxima permitida.
     * @param etiqueta nombre del campo para el mensaje de error.
     * @return el texto recortado, o {@code null} si no se proporciono.
     * @throws ReglaNegocioException si excede el maximo (422).
     */
    public static String normalizarTextoOpcional(String valor, int maximo, String etiqueta) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > maximo) {
            throw new ReglaNegocioException(etiqueta + " no puede exceder " + maximo + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Normaliza la clave del regimen fiscal OPCIONAL (Req 29, V89): recorta, pasa a
     * mayusculas y acota a 10 caracteres. Nulo/blanco = ausencia.
     *
     * @param valor clave del regimen fiscal; puede ser {@code null}.
     * @return la clave normalizada en mayusculas, o {@code null}.
     * @throws ReglaNegocioException si excede el maximo (422).
     */
    public static String normalizarRegimenFiscal(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip().toUpperCase(Locale.ROOT);
        if (normalizado.length() > LONGITUD_MAXIMA_REGIMEN_FISCAL) {
            throw new ReglaNegocioException(
                    "El regimen fiscal no puede exceder " + LONGITUD_MAXIMA_REGIMEN_FISCAL + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida los dias de credito OPCIONALES (Req 29, V89): si se informan, no
     * pueden ser negativos ni exceder el tope de negocio. Nulo = sin condiciones
     * de credito (pago de contado).
     *
     * @param valor dias de credito; puede ser {@code null}.
     * @return el valor si es valido, o {@code null}.
     * @throws ReglaNegocioException si es negativo o excede el tope (422).
     */
    public static Integer normalizarDiasCredito(Integer valor) {
        if (valor == null) {
            return null;
        }
        if (valor < 0) {
            throw new ReglaNegocioException("Los dias de credito no pueden ser negativos.");
        }
        if (valor > DIAS_CREDITO_MAXIMO) {
            throw new ReglaNegocioException(
                    "Los dias de credito no pueden exceder " + DIAS_CREDITO_MAXIMO + " dias.");
        }
        return valor;
    }

    /**
     * Valida y normaliza el codigo postal OPCIONAL (Req 29, V89): si se informa,
     * debe constar de exactamente 5 digitos. Nulo/blanco = ausencia.
     *
     * @param valor codigo postal; puede ser {@code null}.
     * @return el codigo postal recortado, o {@code null}.
     * @throws ReglaNegocioException si no consta de 5 digitos (422).
     */
    public static String normalizarCodigoPostal(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (!PATRON_CODIGO_POSTAL.matcher(normalizado).matches()) {
            throw new ReglaNegocioException("El codigo postal debe constar de 5 digitos.");
        }
        return normalizado;
    }
}
