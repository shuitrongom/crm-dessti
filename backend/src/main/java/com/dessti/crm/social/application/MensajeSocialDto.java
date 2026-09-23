package com.dessti.crm.social.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.social.domain.EstadoEntrega;
import com.dessti.crm.social.domain.MensajeSocial;

/**
 * DTO de salida de un {@link MensajeSocial} (Req 12.2, 64.4, 64.11), distinto de la
 * entidad de persistencia.
 *
 * @param id             identificador del Mensaje_Social.
 * @param conversacionId Conversacion a la que pertenece.
 * @param direccion      etiqueta del sentido (entrante/saliente).
 * @param tipo           etiqueta del tipo (texto/plantilla/interactivo).
 * @param contenido      contenido del mensaje.
 * @param esMarketing    {@code true} si es un mensaje de marketing (Req 64.8).
 * @param estadoEntrega  etiqueta del estado de entrega (salientes); {@code null} en entrantes.
 * @param externoId      id del mensaje en el proveedor; {@code null} si no aplica.
 * @param enviadoEn      instante de envio (UTC); {@code null} en entrantes.
 * @param recibidoEn     instante de recepcion (UTC); {@code null} en salientes.
 * @param version        version para concurrencia optimista (Req 49).
 * @param createdAt      instante de alta (UTC).
 * @param updatedAt      instante de la ultima modificacion (UTC).
 */
public record MensajeSocialDto(
        UUID id,
        UUID conversacionId,
        String direccion,
        String tipo,
        String contenido,
        boolean esMarketing,
        String estadoEntrega,
        String externoId,
        Instant enviadoEn,
        Instant recibidoEn,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link MensajeSocial} a su DTO de salida.
     *
     * @param mensaje entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static MensajeSocialDto de(MensajeSocial mensaje) {
        EstadoEntrega estado = mensaje.getEstadoEntrega();
        return new MensajeSocialDto(
                mensaje.getId(),
                mensaje.getConversacionId(),
                mensaje.getDireccion().valorBd(),
                mensaje.getTipo().valorBd(),
                mensaje.getContenido(),
                mensaje.isEsMarketing(),
                (estado == null) ? null : estado.valorBd(),
                mensaje.getExternoId(),
                mensaje.getEnviadoEn(),
                mensaje.getRecibidoEn(),
                mensaje.getVersion(),
                mensaje.getCreatedAt(),
                mensaje.getUpdatedAt());
    }
}
