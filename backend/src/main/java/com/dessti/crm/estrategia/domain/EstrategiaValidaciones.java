package com.dessti.crm.estrategia.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion, normalizacion y aritmetica del submodulo de estrategia
 * ({@link ObjetivoEstrategico}, {@link ResultadoClave}, {@link EsenciaEmpresa};
 * Req 58). Centraliza las reglas de rango, longitud y el redondeo <em>half-up</em>
 * del avance y del peso para evitar duplicarlas y garantizar un comportamiento
 * consistente y verificable (Property 29). Sigue el patron de
 * {@code CotizacionValidaciones}.
 *
 * <h2>Reglas (Req 58)</h2>
 * <ul>
 *   <li><strong>Nombre / responsable / meta del objetivo:</strong> obligatorios,
 *       no en blanco tras recortar, y acotados a la longitud de su columna en V38
 *       (Req 58.2, 58.3).</li>
 *   <li><strong>Avance:</strong> porcentaje en el rango cerrado [0, 100] con escala
 *       2; nunca excede 100 (Req 58.9; Property 29).</li>
 *   <li><strong>Peso de un resultado clave:</strong> en el rango (0, 100], escala 2
 *       (Req 58.8).</li>
 *   <li><strong>Valores objetivo/actual de un resultado clave:</strong> escala 4,
 *       no negativos; el valor objetivo debe ser estrictamente positivo (Req 58.8).</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422), coherente con el resto del dominio.</p>
 */
public final class EstrategiaValidaciones {

    /** Escala (decimales) del porcentaje de avance y del peso (Req 58.8, 58.9). */
    public static final int ESCALA_PORCENTAJE = 2;

    /** Escala (decimales) de las metricas de un resultado clave (Req 58.8). */
    public static final int ESCALA_METRICA = 4;

    /** Avance minimo posible (Req 58.9). */
    public static final BigDecimal AVANCE_MINIMO = BigDecimal.ZERO.setScale(ESCALA_PORCENTAJE);

    /** Avance maximo posible; nunca se excede (Req 58.9; Property 29). */
    public static final BigDecimal AVANCE_MAXIMO = new BigDecimal("100").setScale(ESCALA_PORCENTAJE);

    /** Peso maximo de un resultado clave (Req 58.8). */
    public static final BigDecimal PESO_MAXIMO = new BigDecimal("100").setScale(ESCALA_PORCENTAJE);

    /** Longitud maxima del nombre del objetivo (VARCHAR(200) de V38). */
    public static final int LONGITUD_NOMBRE = 200;

    /** Longitud maxima del responsable del objetivo (VARCHAR(200) de V38). */
    public static final int LONGITUD_RESPONSABLE = 200;

    /** Longitud maxima de la meta del objetivo (VARCHAR(500) de V38). */
    public static final int LONGITUD_META = 500;

    /** Longitud maxima de la descripcion de un resultado clave (VARCHAR(300) de V38). */
    public static final int LONGITUD_DESCRIPCION_RC = 300;

    private EstrategiaValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida y normaliza un texto obligatorio (nombre, responsable, meta,
     * descripcion) recortando espacios y comprobando la longitud maxima (Req 58.2,
     * 58.3).
     *
     * @param val             valor a normalizar.
     * @param nombreCampo     nombre del campo, para el mensaje del error (Req 58.3).
     * @param longitudMaxima  longitud maxima permitida.
     * @return el texto recortado.
     * @throws ReglaNegocioException si es nulo/blanco (nombrando el campo faltante,
     *         Req 58.3) o excede {@code longitudMaxima} (422).
     */
    public static String normalizarObligatorio(String val, String nombreCampo, int longitudMaxima) {
        if (val == null || val.isBlank()) {
            throw new ReglaNegocioException("El campo '" + nombreCampo + "' es obligatorio.");
        }
        String normalizado = val.strip();
        if (normalizado.length() > longitudMaxima) {
            throw new ReglaNegocioException(
                    "El campo '" + nombreCampo + "' no puede exceder " + longitudMaxima
                            + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Normaliza un porcentaje de avance a escala 2 y lo <strong>acota</strong> al
     * rango cerrado [0, 100] (Req 58.9; Property 29): un valor menor a 0 se lleva a
     * 0 y uno mayor a 100 se lleva a 100, de modo que el avance nunca excede 100%.
     *
     * @param valor porcentaje a normalizar; obligatorio.
     * @return el porcentaje a escala 2 acotado a [0, 100].
     * @throws ReglaNegocioException si el valor es nulo (422).
     */
    public static BigDecimal acotarAvance(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("El avance es obligatorio.");
        }
        BigDecimal normalizado = valor.setScale(ESCALA_PORCENTAJE, RoundingMode.HALF_UP);
        if (normalizado.compareTo(AVANCE_MINIMO) < 0) {
            return AVANCE_MINIMO;
        }
        if (normalizado.compareTo(AVANCE_MAXIMO) > 0) {
            return AVANCE_MAXIMO;
        }
        return normalizado;
    }

    /**
     * Valida el peso de un resultado clave dentro del rango (0, 100], escala 2
     * (Req 58.8). El peso debe ser estrictamente positivo para que la ponderacion
     * tenga sentido.
     *
     * @param valor peso a validar; obligatorio.
     * @return el peso normalizado a escala 2.
     * @throws ReglaNegocioException si es nulo o queda fuera de (0, 100] (422).
     */
    public static BigDecimal validarPeso(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("El peso del resultado clave es obligatorio.");
        }
        BigDecimal normalizado = valor.setScale(ESCALA_PORCENTAJE, RoundingMode.HALF_UP);
        if (normalizado.compareTo(BigDecimal.ZERO) <= 0
                || normalizado.compareTo(PESO_MAXIMO) > 0) {
            throw new ReglaNegocioException(
                    "El peso " + normalizado.toPlainString()
                            + " del resultado clave debe estar en el rango (0, 100].");
        }
        return normalizado;
    }

    /**
     * Valida el valor objetivo (meta medible) de un resultado clave: escala 4 y
     * estrictamente positivo (Req 58.8), pues el porcentaje de cumplimiento se
     * calcula como {@code valorActual / valorObjetivo}.
     *
     * @param valor valor objetivo a validar; obligatorio.
     * @return el valor objetivo normalizado a escala 4.
     * @throws ReglaNegocioException si es nulo o no positivo (422).
     */
    public static BigDecimal validarValorObjetivo(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("El valor objetivo del resultado clave es obligatorio.");
        }
        BigDecimal normalizado = valor.setScale(ESCALA_METRICA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReglaNegocioException(
                    "El valor objetivo del resultado clave debe ser estrictamente positivo.");
        }
        return normalizado;
    }

    /**
     * Valida el valor actual de un resultado clave: escala 4 y no negativo
     * (Req 58.8).
     *
     * @param valor valor actual a validar; obligatorio.
     * @return el valor actual normalizado a escala 4.
     * @throws ReglaNegocioException si es nulo o negativo (422).
     */
    public static BigDecimal validarValorActual(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("El valor actual del resultado clave es obligatorio.");
        }
        BigDecimal normalizado = valor.setScale(ESCALA_METRICA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(BigDecimal.ZERO) < 0) {
            throw new ReglaNegocioException(
                    "El valor actual del resultado clave no puede ser negativo.");
        }
        return normalizado;
    }
}
