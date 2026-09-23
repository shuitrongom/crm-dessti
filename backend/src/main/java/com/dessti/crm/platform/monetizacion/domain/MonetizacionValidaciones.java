package com.dessti.crm.platform.monetizacion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion y normalizacion compartidas por la monetizacion de
 * modulos (catalogo de modulos, monedas y precios). Centraliza las reglas de
 * formato de codigo de moneda, clave de modulo y rango de precio para un
 * comportamiento consistente y verificable.
 *
 * <h2>Reglas</h2>
 * <ul>
 *   <li><strong>Codigo de moneda (ISO 4217):</strong> exactamente 3 letras; se
 *       normaliza a mayusculas.</li>
 *   <li><strong>Clave de modulo:</strong> obligatoria, hasta 60 caracteres; se
 *       normaliza a minusculas y sin espacios extremos, coherente con la
 *       normalizacion de {@code plan.modulos_habilitados}.</li>
 *   <li><strong>Precio de modulo:</strong> dentro del rango cerrado
 *       [0.00, 999,999,999.99], escala 2 (redondeo al valor mas cercano). Se
 *       permite 0.00 (modulo gratuito). Un precio fuera de rango se rechaza.</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422).</p>
 */
public final class MonetizacionValidaciones {

    /** Escala monetaria del sistema (2 decimales). */
    public static final int ESCALA_MONETARIA = 2;

    /** Precio minimo aceptado para un modulo (0.00: un modulo puede ser gratuito). */
    public static final BigDecimal PRECIO_MINIMO = new BigDecimal("0.00");

    /** Precio maximo aceptado para un modulo. */
    public static final BigDecimal PRECIO_MAXIMO = new BigDecimal("999999999.99");

    /** Longitud maxima de la clave de modulo (coincide con VARCHAR(60) de V22). */
    public static final int LONGITUD_MAXIMA_CLAVE = 60;

    private static final Pattern PATRON_MONEDA = Pattern.compile("^[A-Z]{3}$");

    private MonetizacionValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida y normaliza un codigo de moneda ISO 4217 (3 letras) a mayusculas.
     *
     * @param valor codigo a normalizar.
     * @return el codigo en mayusculas.
     * @throws ReglaNegocioException si es nulo/vacio o no son 3 letras.
     */
    public static String normalizarCodigoMoneda(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El codigo de moneda es obligatorio.");
        }
        String normalizado = valor.strip().toUpperCase(Locale.ROOT);
        if (!PATRON_MONEDA.matcher(normalizado).matches()) {
            throw new ReglaNegocioException(
                    "El codigo de moneda debe ser ISO 4217 (3 letras, p. ej. MXN, USD, EUR).");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza la clave de un modulo a minusculas y sin espacios
     * extremos (coherente con {@code plan.modulos_habilitados}).
     *
     * @param valor clave a normalizar.
     * @return la clave normalizada.
     * @throws ReglaNegocioException si es nula/vacia o excede el maximo.
     */
    public static String normalizarClaveModulo(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("La clave del modulo es obligatoria.");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        if (normalizado.length() > LONGITUD_MAXIMA_CLAVE) {
            throw new ReglaNegocioException(
                    "La clave del modulo no puede exceder " + LONGITUD_MAXIMA_CLAVE + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida un precio de modulo y lo normaliza a 2 decimales (HALF_UP), dentro
     * del rango cerrado [0.00, 999,999,999.99].
     *
     * @param valor precio a validar; obligatorio.
     * @return el precio normalizado a escala 2.
     * @throws ReglaNegocioException si es nulo o queda fuera del rango.
     */
    public static BigDecimal validarPrecio(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("El precio del modulo es obligatorio.");
        }
        BigDecimal normalizado = valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(PRECIO_MINIMO) < 0 || normalizado.compareTo(PRECIO_MAXIMO) > 0) {
            throw new ReglaNegocioException(
                    "El precio " + normalizado.toPlainString() + " esta fuera del rango permitido ["
                            + PRECIO_MINIMO.toPlainString() + ", " + PRECIO_MAXIMO.toPlainString() + "].");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza un nombre obligatorio con un maximo de caracteres.
     *
     * @param valor  nombre a normalizar.
     * @param maximo longitud maxima permitida.
     * @param campo  nombre del campo para el mensaje de error.
     * @return el nombre recortado.
     * @throws ReglaNegocioException si es nulo/vacio o excede el maximo.
     */
    public static String normalizarNombre(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El " + campo + " es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > maximo) {
            throw new ReglaNegocioException(
                    "El " + campo + " no puede exceder " + maximo + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Normaliza una descripcion opcional (recorte); {@code null}/vacia -&gt; {@code null}.
     *
     * @param valor descripcion; puede ser {@code null}.
     * @return la descripcion recortada o {@code null}.
     */
    public static String normalizarDescripcion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.strip();
    }
}