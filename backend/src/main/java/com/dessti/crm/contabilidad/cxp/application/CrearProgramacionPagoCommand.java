package com.dessti.crm.contabilidad.cxp.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando de creacion de una {@link com.dessti.crm.contabilidad.cxp.domain.ProgramacionPago}
 * (Req 42.2). Objeto de entrada de la capa de aplicacion, distinto de la entidad. No
 * incluye el {@code tenant_id} (se deriva del contexto, Req 23.4).
 *
 * @param cuentaPorPagarId Cuenta_Por_Pagar a la que corresponde el pago; obligatorio.
 * @param fechaProgramada  fecha programada del pago; obligatoria.
 * @param monto            monto programado; positivo (Req 42.2).
 */
public record CrearProgramacionPagoCommand(
        UUID cuentaPorPagarId,
        LocalDate fechaProgramada,
        BigDecimal monto) {
}
