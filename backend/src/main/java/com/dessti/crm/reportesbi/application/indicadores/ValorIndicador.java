package com.dessti.crm.reportesbi.application.indicadores;

import java.math.BigDecimal;

/**
 * Valor de un indicador individual, inmutable y libre de dependencias de framework
 * (Req 22.1, 48.1). Es la unidad minima que devuelven los {@link IndicadorAreaPort
 * puertos de indicadores}: una <strong>metrica agregada de solo lectura</strong>
 * identificada por una clave ASCII estable, con una etiqueta legible, un valor
 * numerico y una unidad de medida opcional.
 *
 * <p>El valor se modela como {@link BigDecimal} para cubrir tanto conteos (por ejemplo
 * "Ordenes de Fabricacion en produccion") como importes monetarios (por ejemplo
 * "facturacion del periodo") sin perdida de precision. La unidad ({@code "conteo"},
 * {@code "MXN"}, {@code "porcentaje"}, {@code "dias"}, ...) documenta la escala para el
 * consumidor. El {@code comparativo} transporta, cuando existe, el valor del periodo
 * anterior para derivar tendencias/comparativos de forma read-only (Req 48.1); es
 * {@code null} cuando el area no aporta historico.</p>
 *
 * @param clave       clave ASCII estable del indicador (p. ej.
 *                    {@code "cotizaciones_aprobadas"}); obligatoria.
 * @param etiqueta    descripcion legible del indicador (puede llevar acentos).
 * @param valor       valor agregado del periodo actual; nunca {@code null}.
 * @param unidad      unidad de medida (p. ej. {@code "conteo"}, {@code "MXN"});
 *                    obligatoria.
 * @param comparativo valor del periodo anterior para el comparativo (Req 48.1);
 *                    {@code null} si el area no aporta historico.
 */
public record ValorIndicador(
        String clave,
        String etiqueta,
        BigDecimal valor,
        String unidad,
        BigDecimal comparativo) {

    /**
     * Crea un indicador de conteo sin comparativo.
     *
     * @param clave    clave ASCII estable.
     * @param etiqueta descripcion legible.
     * @param valor    conteo agregado.
     * @return el indicador con unidad {@code "conteo"} y sin comparativo.
     */
    public static ValorIndicador conteo(String clave, String etiqueta, BigDecimal valor) {
        return new ValorIndicador(clave, etiqueta, valor, "conteo", null);
    }

    /**
     * Crea un indicador monetario sin comparativo.
     *
     * @param clave    clave ASCII estable.
     * @param etiqueta descripcion legible.
     * @param valor    importe agregado.
     * @return el indicador con unidad {@code "MXN"} y sin comparativo.
     */
    public static ValorIndicador monetario(String clave, String etiqueta, BigDecimal valor) {
        return new ValorIndicador(clave, etiqueta, valor, "MXN", null);
    }

    /**
     * Indica si el indicador tiene un comparativo de periodo anterior disponible
     * (Req 48.1), lo que permite derivar una tendencia read-only.
     *
     * @return {@code true} si {@link #comparativo()} no es {@code null}.
     */
    public boolean tieneComparativo() {
        return comparativo != null;
    }
}
