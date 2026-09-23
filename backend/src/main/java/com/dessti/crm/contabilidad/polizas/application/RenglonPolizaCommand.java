package com.dessti.crm.contabilidad.polizas.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando que describe un renglon de una Poliza_Contable: un cargo
 * <strong>o</strong> un abono a una Cuenta_Contable (Req 38.2, 38.3). El servicio
 * traduce el renglon a un {@code MovimientoPoliza} de cargo o de abono segun cual de
 * los dos importes sea positivo.
 *
 * <p>Exactamente uno de {@code cargo} y {@code abono} debe ser positivo; el otro
 * debe ser cero o {@code null} (regla cargo XOR abono, validada por el dominio).</p>
 *
 * @param cuentaContableId Cuenta_Contable afectada; obligatoria.
 * @param cargo            importe del cargo; positivo si el renglon es un cargo.
 * @param abono            importe del abono; positivo si el renglon es un abono.
 */
public record RenglonPolizaCommand(
        UUID cuentaContableId,
        BigDecimal cargo,
        BigDecimal abono) {
}
