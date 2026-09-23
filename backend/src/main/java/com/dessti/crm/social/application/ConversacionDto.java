package com.dessti.crm.social.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.social.domain.Conversacion;

/**
 * DTO de salida de una {@link Conversacion} de la Bandeja_Unificada (Req 12.2,
 * 64.5), distinto de la entidad de persistencia.
 *
 * @param id                  identificador de la Conversacion.
 * @param cuentaCanalSocialId cuenta de canal a la que pertenece.
 * @param canal               etiqueta del Canal_Social.
 * @param remitenteExterno    identificador del remitente en el canal.
 * @param clienteId           Cliente asociado; {@code null} si no se resolvio.
 * @param contactoId          Contacto asociado; {@code null} si no se resolvio.
 * @param estado              etiqueta del estado (abierta/asignada/cerrada).
 * @param asignadoA           Usuario asignado (handover); {@code null} si sin asignar.
 * @param ultimoEntranteUtc   instante del ultimo entrante (UTC); gobierna la
 *                            Ventana_Servicio; {@code null} si no hay entrantes.
 * @param version             version para concurrencia optimista (Req 49).
 * @param createdAt           instante de alta (UTC).
 * @param updatedAt           instante de la ultima modificacion (UTC).
 */
public record ConversacionDto(
        UUID id,
        UUID cuentaCanalSocialId,
        String canal,
        String remitenteExterno,
        UUID clienteId,
        UUID contactoId,
        String estado,
        UUID asignadoA,
        Instant ultimoEntranteUtc,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Conversacion} a su DTO de salida.
     *
     * @param conversacion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ConversacionDto de(Conversacion conversacion) {
        return new ConversacionDto(
                conversacion.getId(),
                conversacion.getCuentaCanalSocialId(),
                conversacion.getCanal().valorBd(),
                conversacion.getRemitenteExterno(),
                conversacion.getClienteId(),
                conversacion.getContactoId(),
                conversacion.getEstado().valorBd(),
                conversacion.getAsignadoA(),
                conversacion.getUltimoEntranteUtc(),
                conversacion.getVersion(),
                conversacion.getCreatedAt(),
                conversacion.getUpdatedAt());
    }
}
