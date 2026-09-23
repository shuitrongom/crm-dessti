package com.dessti.crm.reportesbi.application;

import java.math.BigDecimal;

import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * DTO de salida de un indicador individual del Tablero o del consolidado (Req 22.1,
 * 48.1), distinto del objeto de dominio {@link ValorIndicador}. Expone la clave, la
 * etiqueta legible, el valor, la unidad y, cuando existe, el valor comparativo del
 * periodo anterior junto con la variacion derivada (tendencia read-only, Req 48.1).
 *
 * @param clave       clave ASCII estable del indicador.
 * @param etiqueta    descripcion legible del indicador.
 * @param valor       valor agregado del periodo actual.
 * @param unidad      unidad de medida (p. ej. {@code "conteo"}, {@code "MXN"}).
 * @param comparativo valor del periodo anterior; {@code null} si no hay historico.
 * @param variacion   diferencia {@code valor - comparativo}; {@code null} si no hay
 *                    comparativo. Derivada de solo lectura (Req 48.1, 48.2).
 */
public record IndicadorDto(
        String clave,
        String etiqueta,
        BigDecimal valor,
        String unidad,
        BigDecimal comparativo,
        BigDecimal variacion) {

    /**
     * Proyecta un {@link ValorIndicador} de dominio a su DTO, calculando la variacion
     * cuando el indicador aporta comparativo (Req 48.1).
     *
     * @param valor indicador de dominio.
     * @return el DTO correspondiente.
     */
    public static IndicadorDto de(ValorIndicador valor) {
        BigDecimal variacion = valor.tieneComparativo()
                ? valor.valor().subtract(valor.comparativo())
                : null;
        return new IndicadorDto(valor.clave(), valor.etiqueta(), valor.valor(),
                valor.unidad(), valor.comparativo(), variacion);
    }
}
