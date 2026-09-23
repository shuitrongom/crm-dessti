package com.dessti.crm.operacion.inventario.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para registrar un Movimiento_Inventario sobre un Material
 * (Req 18.2, 18.3). DTO de entrada del contrato REST, distinto de la entidad JPA.
 *
 * <p>El {@code tipo} debe ser una etiqueta valida ({@code entrada}, {@code salida},
 * {@code ajuste}); una etiqueta desconocida produce 422 en la capa de aplicacion. La
 * regla de no negatividad (Req 18.3, Property 9) la aplica el dominio (422 "existencias
 * insuficientes").</p>
 *
 * @param tipo     etiqueta del tipo de movimiento; obligatoria.
 * @param cantidad cantidad del movimiento; obligatoria. Para entrada/salida positiva;
 *                 para ajuste distinta de cero (el signo indica el sentido).
 * @param motivo   nota opcional (por ejemplo razon del ajuste).
 */
public record RegistrarMovimientoRequest(
        @NotBlank String tipo,
        @NotNull BigDecimal cantidad,
        @Size(max = 500) String motivo) {
}
