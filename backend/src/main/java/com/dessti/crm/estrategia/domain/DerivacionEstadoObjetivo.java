package com.dessti.crm.estrategia.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Funcion <strong>pura</strong> del dominio que deriva el {@link EstadoObjetivo}
 * (en_riesgo / en_curso / cumplido) de un {@link ObjetivoEstrategico} a partir de
 * su avance (0..100) y de la fraccion del periodo transcurrida (Req 58.10). No
 * depende de Spring ni de JPA: recibe el avance, el periodo y la fecha de consulta,
 * por lo que es determinista y unitariamente comprobable (tarea 37.3). Espeja el
 * estilo de {@code DerivacionEstadoProyecto} (bloque 22).
 *
 * <h2>Regla de derivacion (Req 58.9, 58.10; design.md)</h2>
 * <ol>
 *   <li><strong>cumplido</strong> cuando {@code avance >= 100}: el objetivo alcanzo
 *       la meta, con independencia del periodo (Req 58.9).</li>
 *   <li>En otro caso ({@code avance < 100}) se calcula la
 *       <em>fraccion del periodo transcurrida</em> como
 *       {@code clamp((hoy - inicio) / (fin - inicio), 0, 1) * 100} y:
 *       <ul>
 *         <li><strong>en_riesgo</strong> si {@code avance < fraccionPeriodo}: el
 *             avance queda por debajo de lo esperado (rezago).</li>
 *         <li><strong>en_curso</strong> si {@code avance >= fraccionPeriodo}: el
 *             avance es consistente con el periodo transcurrido.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>Casos borde documentados</h2>
 * <ul>
 *   <li><strong>Antes del inicio del periodo</strong> ({@code hoy < inicio}): la
 *       fraccion transcurrida es 0; como cualquier avance {@code >= 0}, el estado es
 *       <strong>en_curso</strong> (aun no se espera avance, no hay rezago).</li>
 *   <li><strong>Despues del fin del periodo</strong> ({@code hoy >= fin}) con
 *       {@code avance < 100}: la fraccion transcurrida es 100; como el avance es
 *       menor a 100, el estado es <strong>en_riesgo</strong> (el periodo termino sin
 *       cumplir la meta).</li>
 *   <li><strong>Periodo de un solo dia</strong> ({@code inicio == fin}): mientras
 *       {@code hoy < fin} la fraccion es 0 (en_curso salvo cumplido); desde
 *       {@code hoy >= fin} la fraccion es 100 (en_riesgo si avance &lt; 100). Evita
 *       la division por cero tratando la duracion 0 como "instantanea".</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable.</p>
 */
public final class DerivacionEstadoObjetivo {

    /** Precision intermedia amplia para la fraccion del periodo antes de escalar. */
    private static final MathContext PRECISION = new MathContext(20, RoundingMode.HALF_UP);

    private static final BigDecimal CIEN = new BigDecimal("100");

    private DerivacionEstadoObjetivo() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Deriva el estado del objetivo a partir de su avance y de la fraccion del
     * periodo transcurrida al dia {@code hoy} (Req 58.10). Funcion pura: sin efectos
     * secundarios y determinista.
     *
     * @param avance         porcentaje de avance del objetivo en [0, 100] (Req 58.9).
     * @param periodoInicio  fecha de inicio del periodo; obligatoria.
     * @param periodoFin     fecha de fin del periodo; obligatoria ({@code >= inicio}).
     * @param hoy            fecha de consulta (normalmente el reloj del sistema).
     * @return el estado derivado (en_riesgo / en_curso / cumplido).
     * @throws NullPointerException     si algun parametro es {@code null}.
     * @throws IllegalArgumentException si {@code periodoFin} es anterior a
     *         {@code periodoInicio}.
     */
    public static EstadoObjetivo derivar(BigDecimal avance, LocalDate periodoInicio,
                                         LocalDate periodoFin, LocalDate hoy) {
        if (avance == null) {
            throw new NullPointerException("El avance es obligatorio.");
        }
        if (periodoInicio == null || periodoFin == null || hoy == null) {
            throw new NullPointerException("El periodo y la fecha de consulta son obligatorios.");
        }
        if (periodoFin.isBefore(periodoInicio)) {
            throw new IllegalArgumentException(
                    "El fin del periodo no puede ser anterior a su inicio.");
        }

        // (1) cumplido: la meta se alcanzo, independientemente del periodo (Req 58.9).
        if (avance.compareTo(EstrategiaValidaciones.AVANCE_MAXIMO) >= 0) {
            return EstadoObjetivo.CUMPLIDO;
        }

        // (2) fraccion del periodo transcurrida, expresada como porcentaje [0, 100].
        BigDecimal fraccionPeriodo = fraccionTranscurrida(periodoInicio, periodoFin, hoy);

        // en_riesgo si el avance va por debajo de lo esperado; en_curso si lo alcanza.
        if (avance.compareTo(fraccionPeriodo) < 0) {
            return EstadoObjetivo.EN_RIESGO;
        }
        return EstadoObjetivo.EN_CURSO;
    }

    /**
     * Calcula la fraccion del periodo transcurrida al dia {@code hoy}, como
     * porcentaje acotado a [0, 100]. Antes del inicio devuelve 0; en el fin o
     * despues devuelve 100; un periodo de duracion cero se trata como instantaneo.
     *
     * @param inicio inicio del periodo.
     * @param fin    fin del periodo ({@code >= inicio}).
     * @param hoy    fecha de consulta.
     * @return el porcentaje del periodo transcurrido en [0, 100].
     */
    private static BigDecimal fraccionTranscurrida(LocalDate inicio, LocalDate fin, LocalDate hoy) {
        // El orden importa en el caso borde inicio == fin == hoy: "periodo agotado"
        // (hoy >= fin) tiene precedencia sobre "aun no inicia" (hoy <= inicio), de modo
        // que un periodo de un solo dia, consultado ese mismo dia, se considera 100%
        // transcurrido (en_riesgo si avance < 100), coherente con la regla documentada.
        if (!hoy.isBefore(fin)) {
            // hoy >= fin: el periodo se agoto por completo.
            return CIEN;
        }
        if (!hoy.isAfter(inicio)) {
            // hoy <= inicio (con hoy < fin): el periodo aun no comienza a "consumirse".
            return BigDecimal.ZERO;
        }
        // inicio < hoy < fin: proporcion de dias transcurridos sobre la duracion total.
        long duracionTotal = fin.toEpochDay() - inicio.toEpochDay();
        if (duracionTotal <= 0) {
            // Periodo instantaneo (inicio == fin): ya cubierto por las ramas anteriores,
            // pero se protege la division por cero por robustez.
            return CIEN;
        }
        long transcurridos = hoy.toEpochDay() - inicio.toEpochDay();
        BigDecimal fraccion = BigDecimal.valueOf(transcurridos)
                .divide(BigDecimal.valueOf(duracionTotal), PRECISION)
                .multiply(CIEN);
        return fraccion.setScale(EstrategiaValidaciones.ESCALA_PORCENTAJE, RoundingMode.HALF_UP);
    }
}
