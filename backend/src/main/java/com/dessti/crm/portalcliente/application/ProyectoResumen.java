package com.dessti.crm.portalcliente.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resumen de un {@code Proyecto} tal como lo expone el Portal del Cliente.
 * Definido por el <strong>Nucleo</strong> (el Portal) e implementado por el
 * vertical de anuncios a traves del {@link ResumenProyectosPort} (Req 10.5).
 * Reproduce exactamente la forma del {@code ProyectoDto} del vertical (mismos
 * nombres de campo) para preservar el contrato REST del Portal frente al frontend
 * (Req 10.4).
 *
 * <p>Admite dos proyecciones, igual que el DTO del vertical: el listado paginado
 * (Req 21.5) usa la proyeccion de resumen ({@code estadoConsolidado} nulo y
 * {@code sitios} vacio); la consulta detallada (Req 21.4) incluye el estado
 * consolidado derivado y los Sitios con su avance por fase.</p>
 *
 * @param id                identificador del Proyecto (Req 21.1).
 * @param clienteId         Cliente asociado; el Portal lo usa como guarda de
 *                          propiedad (Req 45.3).
 * @param nombre            nombre del Proyecto (Req 21.1).
 * @param estadoConsolidado etiqueta del estado consolidado derivado (Req 21.4);
 *                          {@code null} en la proyeccion de resumen del listado.
 * @param sitios            Sitios del Proyecto con su avance por fase (Req 21.3,
 *                          21.4); lista vacia en la proyeccion de resumen.
 * @param version           version para concurrencia optimista (Req 49).
 * @param createdAt         instante de alta (UTC).
 * @param updatedAt         instante de la ultima modificacion (UTC).
 */
public record ProyectoResumen(
        UUID id,
        UUID clienteId,
        String nombre,
        String estadoConsolidado,
        List<SitioAvanceResumen> sitios,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
