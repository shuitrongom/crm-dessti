package com.dessti.crm.reportesbi.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import com.dessti.crm.reportesbi.application.GuardarTableroPersonalizadoCommand;
import com.dessti.crm.reportesbi.application.GuardarTableroPersonalizadoCommand.WidgetCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear o actualizar un tablero analitico personalizado
 * (Req 48.3). DTO de entrada del contrato REST, distinto del comando de aplicacion y de
 * la entidad JPA. El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * @param nombre               nombre del tablero; obligatorio (1..200).
 * @param descripcion          descripcion; opcional (<=500).
 * @param propietarioUsuarioId Usuario propietario; opcional.
 * @param widgets              widgets del tablero; opcional (puede venir vacia o nula).
 */
public record GuardarTableroPersonalizadoRequest(
        @NotBlank @Size(max = 200) String nombre,
        @Size(max = 500) String descripcion,
        UUID propietarioUsuarioId,
        @Valid List<WidgetRequest> widgets) {

    /**
     * Cuerpo de un widget del tablero (Req 48.3).
     *
     * @param area              etiqueta ASCII del area; obligatoria (<=40).
     * @param metrica           clave ASCII de la metrica; obligatoria (<=80).
     * @param orden             orden de presentacion; no negativo.
     * @param configuracionJson parametros de presentacion como JSON opaco; opcional.
     */
    public record WidgetRequest(
            @NotBlank @Size(max = 40) String area,
            @NotBlank @Size(max = 80) String metrica,
            @PositiveOrZero int orden,
            String configuracionJson) {
    }

    /**
     * Convierte esta peticion en el comando de aplicacion equivalente.
     *
     * @return el {@link GuardarTableroPersonalizadoCommand} correspondiente.
     */
    public GuardarTableroPersonalizadoCommand aComando() {
        List<WidgetCommand> comandos = (widgets == null)
                ? List.of()
                : widgets.stream()
                        .map(w -> new WidgetCommand(w.area(), w.metrica(), w.orden(),
                                w.configuracionJson()))
                        .toList();
        return new GuardarTableroPersonalizadoCommand(nombre, descripcion, propietarioUsuarioId,
                comandos);
    }
}
