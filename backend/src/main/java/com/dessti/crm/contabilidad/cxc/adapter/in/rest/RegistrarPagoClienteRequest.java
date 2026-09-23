package com.dessti.crm.contabilidad.cxc.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para registrar un Pago_Cliente y aplicarlo a una o varias
 * Facturas (Req 36.2, 36.3, 36.4). DTO de entrada del contrato REST, distinto del
 * comando de aplicacion
 * {@link com.dessti.crm.contabilidad.cxc.application.RegistrarPagoClienteCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * @param clienteId     Cliente que paga; obligatorio (Req 36.2).
 * @param monto         monto total del pago; obligatorio y positivo (Req 36.2).
 * @param formaPago     forma de pago (clave del catalogo del SAT); opcional.
 * @param esParcialidad {@code true} si es parcialidad/diferido: exige y timbra un
 *                      Complemento_Pago via PAC (Req 36.4). {@code null} se trata
 *                      como {@code false}.
 * @param aplicaciones  desglose de la aplicacion por Factura; obligatorio y no vacio.
 */
public record RegistrarPagoClienteRequest(
        @NotNull UUID clienteId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal monto,
        String formaPago,
        Boolean esParcialidad,
        @NotNull @NotEmpty @Size(max = 200) @Valid List<AplicacionPagoRequest> aplicaciones) {

    /**
     * Indica si el pago es una parcialidad/diferido, tratando {@code null} como
     * {@code false}.
     *
     * @return {@code true} si es parcialidad.
     */
    public boolean esParcialidadEfectiva() {
        return Boolean.TRUE.equals(esParcialidad);
    }
}
