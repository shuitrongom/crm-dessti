package com.dessti.crm.contabilidad.cxc.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.contabilidad.cxc.domain.AplicacionPago;

/**
 * DTO de salida de una {@link AplicacionPago} (la porcion de un Pago_Cliente
 * aplicada a una Factura) (Req 12.2, 36.2), distinto de la entidad.
 *
 * @param id                identificador de la aplicacion.
 * @param cuentaPorCobrarId Cuenta_Por_Cobrar a la que se aplico.
 * @param facturaId         Factura asociada.
 * @param montoAplicado     monto aplicado a esa Factura (Req 36.2).
 */
public record AplicacionPagoDto(
        UUID id,
        UUID cuentaPorCobrarId,
        UUID facturaId,
        BigDecimal montoAplicado) {

    /**
     * Proyecta una entidad {@link AplicacionPago} a su DTO de salida.
     *
     * @param aplicacion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static AplicacionPagoDto de(AplicacionPago aplicacion) {
        return new AplicacionPagoDto(
                aplicacion.getId(),
                aplicacion.getCuentaPorCobrarId(),
                aplicacion.getFacturaId(),
                aplicacion.getMontoAplicado());
    }
}
