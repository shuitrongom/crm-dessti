package com.dessti.crm.platform.empresas.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para reasignar el Giro de una Empresa (Req 3).
 *
 * <p>Solo transporta el identificador del nuevo Giro; toda la validacion de
 * negocio (Giro activo, rechazo si la Empresa tiene datos del vertical actual y
 * auditoria del giro anterior/nuevo) la impone {@code ServicioEmpresas.cambiarGiro},
 * no este contrato REST.</p>
 *
 * @param giroId identificador del nuevo Giro a asignar (obligatorio).
 */
public record CambiarGiroRequest(@NotNull UUID giroId) {
}
