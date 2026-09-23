package com.dessti.crm.facturacion.factura.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Componente de dominio <strong>puro</strong> que calcula los importes fiscales de
 * una Factura (CFDI 4.0): IVA, retenciones y total (Req 34.2; Property 4).
 *
 * <h2>Reglas fiscales (Req 34.2)</h2>
 * <ul>
 *   <li><strong>IVA:</strong> {@code iva = round(subtotal * 0.16, 2)} con redondeo
 *       al valor mas cercano (half-up). La tasa del 16% es la vigente en Mexico
 *       ({@link #TASA_IVA}).</li>
 *   <li><strong>Retenciones:</strong> cuando la operacion esta sujeta a Retencion,
 *       {@code retenciones = round(subtotal * tasaRetencion, 2)} half-up; si no hay
 *       retencion, la tasa es 0 y el importe es {@code 0.00}.</li>
 *   <li><strong>Total:</strong> {@code total = round(subtotal + iva - retenciones,
 *       2)} half-up.</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable, sin dependencias de framework ni de
 * persistencia, para que la <strong>Property 4</strong> la ejercite directamente
 * sobre miles de entradas. El redondeo half-up y la escala 2 son coherentes con
 * {@code CotizacionValidaciones} (aritmetica monetaria del sistema, NUMERIC(18,2)).
 * Los importes negativos o una tasa de retencion fuera de {@code [0, 1]} se
 * rechazan con {@link ReglaNegocioException} (422).</p>
 */
public final class CalculoFiscalCfdi {

    /** Escala monetaria del sistema (2 decimales), coherente con NUMERIC(18,2). */
    public static final int ESCALA_MONETARIA = 2;

    /** Tasa de IVA vigente en Mexico: 16% (Req 34.2). */
    public static final BigDecimal TASA_IVA = new BigDecimal("0.16");

    /** Tasa de retencion nula (operacion no sujeta a Retencion). */
    public static final BigDecimal SIN_RETENCION = BigDecimal.ZERO;

    private CalculoFiscalCfdi() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Calcula los importes fiscales de una Factura a partir de su subtotal y de la
     * tasa de retencion aplicable (Req 34.2; Property 4). Todos los importes del
     * resultado quedan a escala 2 (half-up).
     *
     * @param subtotal      subtotal de la Factura; obligatorio y no negativo.
     * @param tasaRetencion tasa de retencion en {@code [0, 1]} ({@link #SIN_RETENCION}
     *                      si la operacion no esta sujeta a Retencion); obligatoria.
     * @return los importes fiscales calculados (subtotal normalizado, IVA,
     *         retenciones y total).
     * @throws ReglaNegocioException si el subtotal es nulo/negativo o la tasa de
     *         retencion es nula o esta fuera de {@code [0, 1]} (422).
     */
    public static ImportesFiscales calcular(BigDecimal subtotal, BigDecimal tasaRetencion) {
        if (subtotal == null) {
            throw new ReglaNegocioException("El subtotal de la Factura es obligatorio.");
        }
        if (subtotal.signum() < 0) {
            throw new ReglaNegocioException("El subtotal de la Factura no puede ser negativo.");
        }
        if (tasaRetencion == null) {
            throw new ReglaNegocioException("La tasa de retencion es obligatoria (0 si no aplica).");
        }
        if (tasaRetencion.signum() < 0 || tasaRetencion.compareTo(BigDecimal.ONE) > 0) {
            throw new ReglaNegocioException("La tasa de retencion debe estar entre 0 y 1.");
        }

        BigDecimal subtotalNormalizado = redondear(subtotal);
        BigDecimal iva = redondear(subtotalNormalizado.multiply(TASA_IVA));
        BigDecimal retenciones = redondear(subtotalNormalizado.multiply(tasaRetencion));
        BigDecimal total = redondear(subtotalNormalizado.add(iva).subtract(retenciones));
        return new ImportesFiscales(subtotalNormalizado, iva, retenciones, total);
    }

    /**
     * Normaliza un importe monetario a escala 2 con redondeo al valor mas cercano
     * (half-up), coherente con el resto de la aritmetica monetaria del sistema.
     *
     * @param valor importe a normalizar; obligatorio.
     * @return el importe a escala 2 (half-up).
     */
    public static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Importes fiscales resultantes del calculo de una Factura (Req 34.2), todos a
     * escala 2. El invariante {@code total == round(subtotal + iva - retenciones,
     * 2)} lo garantiza {@link #calcular(BigDecimal, BigDecimal)} (Property 4).
     *
     * @param subtotal    subtotal normalizado a escala 2.
     * @param iva         IVA trasladado = round(subtotal * 0.16, 2).
     * @param retenciones retenciones = round(subtotal * tasaRetencion, 2).
     * @param total       total = round(subtotal + iva - retenciones, 2).
     */
    public record ImportesFiscales(
            BigDecimal subtotal,
            BigDecimal iva,
            BigDecimal retenciones,
            BigDecimal total) {
    }
}
