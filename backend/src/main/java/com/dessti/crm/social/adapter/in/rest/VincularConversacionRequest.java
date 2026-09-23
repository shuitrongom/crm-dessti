package com.dessti.crm.social.adapter.in.rest;

import java.util.UUID;

/**
 * Cuerpo de la peticion para vincular una Conversacion a un Cliente existente del
 * tenant (lead social, Req 64.4, 5.1). DTO de entrada del contrato REST.
 *
 * @param clienteId identificador del Cliente a vincular; opcional (nulo no altera
 *                  el Cliente actual segun la semantica del dominio).
 */
public record VincularConversacionRequest(UUID clienteId) {
}
