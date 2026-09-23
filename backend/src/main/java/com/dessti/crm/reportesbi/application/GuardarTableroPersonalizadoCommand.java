package com.dessti.crm.reportesbi.application;

import java.util.List;
import java.util.UUID;

/**
 * Comando de aplicacion para crear o actualizar un tablero analitico personalizado con
 * sus widgets (Req 48.3). Es un objeto de entrada inmutable, distinto de los DTO REST y
 * de la entidad JPA, que la capa de aplicacion consume. El {@code tenant_id} y el actor
 * NUNCA viajan aqui: se derivan del contexto autenticado (Req 23.4, 48.5).
 *
 * @param nombre               nombre del tablero; obligatorio (1..200).
 * @param descripcion          descripcion; opcional (<=500).
 * @param propietarioUsuarioId Usuario propietario; opcional.
 * @param widgets              definiciones de los widgets a establecer; nunca {@code null}
 *                             (puede ser vacia).
 */
public record GuardarTableroPersonalizadoCommand(
        String nombre,
        String descripcion,
        UUID propietarioUsuarioId,
        List<WidgetCommand> widgets) {

    /**
     * Definicion de un widget dentro del comando (Req 48.3).
     *
     * @param area              etiqueta del area de la metrica; obligatoria.
     * @param metrica           clave de la metrica; obligatoria.
     * @param orden             orden de presentacion; no negativo.
     * @param configuracionJson parametros de presentacion como JSON opaco; opcional.
     */
    public record WidgetCommand(String area, String metrica, int orden, String configuracionJson) {
    }
}
