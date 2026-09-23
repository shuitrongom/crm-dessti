package com.dessti.crm.platform.empresas.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para convertir un Contrato de Suscripcion a un Contrato
 * de Plan (Req 9.2). Crea un Contrato de Plan nuevo y cierra el anterior,
 * preservando la exclusividad del instrumento.
 *
 * @param planId identificador del Plan destino de la conversion; obligatorio.
 */
public record ConvertirAPlanRequest(
        @NotNull UUID planId) {
}
