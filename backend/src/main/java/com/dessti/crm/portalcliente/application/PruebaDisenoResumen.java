package com.dessti.crm.portalcliente.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Resumen de una {@code Prueba_Diseno} tal como lo expone el Portal del Cliente,
 * definido por el <strong>Nucleo</strong> (el Portal) e implementado por el
 * Modulo-Vertical de anuncios a traves del {@link ResumenPruebasDisenoPort}
 * (Req 10.5, 4.5). El Portal deja de depender de la entidad ni del DTO internos
 * del vertical: modela aqui, en el propio Portal, la forma que necesita.
 *
 * <p>Los campos reproducen la proyeccion publica que el Portal ya devolvia al
 * frontend (misma forma que el {@code PruebaDisenoDto} del vertical), para
 * preservar el contrato REST del Portal (Req 10.4).</p>
 *
 * @param id            identificador de la Prueba_Diseno.
 * @param cotizacionId  Cotizacion a la que pertenece (Req 15.1).
 * @param numeroVersion version de negocio (1, 2, 3, ...).
 * @param estado        etiqueta del estado ({@code pendiente}/{@code aprobada}/{@code rechazada}).
 * @param aprobadaPor   actor que aprobo; {@code null} si no aprobada.
 * @param rechazadaPor  actor que rechazo; {@code null} si no rechazada.
 * @param decididaEn    instante UTC de la decision; {@code null} si pendiente.
 * @param version       version para concurrencia optimista.
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 */
public record PruebaDisenoResumen(
        UUID id,
        UUID cotizacionId,
        int numeroVersion,
        String estado,
        String aprobadaPor,
        String rechazadaPor,
        Instant decididaEn,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
