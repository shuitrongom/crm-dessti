package com.dessti.crm.reportesbi.application.ia;

import java.math.BigDecimal;

/**
 * Proyeccion minima y estable de un indicador que se entrega al
 * {@link GeneradorInsightsPort generador de insights} (Req 48, suite BI+IA). Es un
 * <em>record</em> inmutable, libre de dependencias de framework, que transporta lo
 * imprescindible para narrar el desempeno del periodo: la clave estable, la etiqueta
 * legible, el area a la que pertenece, el valor agregado del periodo actual, su unidad
 * de medida y, cuando existe, el comparativo del periodo anterior con su variacion
 * absoluta.
 *
 * <p>Se mantiene desacoplado de los DTO REST y del dominio: el servicio de insights lo
 * compone a partir del consolidado, de modo que el puerto de IA no dependa de la forma
 * de serializacion ni de las entidades JPA.</p>
 *
 * @param area        etiqueta ASCII estable del area (p. ej. {@code "COMERCIAL"}).
 * @param clave       clave ASCII estable del indicador.
 * @param etiqueta    descripcion legible del indicador (puede llevar acentos).
 * @param valor       valor agregado del periodo actual; nunca {@code null}.
 * @param unidad      unidad de medida (p. ej. {@code "conteo"}, {@code "MXN"}).
 * @param comparativo valor del periodo anterior; {@code null} si no hay historico.
 * @param variacion   diferencia {@code valor - comparativo}; {@code null} si no hay
 *                    comparativo.
 */
public record IndicadorInsight(
        String area,
        String clave,
        String etiqueta,
        BigDecimal valor,
        String unidad,
        BigDecimal comparativo,
        BigDecimal variacion) {

    /**
     * Indica si el indicador aporta un comparativo del periodo anterior, condicion
     * necesaria para derivar una tendencia.
     *
     * @return {@code true} si {@link #comparativo()} y {@link #variacion()} no son nulos.
     */
    public boolean tieneComparativo() {
        return comparativo != null && variacion != null;
    }

    /**
     * Variacion relativa respecto al periodo anterior, como fraccion (0.10 = +10%).
     * Devuelve {@code null} cuando no hay comparativo o cuando el comparativo es cero
     * (no se puede dividir), evitando divisiones indefinidas.
     *
     * @return la variacion relativa, o {@code null} si no es calculable.
     */
    public BigDecimal variacionRelativa() {
        if (!tieneComparativo() || comparativo.signum() == 0) {
            return null;
        }
        return variacion.divide(comparativo.abs(), 4, java.math.RoundingMode.HALF_UP);
    }
}
