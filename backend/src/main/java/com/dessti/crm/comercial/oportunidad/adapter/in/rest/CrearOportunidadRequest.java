package com.dessti.crm.comercial.oportunidad.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta una Oportunidad (Req 14.1). DTO de
 * entrada del contrato REST, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.comercial.oportunidad.application.CrearOportunidadCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * <p>La validacion de campo (Bean Validation -&gt; 400) cubre presencia, longitud
 * y rango monetario; las reglas de dominio finas (422) las aplica la capa de
 * aplicacion.</p>
 *
 * @param clienteId     Cliente existente al que se asocia; obligatorio.
 * @param titulo        titulo de la Oportunidad; obligatorio (1..200).
 * @param valorEstimado valor estimado; obligatorio, en [0.01, 999,999,999.99].
 */
public record CrearOportunidadRequest(
        @NotNull UUID clienteId,
        @NotBlank @Size(max = 200) String titulo,
        @NotNull
        @DecimalMin(value = "0.01")
        @DecimalMax(value = "999999999.99")
        @Digits(integer = 9, fraction = 2)
        BigDecimal valorEstimado) {
}
