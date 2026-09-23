package com.dessti.crm.vertical.anuncios.instalacion.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.instalacion.domain.OrdenTrabajoInstalacion;

/**
 * DTO de salida de una {@link OrdenTrabajoInstalacion} (Req 12.2, 19), distinto de
 * la entidad de persistencia. El controlador REST lo serializa; nunca se expone la
 * entidad JPA. El estado se expone como su etiqueta de negocio ({@code programada},
 * {@code en_curso}, {@code completada}, {@code cancelada}) coherente con el
 * Req 19.5.
 *
 * @param id                 identificador de la Orden_Trabajo_Instalacion (Req 19.1).
 * @param ordenFabricacionId Orden_Fabricacion terminada de origen (Req 19.1).
 * @param sitioId            Sitio de la instalacion (Req 19.3).
 * @param cuadrillaId        Cuadrilla asignada; permite segmentar el listado (Req 19.7).
 * @param clienteId          Cliente de la Orden_Fabricacion; permite segmentar el
 *                           listado (Req 19.7).
 * @param fechaProgramada    fecha programada de la instalacion (Req 19.1).
 * @param estado             etiqueta del estado (Req 19.1, 19.5).
 * @param version            version para concurrencia optimista (Req 49).
 * @param createdAt          instante de alta (UTC).
 * @param updatedAt          instante de la ultima modificacion (UTC).
 */
public record OrdenTrabajoInstalacionDto(
        UUID id,
        UUID ordenFabricacionId,
        UUID sitioId,
        UUID cuadrillaId,
        UUID clienteId,
        LocalDate fechaProgramada,
        String estado,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link OrdenTrabajoInstalacion} a su DTO de salida.
     *
     * @param orden entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static OrdenTrabajoInstalacionDto de(OrdenTrabajoInstalacion orden) {
        return new OrdenTrabajoInstalacionDto(
                orden.getId(),
                orden.getOrdenFabricacionId(),
                orden.getSitioId(),
                orden.getCuadrillaId(),
                orden.getClienteId(),
                orden.getFechaProgramada(),
                orden.getEstado().valorBd(),
                orden.getVersion(),
                orden.getCreatedAt(),
                orden.getUpdatedAt());
    }
}
