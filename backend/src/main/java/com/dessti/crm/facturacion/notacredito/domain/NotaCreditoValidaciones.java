package com.dessti.crm.facturacion.notacredito.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades <strong>puras</strong> de validacion monetaria de la Nota de Credito
 * (Req 37.2; Property 14). Centraliza la regla de acotacion del monto por el saldo
 * disponible de la Factura referenciada para poder verificarla directamente por
 * pruebas, sin base de datos ni contexto de Spring.
 *
 * <h2>Regla (Req 37.2)</h2>
 * <p>El saldo disponible de una Factura para notas de credito es
 * {@code saldo = total - Σ(notas de credito previas no canceladas)}. Una Nota de
 * Credito se admite <em>si y solo si</em> su {@code monto} es positivo y no excede
 * ese saldo; en caso contrario se rechaza (422) sin alterar la Factura
 * (Property 14).</p>
 *
 * <p>Nota de integracion: la disminucion efectiva de la Cuenta_Por_Cobrar
 * asociada (Req 37.1) se completa en el bloque 29 (modulo contabilidad-finanzas,
 * CxC). En este bloque el tope se calcula como {@code total - notas previas},
 * equivalente al saldo cuando aun no hay pagos aplicados; el modulo de CxC
 * refinara el saldo restando tambien los pagos.</p>
 */
public final class NotaCreditoValidaciones {

    /** Escala monetaria del sistema (2 decimales), coherente con NUMERIC(18,2). */
    public static final int ESCALA_MONETARIA = 2;

    private NotaCreditoValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Calcula el saldo disponible de una Factura para emitir notas de credito
     * (Req 37.2): {@code round(total - notasPrevias, 2)} half-up, acotado a 0 por
     * abajo (nunca negativo).
     *
     * @param totalFactura total de la Factura referenciada; obligatorio y no negativo.
     * @param notasPrevias suma de las notas de credito previas no canceladas;
     *                     obligatoria y no negativa.
     * @return el saldo disponible a escala 2 (>= 0).
     * @throws ReglaNegocioException si algun importe es nulo o negativo (422).
     */
    public static BigDecimal saldoDisponible(BigDecimal totalFactura, BigDecimal notasPrevias) {
        if (totalFactura == null || notasPrevias == null) {
            throw new ReglaNegocioException("Los importes de la Factura son obligatorios.");
        }
        if (totalFactura.signum() < 0 || notasPrevias.signum() < 0) {
            throw new ReglaNegocioException("Los importes de la Factura no pueden ser negativos.");
        }
        BigDecimal saldo = totalFactura.subtract(notasPrevias)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return (saldo.signum() < 0) ? BigDecimal.ZERO.setScale(ESCALA_MONETARIA) : saldo;
    }

    /**
     * Valida que el monto de una Nota de Credito sea positivo y no exceda el saldo
     * disponible de la Factura referenciada (Req 37.2; Property 14). Devuelve el
     * monto normalizado a escala 2 cuando es valido.
     *
     * @param monto        monto de la Nota de Credito; obligatorio y positivo.
     * @param totalFactura total de la Factura referenciada; obligatorio.
     * @param notasPrevias suma de las notas de credito previas no canceladas;
     *                     obligatoria.
     * @return el monto normalizado a escala 2, si es admisible.
     * @throws ReglaNegocioException si el monto es nulo/no positivo o excede el
     *         saldo disponible (422, Req 37.2).
     */
    public static BigDecimal validarMontoContraSaldo(BigDecimal monto, BigDecimal totalFactura,
                                                     BigDecimal notasPrevias) {
        if (monto == null) {
            throw new ReglaNegocioException("El monto de la Nota de Credito es obligatorio.");
        }
        BigDecimal montoNormalizado = monto.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (montoNormalizado.signum() <= 0) {
            throw new ReglaNegocioException("El monto de la Nota de Credito debe ser positivo.");
        }
        BigDecimal saldo = saldoDisponible(totalFactura, notasPrevias);
        if (montoNormalizado.compareTo(saldo) > 0) {
            throw new ReglaNegocioException(
                    "El monto de la Nota de Credito (" + montoNormalizado.toPlainString()
                            + ") excede el saldo disponible de la Factura (" + saldo.toPlainString()
                            + ").");
        }
        return montoNormalizado;
    }
}
