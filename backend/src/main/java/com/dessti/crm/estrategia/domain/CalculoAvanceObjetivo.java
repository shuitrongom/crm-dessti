package com.dessti.crm.estrategia.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Funcion <strong>pura</strong> del dominio que calcula el avance de un
 * {@link ObjetivoEstrategico} como el <strong>porcentaje ponderado</strong> de
 * cumplimiento de sus resultados clave (Req 58.8), acotado a [0, 100] de modo que
 * <strong>nunca exceda 100%</strong> (Req 58.9). No depende de Spring ni de JPA:
 * recibe la lista de {@link ResultadoClaveValor} y devuelve el avance, por lo que
 * es completamente determinista y unitariamente comprobable (Property 29,
 * tarea 37.2). Espeja el estilo de {@code DerivacionEstadoProyecto} (bloque 22).
 *
 * <h2>Regla de calculo (Req 58.8, 58.9; Property 29)</h2>
 * <p>Para cada resultado clave se calcula su <em>cumplimiento</em> como la fraccion
 * {@code valorActual / valorObjetivo} <strong>acotada a [0, 1]</strong> (un valor
 * actual que supere el objetivo cuenta como 1, es decir 100% de ese resultado;
 * nunca aporta mas de su peso). El avance del objetivo es:</p>
 * <pre>
 *   avance = round( (Î£ peso_i Â· cumplimiento_i) / (Î£ peso_i) Â· 100 , 2 )
 * </pre>
 * acotado finalmente a [0, 100]. Como cada {@code cumplimiento_i âˆˆ [0, 1]} y los
 * pesos son positivos, el promedio ponderado esta en [0, 1] y el avance en
 * [0, 100]: <strong>por construccion el avance nunca excede 100%</strong>
 * (Req 58.9), incluso si algun {@code valorActual} es mucho mayor que su
 * {@code valorObjetivo}.
 *
 * <h2>Casos borde documentados</h2>
 * <ul>
 *   <li><strong>Sin resultados clave</strong> (lista vacia): el avance ponderado es
 *       0. En ese caso el avance del objetivo lo gobierna el valor actualizado
 *       manualmente (Req 58.4); ver {@link ObjetivoEstrategico}.</li>
 *   <li><strong>valorObjetivo &lt;= 0</strong>: no es una meta medible valida; su
 *       cumplimiento se trata como 0 (regla conservadora) para no dividir por cero
 *       ni inflar el avance. En la practica la validacion de dominio exige
 *       {@code valorObjetivo > 0} (ver {@link EstrategiaValidaciones}), de modo que
 *       este guardado solo protege la funcion pura ante datos degenerados.</li>
 *   <li><strong>peso &lt;= 0</strong> en todos los resultados: si la suma de pesos
 *       no es positiva, el avance ponderado es 0 (evita division por cero).</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable.</p>
 */
public final class CalculoAvanceObjetivo {

    /** Precision intermedia amplia para la division ponderada antes del redondeo final. */
    private static final MathContext PRECISION = new MathContext(20, RoundingMode.HALF_UP);

    /** Fraccion de cumplimiento maxima por resultado clave (100% de ese resultado). */
    private static final BigDecimal CUMPLIMIENTO_MAXIMO = BigDecimal.ONE;

    /** Factor para expresar la fraccion ponderada [0,1] como porcentaje [0,100]. */
    private static final BigDecimal CIEN = new BigDecimal("100");

    private CalculoAvanceObjetivo() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Calcula el avance ponderado de un objetivo a partir de sus resultados clave
     * (Req 58.8), acotado a [0, 100] (Req 58.9; Property 29). Funcion pura: sin
     * efectos secundarios y determinista.
     *
     * @param resultados contribuciones de los resultados clave; nunca {@code null}
     *                   (una lista vacia significa objetivo sin resultados clave).
     * @return el avance ponderado a escala 2, en el rango [0, 100].
     * @throws NullPointerException si {@code resultados} es {@code null} o contiene
     *         algun elemento {@code null}.
     */
    public static BigDecimal avancePonderado(List<ResultadoClaveValor> resultados) {
        if (resultados == null) {
            throw new NullPointerException("La lista de resultados clave es obligatoria.");
        }
        if (resultados.isEmpty()) {
            return EstrategiaValidaciones.AVANCE_MINIMO;
        }

        BigDecimal sumaPesos = BigDecimal.ZERO;
        BigDecimal sumaPonderada = BigDecimal.ZERO;
        for (ResultadoClaveValor rc : resultados) {
            if (rc == null) {
                throw new NullPointerException("Un resultado clave no puede ser nulo.");
            }
            BigDecimal peso = normalizarNoNulo(rc.peso());
            // Solo suma pesos estrictamente positivos: pesos <= 0 no contribuyen.
            if (peso.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal cumplimiento = cumplimiento(rc.valorActual(), rc.valorObjetivo());
            sumaPesos = sumaPesos.add(peso);
            sumaPonderada = sumaPonderada.add(peso.multiply(cumplimiento));
        }

        // Sin peso positivo total: no hay ponderacion posible -> avance 0.
        if (sumaPesos.compareTo(BigDecimal.ZERO) <= 0) {
            return EstrategiaValidaciones.AVANCE_MINIMO;
        }

        BigDecimal fraccion = sumaPonderada.divide(sumaPesos, PRECISION); // en [0, 1]
        BigDecimal porcentaje = fraccion.multiply(CIEN);
        // Acotamiento final a [0, 100] (Req 58.9): defensa idempotente ante
        // cualquier redondeo, aunque por construccion ya esta en rango.
        return EstrategiaValidaciones.acotarAvance(porcentaje);
    }

    /**
     * Cumplimiento de un resultado clave como {@code clamp(valorActual /
     * valorObjetivo, 0, 1)}. Un valor objetivo no positivo o valores degenerados se
     * tratan como cumplimiento 0 (regla conservadora documentada en la clase).
     *
     * @param valorActual   valor actual medido.
     * @param valorObjetivo valor objetivo (meta medible).
     * @return la fraccion de cumplimiento en [0, 1].
     */
    private static BigDecimal cumplimiento(BigDecimal valorActual, BigDecimal valorObjetivo) {
        BigDecimal actual = normalizarNoNulo(valorActual);
        BigDecimal objetivo = normalizarNoNulo(valorObjetivo);
        // Guardado: valor objetivo no positivo -> cumplimiento 0 (evita /0 e inflado).
        if (objetivo.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        // Un valor actual negativo (dato degenerado) se trata como 0.
        if (actual.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal fraccion = actual.divide(objetivo, PRECISION);
        // Acotamiento superior: superar la meta cuenta como 100% de ese resultado
        // (nunca aporta mas de su peso), garantia clave del Req 58.9.
        if (fraccion.compareTo(CUMPLIMIENTO_MAXIMO) > 0) {
            return CUMPLIMIENTO_MAXIMO;
        }
        return fraccion;
    }

    private static BigDecimal normalizarNoNulo(BigDecimal valor) {
        return (valor == null) ? BigDecimal.ZERO : valor;
    }
}
