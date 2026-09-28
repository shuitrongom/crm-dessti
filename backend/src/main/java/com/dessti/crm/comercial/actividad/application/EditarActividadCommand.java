package com.dessti.crm.comercial.actividad.application;

/**
 * Comando de edicion del contenido de una {@link
 * com.dessti.crm.comercial.actividad.domain.Actividad}. Permite corregir el
 * asunto y la descripcion sin alterar el ciclo de vida (el tipo, el Cliente y la
 * Oportunidad son inmutables).
 *
 * @param asunto      nuevo asunto; obligatorio (1..200).
 * @param descripcion nueva descripcion; opcional.
 */
public record EditarActividadCommand(
        String asunto,
        String descripcion) {
}
