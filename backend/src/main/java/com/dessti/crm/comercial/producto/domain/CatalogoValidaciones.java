package com.dessti.crm.comercial.producto.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion y normalizacion compartidas por las entidades del
 * catalogo de productos ({@link Producto}, {@link ListaPrecios},
 * {@link PrecioProducto}) del submodulo comercial-crm (Req 59). Centraliza las
 * reglas de formato y de rango para evitar duplicarlas y garantizar un
 * comportamiento consistente.
 *
 * <h2>Reglas (Req 59)</h2>
 * <ul>
 *   <li><strong>Nombre del Producto/Lista:</strong> obligatorio, entre 1 y 200
 *       caracteres tras recortar (Req 59.1, 59.2).</li>
 *   <li><strong>Unidad:</strong> obligatoria, hasta 50 caracteres (Req 59.1).</li>
 *   <li><strong>Descripcion del Producto:</strong> obligatoria, hasta 2000
 *       caracteres (Req 59.1, 59.2).</li>
 *   <li><strong>Precio:</strong> obligatorio, dentro del rango cerrado
 *       [0.01, 999,999,999.99], expresado con 2 decimales (redondeo al valor mas
 *       cercano). Un precio fuera de rango se rechaza (Req 59.3, 59.10;
 *       Property 30).</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422), coherente con el resto del dominio.</p>
 */
public final class CatalogoValidaciones {

    /** Longitud maxima del nombre (coincide con VARCHAR(200) de V12). */
    public static final int LONGITUD_MAXIMA_NOMBRE = 200;

    /** Longitud maxima de la unidad (coincide con VARCHAR(50) de V12). */
    public static final int LONGITUD_MAXIMA_UNIDAD = 50;

    /** Longitud maxima de la descripcion (coincide con VARCHAR(2000) de V12). */
    public static final int LONGITUD_MAXIMA_DESCRIPCION = 2000;

    /** Longitud maxima del segmento de la Lista_Precios (coincide con VARCHAR(100)). */
    public static final int LONGITUD_MAXIMA_SEGMENTO = 100;

    /** Escala monetaria del sistema (2 decimales). */
    public static final int ESCALA_MONETARIA = 2;

    /** Precio minimo aceptado (Req 59.3, 59.10). */
    public static final BigDecimal PRECIO_MINIMO = new BigDecimal("0.01");

    /** Precio maximo aceptado (Req 59.3, 59.10). */
    public static final BigDecimal PRECIO_MAXIMO = new BigDecimal("999999999.99");

    private CatalogoValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida y normaliza un nombre obligatorio (Req 59.1, 59.2).
     *
     * @param valor nombre a normalizar.
     * @return el nombre recortado.
     * @throws ReglaNegocioException si es nulo/vacio o excede {@link #LONGITUD_MAXIMA_NOMBRE}.
     */
    public static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre no puede exceder " + LONGITUD_MAXIMA_NOMBRE + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza la unidad obligatoria del Producto (Req 59.1, 59.2).
     *
     * @param valor unidad a normalizar.
     * @return la unidad recortada.
     * @throws ReglaNegocioException si es nula/vacia o excede {@link #LONGITUD_MAXIMA_UNIDAD}.
     */
    public static String normalizarUnidad(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("La unidad del Producto es obligatoria.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_UNIDAD) {
            throw new ReglaNegocioException(
                    "La unidad no puede exceder " + LONGITUD_MAXIMA_UNIDAD + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza la descripcion obligatoria del Producto (Req 59.1, 59.2).
     *
     * @param valor descripcion a normalizar.
     * @return la descripcion recortada.
     * @throws ReglaNegocioException si es nula/vacia o excede {@link #LONGITUD_MAXIMA_DESCRIPCION}.
     */
    public static String normalizarDescripcion(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("La descripcion del Producto es obligatoria.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_DESCRIPCION) {
            throw new ReglaNegocioException(
                    "La descripcion no puede exceder " + LONGITUD_MAXIMA_DESCRIPCION + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Normaliza un texto descriptivo opcional (informacion comercial de apoyo,
     * Req 59.5). Un valor nulo/en blanco se interpreta como ausencia y devuelve
     * {@code null}.
     *
     * @param valor texto a normalizar; puede ser {@code null}.
     * @return el texto recortado, o {@code null} si no se proporciono.
     */
    public static String normalizarTextoOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.strip();
    }

    /**
     * Normaliza un segmento opcional de la Lista_Precios (Req 59.9). Un valor
     * nulo/en blanco representa la lista general (sin segmento) y devuelve
     * {@code null}. La comparacion posterior de segmento se hace sin distinguir
     * mayusculas, por lo que se conserva el valor recortado tal cual.
     *
     * @param valor segmento a normalizar; puede ser {@code null}.
     * @return el segmento recortado, o {@code null} para la lista general.
     * @throws ReglaNegocioException si excede {@link #LONGITUD_MAXIMA_SEGMENTO}.
     */
    public static String normalizarSegmentoOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_SEGMENTO) {
            throw new ReglaNegocioException(
                    "El segmento no puede exceder " + LONGITUD_MAXIMA_SEGMENTO + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida un precio y lo normaliza a 2 decimales (redondeo al valor mas
     * cercano). El precio debe estar dentro del rango cerrado
     * [0.01, 999,999,999.99] (Req 59.3, 59.10; Property 30).
     *
     * <p>El redondeo se aplica <em>antes</em> de la comprobacion de rango, de
     * modo que un valor con mas de 2 decimales se evalua por su representacion
     * monetaria efectiva (por ejemplo, {@code 0.004} redondea a {@code 0.00} y
     * se rechaza).</p>
     *
     * @param valor precio a validar; obligatorio.
     * @return el precio normalizado a escala 2.
     * @throws ReglaNegocioException si es nulo o queda fuera del rango
     *         permitido (422).
     */
    public static BigDecimal validarPrecio(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("El precio del Producto es obligatorio.");
        }
        BigDecimal normalizado = valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(PRECIO_MINIMO) < 0 || normalizado.compareTo(PRECIO_MAXIMO) > 0) {
            throw new ReglaNegocioException(
                    "El precio " + normalizado.toPlainString() + " esta fuera del rango permitido ["
                            + PRECIO_MINIMO.toPlainString() + ", " + PRECIO_MAXIMO.toPlainString() + "].");
        }
        return normalizado;
    }
}
