package com.dessti.crm.vertical.anuncios.instalacion.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.instalacion.domain.OrdenTrabajoInstalacion;

/**
 * DTO de salida enriquecido de una {@link OrdenTrabajoInstalacion} (OTI) para la
 * consulta de detalle (Req 8.1, 8.2, 8.3; diseno §C2). Extiende la forma del
 * {@link OrdenTrabajoInstalacionDto} con la lista de
 * {@link PendienteInstalacionDto pendientes} (cada uno con su bandera
 * {@code resuelto}) y la lista de {@link EvidenciaInstalacionDto evidencias}
 * vinculadas a la OTI (listas vacias cuando no haya).
 *
 * <p>Se usa exclusivamente en {@code GET /ordenes-trabajo-instalacion/{id}}; el
 * listado ({@code GET /ordenes-trabajo-instalacion}) conserva el
 * {@link OrdenTrabajoInstalacionDto} de resumen (sin pendientes ni evidencias) para
 * no alterar su forma.</p>
 *
 * @param id                 identificador de la Orden_Trabajo_Instalacion (Req 19.1).
 * @param ordenFabricacionId Orden_Fabricacion terminada de origen (Req 19.1).
 * @param sitioId            Sitio de la instalacion (Req 19.3).
 * @param cuadrillaId        Cuadrilla asignada; permite segmentar el listado (Req 19.7).
 * @param clienteId          Cliente de la Orden_Fabricacion; permite segmentar el listado (Req 19.7).
 * @param fechaProgramada    fecha programada de la instalacion (Req 19.1).
 * @param estado             etiqueta del estado (Req 19.1, 19.5).
 * @param version            version para concurrencia optimista (Req 49).
 * @param createdAt          instante de alta (UTC).
 * @param updatedAt          instante de la ultima modificacion (UTC).
 * @param pendientes         pendientes de la Lista_Pendientes de la OTI; lista vacia si no hay (Req 8.1, 8.2).
 * @param evidencias         evidencias fotograficas adjuntas a la OTI; lista vacia si no hay (Req 8.1, 8.3).
 */
public record OrdenTrabajoInstalacionDetalleDto(
        UUID id,
        UUID ordenFabricacionId,
        UUID sitioId,
        UUID cuadrillaId,
        UUID clienteId,
        LocalDate fechaProgramada,
        String estado,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<PendienteInstalacionDto> pendientes,
        List<EvidenciaInstalacionDto> evidencias) {

    /**
     * Compone el DTO de detalle a partir de la entidad de la OTI y sus pendientes y
     * evidencias ya proyectados a sus respectivos DTOs.
     *
     * @param orden      entidad de la Orden_Trabajo_Instalacion a proyectar.
     * @param pendientes pendientes vinculados (posiblemente vacia; nunca {@code null}).
     * @param evidencias evidencias vinculadas (posiblemente vacia; nunca {@code null}).
     * @return el DTO de detalle enriquecido con sus pendientes y evidencias.
     */
    public static OrdenTrabajoInstalacionDetalleDto de(
            OrdenTrabajoInstalacion orden,
            List<PendienteInstalacionDto> pendientes,
            List<EvidenciaInstalacionDto> evidencias) {
        return new OrdenTrabajoInstalacionDetalleDto(
                orden.getId(),
                orden.getOrdenFabricacionId(),
                orden.getSitioId(),
                orden.getCuadrillaId(),
                orden.getClienteId(),
                orden.getFechaProgramada(),
                orden.getEstado().valorBd(),
                orden.getVersion(),
                orden.getCreatedAt(),
                orden.getUpdatedAt(),
                List.copyOf(pendientes),
                List.copyOf(evidencias));
    }
}
