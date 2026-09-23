package com.dessti.crm.operacion.proyecto.application;

import java.util.UUID;

/**
 * Comando de aplicacion para crear un Proyecto asociado a un Cliente existente
 * (Req 21.1). Es un objeto de entrada de la capa de aplicacion, independiente del
 * contrato REST. El {@code tenant_id} y el actor NO forman parte del comando: se
 * derivan del contexto autenticado (Req 23.4).
 *
 * @param clienteId identificador del Cliente al que se asocia el Proyecto;
 *                  obligatorio (Req 21.1).
 * @param nombre    nombre del Proyecto; obligatorio (1..200, Req 21.1).
 */
public record CrearProyectoCommand(UUID clienteId, String nombre) {
}
