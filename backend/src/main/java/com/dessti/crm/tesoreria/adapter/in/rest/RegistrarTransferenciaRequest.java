package com.dessti.crm.tesoreria.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para registrar una Transferencia_Bancaria entre dos
 * Cuentas_Bancarias de la Empresa (Req 43). DTO de entrada del contrato REST,
 * distinto del comando de aplicacion
 * {@link com.dessti.crm.tesoreria.application.RegistrarTransferenciaCommand}. El
 * {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * @param cuentaOrigenId  Cuenta_Bancaria de origen; obligatoria.
 * @param cuentaDestinoId Cuenta_Bancaria de destino; obligatoria y distinta del origen.
 * @param monto           monto a transferir; obligatorio y mayor que cero.
 * @param fecha           fecha del traspaso; obligatoria.
 * @param concepto        concepto descriptivo; opcional (max 300).
 */
public record RegistrarTransferenciaRequest(
        @NotNull UUID cuentaOrigenId,
        @NotNull UUID cuentaDestinoId,
        @NotNull @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero") BigDecimal monto,
        @NotNull LocalDate fecha,
        @Size(max = 300) String concepto) {
}
