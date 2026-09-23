package com.dessti.crm.comercial.oportunidad.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion y normalizacion de la {@link Oportunidad} (Req 14.1).
 * Centraliza las reglas de formato del titulo y de rango del valor estimado para
 * evitar dispersarlas y garantizar un comportamiento consistente.
 *
 * <h2>Reglas (Req 14.1)</h2>
 * <ul>
 *   <li><strong>Titulo:</strong> obligatorio, entre 1 y 200 caracteres tras
 *       recortar (coincide con VARCHAR(200) de V13).</li>
 *   <li><strong>Valor estimado:</strong> obligatorio, dentro del rango cerrado
 *       [0.01, 999,999,999.99] en la moneda unica del sistema, expresado con 2
 *       decimales (redondeo <em>half-up</em>). Un valor fuera de rango se
 *       rechaza (Req 14.1).</li>
 * </ul>
 *
 * <p>El rango monetario y el redondeo (escala 2, HALF_UP) son <em>identicos</em>
 * a los aplicados por {@code CatalogoValidaciones.validarPrecio} en el submodulo
 * de Productos, manteniendo una aritmetica monetaria uniforme en todo el
 * contexto comercial. Las violaciones se senalan con {@link ReglaNegocioException}
 * (HTTP 422), coherente con el resto del dominio.</p>
 *
 * <p>Clase de utilidad no instanciable.</p>
 */
public final class OportunidadValidaciones {

    /** Longitud maxima del titulo (coincide con VARCHAR(200) de V13). */
    public static final int LONGITUD_MAXIMA_TITULO = 200;

    /** Escala monetaria del sistema (2 decimales). */
    public static final int ESCALA_MONETARIA = 2;

    /** Valor estimado minimo aceptado (Req 14.1). */
    public static final BigDecimal VALOR_MINIMO = new BigDecimal("0.01");

    /** Valor estimado maximo aceptado (Req 14.1). */
    public static final BigDecimal VALOR_MAXIMO = new BigDecimal("999999999.99");

    private OportunidadValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida y normaliza el titulo obligatorio de la Oportunidad (Req 14.1).
     *
     * @param valor titulo a normalizar.
     * @return el titulo recortado.
     * @throws ReglaNegocioException si es nulo/vacio o excede
     *         {@link #LONGITUD_MAXIMA_TITULO}.
     */
    public static String normalizarTitulo(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El titulo de la Oportunidad es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_TITULO) {
            throw new ReglaNegocioException(
                    "El titulo no puede exceder " + LONGITUD_MAXIMA_TITULO + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida el valor estimado y lo normaliza a 2 decimales (redondeo half-up).
     * Debe estar dentro del rango cerrado [0.01, 999,999,999.99] (Req 14.1).
     *
     * <p>El redondeo se aplica <em>antes</em> de la comprobacion de rango, de
     * modo que un valor con mas de 2 decimales se evalua por su representacion
     * monetaria efectiva (por ejemplo, {@code 0.004} redondea a {@code 0.00} y
     * se rechaza).</p>
     *
     * @param valor valor estimado a validar; obligatorio.
     * @return el valor normalizado a escala 2.
     * @throws ReglaNegocioException si es nulo o queda fuera del rango permitido
     *         (422, Req 14.1).
     */
    public static BigDecimal validarValorEstimado(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("El valor estimado de la Oportunidad es obligatorio.");
        }
        BigDecimal normalizado = valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(VALOR_MINIMO) < 0 || normalizado.compareTo(VALOR_MAXIMO) > 0) {
            throw new ReglaNegocioException(
                    "El valor estimado " + normalizado.toPlainString()
                            + " esta fuera del rango permitido [" + VALOR_MINIMO.toPlainString()
                            + ", " + VALOR_MAXIMO.toPlainString() + "].");
        }
        return normalizado;
    }
}
