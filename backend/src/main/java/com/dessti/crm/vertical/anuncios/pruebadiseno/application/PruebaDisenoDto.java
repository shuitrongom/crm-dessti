package com.dessti.crm.vertical.anuncios.pruebadiseno.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.PruebaDiseno;

/**
 * DTO de salida de una {@link PruebaDiseno} (Req 12.2, 15), distinto de la entidad
 * de persistencia. El controlador REST lo serializa; nunca se expone la entidad
 * JPA. El estado se expone como su etiqueta de negocio ({@code pendiente},
 * {@code aprobada}, {@code rechazada}) coherente con el Req 15.
 *
 * @param id            identificador de la Prueba_Diseno.
 * @param cotizacionId  Cotizacion a la que pertenece (Req 15.1).
 * @param numeroVersion version de negocio (1, 2, 3, ...); distinta de {@code version} (Req 15.1, 15.3).
 * @param estado        etiqueta del estado (Req 15).
 * @param aprobadaPor   actor que aprobo; {@code null} si no aprobada (Req 15.2).
 * @param rechazadaPor  actor que rechazo; {@code null} si no rechazada (Req 15.3).
 * @param decididaEn    instante UTC de la decision; {@code null} si pendiente (Req 15.2, 15.3).
 * @param version       version para concurrencia optimista (Req 49); NO es la version de negocio.
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 */
public record PruebaDisenoDto(
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

    /**
     * Proyecta una entidad {@link PruebaDiseno} a su DTO de salida.
     *
     * @param prueba entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PruebaDisenoDto de(PruebaDiseno prueba) {
        return new PruebaDisenoDto(
                prueba.getId(),
                prueba.getCotizacionId(),
                prueba.getNumeroVersion(),
                prueba.getEstado().valorBd(),
                prueba.getAprobadaPor(),
                prueba.getRechazadaPor(),
                prueba.getDecididaEn(),
                prueba.getVersion(),
                prueba.getCreatedAt(),
                prueba.getUpdatedAt());
    }
}
