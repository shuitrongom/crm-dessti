package com.dessti.crm.reportesbi.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.reportesbi.domain.TableroPersonalizado;

/**
 * DTO de salida de un {@link TableroPersonalizado} y sus widgets (Req 48.3), distinto
 * de la entidad JPA. El controlador lo serializa; nunca se expone la entidad.
 *
 * @param id                   identificador del tablero.
 * @param nombre               nombre del tablero.
 * @param descripcion          descripcion; puede ser {@code null}.
 * @param propietarioUsuarioId Usuario que lo creo; puede ser {@code null}.
 * @param widgets              widgets del tablero, ordenados por {@code orden}.
 * @param version              version para concurrencia optimista (Req 49).
 * @param createdAt            instante de alta (UTC).
 * @param updatedAt            instante de la ultima modificacion (UTC).
 */
public record TableroPersonalizadoDto(
        UUID id,
        String nombre,
        String descripcion,
        UUID propietarioUsuarioId,
        List<WidgetTableroDto> widgets,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link TableroPersonalizado} (con sus widgets) a su DTO.
     *
     * @param tablero entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static TableroPersonalizadoDto de(TableroPersonalizado tablero) {
        List<WidgetTableroDto> widgets = tablero.getWidgets().stream()
                .map(WidgetTableroDto::de)
                .toList();
        return new TableroPersonalizadoDto(
                tablero.getId(),
                tablero.getNombre(),
                tablero.getDescripcion(),
                tablero.getPropietarioUsuarioId(),
                widgets,
                tablero.getVersion(),
                tablero.getCreatedAt(),
                tablero.getUpdatedAt());
    }
}
