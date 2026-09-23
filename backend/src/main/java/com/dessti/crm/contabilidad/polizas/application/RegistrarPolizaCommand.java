package com.dessti.crm.contabilidad.polizas.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.contabilidad.polizas.domain.TipoPoliza;

/**
 * Comando de registro de una
 * {@link com.dessti.crm.contabilidad.polizas.domain.PolizaContable} balanceada
 * (Req 38.2, 38.3, 38.4). Objeto de entrada de la capa de aplicacion, distinto de la
 * entidad. No incluye el {@code tenant_id} (se deriva del contexto, Req 23.4).
 *
 * @param fecha     fecha contable; obligatoria.
 * @param tipo      tipo de poliza; obligatorio.
 * @param concepto  concepto descriptivo; obligatorio y no vacio.
 * @param origen    evento contable de origen (Req 38.2); opcional.
 * @param origenId  identificador del recurso de origen; opcional.
 * @param renglones renglones de cargo/abono; obligatorio, con al menos un cargo y un
 *                  abono cuya suma total este balanceada (Req 38.3).
 */
public record RegistrarPolizaCommand(
        LocalDate fecha,
        TipoPoliza tipo,
        String concepto,
        String origen,
        UUID origenId,
        List<RenglonPolizaCommand> renglones) {
}
