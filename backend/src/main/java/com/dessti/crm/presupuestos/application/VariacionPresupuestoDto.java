package com.dessti.crm.presupuestos.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.presupuestos.domain.ResultadoVariacion;

/**
 * DTO de salida de <strong>SOLO LECTURA</strong> que reporta la variacion de un
 * Presupuesto frente al ejercicio real (Req 62.2, 62.3, 62.6, 62.7; Property 36). No
 * modifica ningun origen de datos: es una agregacion de lectura calculada bajo demanda
 * (Req 62.2).
 *
 * <p>Para cada renglon (ingresos y egresos) reporta el importe presupuestado, el real
 * (aportado por {@code RealEjercidoPort}), la variacion en importe absoluto y en
 * porcentaje (Req 62.6, 62.7), el indicador favorable/desfavorable segun el tipo de
 * renglon (Req 62.6) y si la variacion supera el umbral configurable y debe destacarse
 * (Req 62.3).</p>
 *
 * @param presupuestoId          identificador del Presupuesto evaluado.
 * @param area                   area funcional del Presupuesto (Req 62.4).
 * @param periodo                periodo del Presupuesto (Req 62.4).
 * @param ingresosEstimados      ingresos presupuestados (escala 2).
 * @param ingresosReales         ingresos reales agregados (escala 2, Req 62.8).
 * @param variacionIngresosImporte     variacion de ingresos en importe absoluto
 *                                     ({@code real - presupuestado}, Req 62.6, 62.7).
 * @param variacionIngresosPorcentaje  variacion de ingresos en porcentaje (Req 62.7).
 * @param ingresosFavorable      {@code true} si la variacion de ingresos es favorable (Req 62.6).
 * @param ingresosSuperaUmbral   {@code true} si la variacion de ingresos supera el umbral (Req 62.3).
 * @param egresosEstimados       egresos presupuestados (escala 2).
 * @param egresosReales          egresos reales agregados (escala 2, Req 62.8).
 * @param variacionEgresosImporte      variacion de egresos en importe absoluto (Req 62.6, 62.7).
 * @param variacionEgresosPorcentaje   variacion de egresos en porcentaje (Req 62.7).
 * @param egresosFavorable       {@code true} si la variacion de egresos es favorable (Req 62.6).
 * @param egresosSuperaUmbral    {@code true} si la variacion de egresos supera el umbral (Req 62.3).
 * @param destacar               {@code true} si CUALQUIER renglon supera el umbral y debe
 *                               destacarse la desviacion en reportes (Req 62.3).
 */
public record VariacionPresupuestoDto(
        UUID presupuestoId,
        String area,
        String periodo,
        BigDecimal ingresosEstimados,
        BigDecimal ingresosReales,
        BigDecimal variacionIngresosImporte,
        BigDecimal variacionIngresosPorcentaje,
        boolean ingresosFavorable,
        boolean ingresosSuperaUmbral,
        BigDecimal egresosEstimados,
        BigDecimal egresosReales,
        BigDecimal variacionEgresosImporte,
        BigDecimal variacionEgresosPorcentaje,
        boolean egresosFavorable,
        boolean egresosSuperaUmbral,
        boolean destacar) {

    /**
     * Ensambla el DTO de variacion a partir del Presupuesto, los importes reales y
     * los resultados puros de variacion calculados por
     * {@code CalculoVariacionPresupuesto} para ingresos y egresos. El indicador
     * {@code destacar} es la disyuncion de los {@code superaUmbral} de ambos renglones
     * (Req 62.3).
     *
     * @param presupuestoId     identificador del Presupuesto.
     * @param area              area funcional.
     * @param periodo           periodo.
     * @param ingresosEstimados ingresos presupuestados.
     * @param ingresosReales    ingresos reales agregados.
     * @param ingresos          resultado de variacion de ingresos.
     * @param egresosEstimados  egresos presupuestados.
     * @param egresosReales     egresos reales agregados.
     * @param egresos           resultado de variacion de egresos.
     * @return el DTO de variacion listo para serializar.
     */
    public static VariacionPresupuestoDto de(
            UUID presupuestoId, String area, String periodo,
            BigDecimal ingresosEstimados, BigDecimal ingresosReales, ResultadoVariacion ingresos,
            BigDecimal egresosEstimados, BigDecimal egresosReales, ResultadoVariacion egresos) {
        return new VariacionPresupuestoDto(
                presupuestoId,
                area,
                periodo,
                ingresosEstimados,
                ingresosReales,
                ingresos.variacionImporte(),
                ingresos.variacionPorcentaje(),
                ingresos.favorable(),
                ingresos.superaUmbral(),
                egresosEstimados,
                egresosReales,
                egresos.variacionImporte(),
                egresos.variacionPorcentaje(),
                egresos.favorable(),
                egresos.superaUmbral(),
                ingresos.superaUmbral() || egresos.superaUmbral());
    }
}
