package com.dessti.crm.rhnomina.nomina.domain;

import java.math.BigDecimal;

/**
 * Resultado inmutable del calculo de nomina de un Empleado en un Periodo_Nomina
 * (Req 41.1, 41.2), producido por {@link CalculoNomina#calcular(EntradaNomina)}.
 * Todos los importes estan a escala {@value TablasFiscalesNomina#ESCALA_MONETARIA}
 * (half-up).
 *
 * <h2>Identidad aritmetica (Property 19, Req 41.1, 41.2)</h2>
 * <p>El invariante central que {@link CalculoNomina} garantiza es:</p>
 * <pre>
 *   percepciones = round(salario + tiempoExtra + aguinaldo + ptu, 2)
 *   deducciones  = round(isr + imss + infonavit, 2)
 *   neto         = round(percepciones - deducciones + subsidio, 2)  y  neto &gt;= 0
 * </pre>
 * <p>El desglose ({@code salario}, {@code tiempoExtra}, {@code aguinaldo},
 * {@code ptu}, {@code isr}, {@code imss}, {@code infonavit}) se conserva para la
 * trazabilidad del Recibo_Nomina y del DTO; los totales agregados
 * ({@code percepciones}, {@code deducciones}, {@code subsidio}, {@code neto}) son
 * los que se persisten en {@code recibo_nomina} (V34).</p>
 *
 * @param salario      percepcion por salario del periodo (salarioDiario * dias).
 * @param tiempoExtra  percepcion por tiempo extra del periodo.
 * @param aguinaldo    percepcion por aguinaldo cuando aplica ({@code 0} si no).
 * @param ptu          percepcion por PTU cuando aplica ({@code 0} si no).
 * @param percepciones total de percepciones = salario + tiempoExtra + aguinaldo + ptu.
 * @param isr          deduccion de ISR (Art. 96 LISR).
 * @param imss         deduccion de la cuota obrera del IMSS.
 * @param infonavit    deduccion de Infonavit ({@code 0} si no hay credito).
 * @param deducciones  total de deducciones = isr + imss + infonavit.
 * @param subsidio     subsidio al empleo aplicable ({@code 0} si no corresponde).
 * @param neto         neto a pagar = percepciones - deducciones + subsidio (&gt;= 0).
 */
public record ResultadoNomina(
        BigDecimal salario,
        BigDecimal tiempoExtra,
        BigDecimal aguinaldo,
        BigDecimal ptu,
        BigDecimal percepciones,
        BigDecimal isr,
        BigDecimal imss,
        BigDecimal infonavit,
        BigDecimal deducciones,
        BigDecimal subsidio,
        BigDecimal neto) {
}
