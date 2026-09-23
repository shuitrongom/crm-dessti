package com.dessti.crm.operacion.proyecto.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.operacion.proyecto.domain.EstadoConsolidadoProyecto;
import com.dessti.crm.operacion.proyecto.domain.Proyecto;

/**
 * DTO de salida de un {@link Proyecto} (Req 12.2, 21), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 *
 * <p>Admite dos proyecciones segun el caso de uso:</p>
 * <ul>
 *   <li><strong>Consulta detallada (Req 21.4):</strong> {@link #consolidado} incluye
 *       el estado consolidado derivado y la lista de Sitios con su avance por fase.</li>
 *   <li><strong>Listado paginado (Req 21.5):</strong> {@link #resumen} omite el
 *       detalle costoso (estado consolidado {@code null} y lista de Sitios vacia),
 *       pues el listado no requiere derivar el avance de cada Proyecto.</li>
 * </ul>
 *
 * @param id                 identificador del Proyecto (Req 21.1).
 * @param clienteId          Cliente asociado; permite segmentar el listado (Req 21.5).
 * @param nombre             nombre del Proyecto (Req 21.1).
 * @param estadoConsolidado  etiqueta del estado consolidado derivado (Req 21.4);
 *                           {@code null} en la proyeccion de resumen del listado.
 * @param sitios             Sitios del Proyecto con su avance por fase (Req 21.3,
 *                           21.4); lista vacia en la proyeccion de resumen.
 * @param version            version para concurrencia optimista (Req 49).
 * @param createdAt          instante de alta (UTC).
 * @param updatedAt          instante de la ultima modificacion (UTC).
 */
public record ProyectoDto(
        UUID id,
        UUID clienteId,
        String nombre,
        String estadoConsolidado,
        List<SitioAvanceDto> sitios,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyeccion detallada de un Proyecto consultado (Req 21.4): incluye el estado
     * consolidado derivado y la lista de Sitios con su avance por fase.
     *
     * @param proyecto entidad del Proyecto.
     * @param estado   estado consolidado derivado de sus Sitios (Req 21.4).
     * @param sitios   Sitios del Proyecto con su avance por fase.
     * @return el DTO detallado.
     */
    public static ProyectoDto consolidado(Proyecto proyecto, EstadoConsolidadoProyecto estado,
                                          List<SitioAvanceDto> sitios) {
        return new ProyectoDto(
                proyecto.getId(),
                proyecto.getClienteId(),
                proyecto.getNombre(),
                estado.valorBd(),
                List.copyOf(sitios),
                proyecto.getVersion(),
                proyecto.getCreatedAt(),
                proyecto.getUpdatedAt());
    }

    /**
     * Proyeccion de resumen para el listado paginado (Req 21.5): omite el estado
     * consolidado ({@code null}) y la lista de Sitios (vacia), evitando derivar el
     * avance de cada Proyecto del listado.
     *
     * @param proyecto entidad del Proyecto.
     * @return el DTO de resumen.
     */
    public static ProyectoDto resumen(Proyecto proyecto) {
        return new ProyectoDto(
                proyecto.getId(),
                proyecto.getClienteId(),
                proyecto.getNombre(),
                null,
                List.of(),
                proyecto.getVersion(),
                proyecto.getCreatedAt(),
                proyecto.getUpdatedAt());
    }
}
