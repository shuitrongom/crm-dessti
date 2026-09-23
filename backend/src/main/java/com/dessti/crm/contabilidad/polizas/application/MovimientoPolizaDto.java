package com.dessti.crm.contabilidad.polizas.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.contabilidad.polizas.domain.MovimientoPoliza;

/**
 * DTO de salida de un renglon ({@link MovimientoPoliza}) de una Poliza_Contable
 * (Req 12.2, 38.2), distinto de la entidad de persistencia.
 *
 * @param id               identificador del renglon.
 * @param cuentaContableId Cuenta_Contable afectada.
 * @param cargo            importe del cargo (0 si es un abono).
 * @param abono            importe del abono (0 si es un cargo).
 */
public record MovimientoPolizaDto(
        UUID id,
        UUID cuentaContableId,
        BigDecimal cargo,
        BigDecimal abono) {

    /**
     * Proyecta un renglon {@link MovimientoPoliza} a su DTO de salida.
     *
     * @param movimiento renglon a proyectar.
     * @return el DTO correspondiente.
     */
    public static MovimientoPolizaDto de(MovimientoPoliza movimiento) {
        return new MovimientoPolizaDto(
                movimiento.getId(),
                movimiento.getCuentaContableId(),
                movimiento.getCargo(),
                movimiento.getAbono());
    }
}
