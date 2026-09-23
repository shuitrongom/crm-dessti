package com.dessti.crm.rhnomina.empleado.domain;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion y normalizacion de los datos obligatorios del
 * {@link Empleado} (Req 40.1, 40.2). Centraliza las reglas de formato del RFC,
 * la CURP y el NSS del IMSS para evitar duplicarlas y garantizar un
 * comportamiento consistente, siguiendo el patron de
 * {@code DatosContacto} del modulo comercial-crm.
 *
 * <h2>Reglas (Req 40.1, 40.2)</h2>
 * <ul>
 *   <li><strong>Nombre:</strong> obligatorio, entre 1 y 200 caracteres (tras
 *       recortar espacios).</li>
 *   <li><strong>RFC (persona fisica):</strong> obligatorio, exactamente 13
 *       caracteres tras normalizar a mayusculas; formato del RFC mexicano de
 *       persona fisica: 4 letras (o &amp;/Ñ) de la clave, 6 digitos de fecha
 *       (AAMMDD) y 3 caracteres de homoclave alfanumericos. Un Empleado es
 *       siempre persona fisica.</li>
 *   <li><strong>CURP:</strong> obligatoria, exactamente 18 caracteres; formato
 *       oficial de la CURP mexicana.</li>
 *   <li><strong>NSS (IMSS):</strong> obligatorio, exactamente 11 digitos.</li>
 *   <li><strong>Fecha de ingreso:</strong> obligatoria.</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422) indicando el campo invalido/faltante,
 * coherente con el resto del dominio (Req 40.2).</p>
 */
public final class ValidacionesEmpleado {

    /** Longitud maxima del nombre (coincide con VARCHAR(200) de V32). */
    public static final int LONGITUD_MAXIMA_NOMBRE = 200;

    /** Longitud exacta del RFC de persona fisica (coincide con VARCHAR(13) de V32). */
    public static final int LONGITUD_RFC = 13;

    /** Longitud exacta de la CURP (coincide con VARCHAR(18) de V32). */
    public static final int LONGITUD_CURP = 18;

    /** Longitud exacta del NSS del IMSS (coincide con VARCHAR(11) de V32). */
    public static final int LONGITUD_NSS = 11;

    /**
     * Formato del RFC de persona fisica (ya en mayusculas): 4 letras (o &amp;/Ñ),
     * 6 digitos de fecha (AAMMDD) y 3 caracteres de homoclave alfanumericos (13).
     */
    private static final Pattern PATRON_RFC =
            Pattern.compile("^[A-ZÑ&]{4}[0-9]{6}[A-Z0-9]{3}$");

    /**
     * Formato oficial de la CURP (ya en mayusculas): 4 letras, 6 digitos de fecha
     * (AAMMDD), sexo H/M, 5 letras de entidad/consonantes, 1 caracter alfanumerico
     * (homoclave) y 1 digito verificador (18).
     */
    private static final Pattern PATRON_CURP =
            Pattern.compile("^[A-Z]{4}[0-9]{6}[HM][A-Z]{5}[A-Z0-9][0-9]$");

    /** NSS del IMSS: exactamente 11 digitos (Req 40.1, 40.2). */
    private static final Pattern PATRON_NSS = Pattern.compile("^[0-9]{11}$");

    private ValidacionesEmpleado() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida y normaliza el nombre del Empleado (Req 40.1, 40.2).
     *
     * @param valor nombre a normalizar.
     * @return el nombre recortado.
     * @throws ReglaNegocioException si es nulo/vacio o excede el maximo.
     */
    public static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre del Empleado es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre no puede exceder " + LONGITUD_MAXIMA_NOMBRE + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza el RFC del Empleado a mayusculas (Req 40.1, 40.2). El
     * Empleado es persona fisica, por lo que el RFC debe tener 13 caracteres.
     *
     * @param valor RFC a normalizar.
     * @return el RFC normalizado a mayusculas.
     * @throws ReglaNegocioException si es nulo/vacio, su longitud no es 13 o su
     *                               formato es invalido.
     */
    public static String normalizarRfc(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El RFC del Empleado es obligatorio.");
        }
        String normalizado = valor.strip().toUpperCase(Locale.ROOT);
        if (normalizado.length() != LONGITUD_RFC || !PATRON_RFC.matcher(normalizado).matches()) {
            throw new ReglaNegocioException("El formato del RFC del Empleado es invalido.");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza la CURP del Empleado a mayusculas (Req 40.1, 40.2).
     *
     * @param valor CURP a normalizar.
     * @return la CURP normalizada a mayusculas.
     * @throws ReglaNegocioException si es nula/vacia, su longitud no es 18 o su
     *                               formato es invalido.
     */
    public static String normalizarCurp(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("La CURP del Empleado es obligatoria.");
        }
        String normalizado = valor.strip().toUpperCase(Locale.ROOT);
        if (normalizado.length() != LONGITUD_CURP || !PATRON_CURP.matcher(normalizado).matches()) {
            throw new ReglaNegocioException("El formato de la CURP del Empleado es invalido.");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza el NSS del IMSS del Empleado (Req 40.1, 40.2).
     *
     * @param valor NSS a normalizar.
     * @return el NSS recortado (11 digitos).
     * @throws ReglaNegocioException si es nulo/vacio o no consta de 11 digitos.
     */
    public static String normalizarNss(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El NSS (IMSS) del Empleado es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() != LONGITUD_NSS || !PATRON_NSS.matcher(normalizado).matches()) {
            throw new ReglaNegocioException(
                    "El NSS (IMSS) del Empleado debe constar de " + LONGITUD_NSS + " digitos.");
        }
        return normalizado;
    }

    /**
     * Exige la fecha de ingreso del Empleado (Req 40.1, 40.2).
     *
     * @param valor fecha de ingreso.
     * @return la misma fecha si es valida.
     * @throws ReglaNegocioException si es {@code null}.
     */
    public static LocalDate exigirFechaIngreso(LocalDate valor) {
        if (valor == null) {
            throw new ReglaNegocioException("La fecha de ingreso del Empleado es obligatoria.");
        }
        return valor;
    }
}
