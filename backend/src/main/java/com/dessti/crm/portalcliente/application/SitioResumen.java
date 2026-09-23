package com.dessti.crm.portalcliente.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Resumen de un {@code Sitio} tal como lo expone el Portal del Cliente. Definido
 * por el <strong>Nucleo</strong> (el Portal) e implementado por el vertical de
 * anuncios a traves del {@link ResumenProyectosPort} (Req 10.5). Reproduce
 * exactamente la forma del {@code SitioDto} del vertical (mismos nombres de campo)
 * para preservar el contrato REST del Portal frente al frontend (Req 10.4).
 *
 * @param id         identificador del Sitio (Req 21.2).
 * @param proyectoId Proyecto al que pertenece (Req 21.2).
 * @param nombre     nombre del Sitio (Req 21.2).
 * @param direccion  direccion fisica; {@code null} si no se proporciono.
 * @param version    version para concurrencia optimista (Req 49).
 * @param createdAt  instante de alta (UTC).
 * @param updatedAt  instante de la ultima modificacion (UTC).
 */
public record SitioResumen(
        UUID id,
        UUID proyectoId,
        String nombre,
        String direccion,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
