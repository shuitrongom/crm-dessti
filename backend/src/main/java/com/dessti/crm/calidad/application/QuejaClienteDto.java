package com.dessti.crm.calidad.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.calidad.domain.QuejaCliente;

/**
 * DTO de salida de una {@link QuejaCliente} (Req 12.2, 70.1), distinto de la entidad de
 * persistencia.
 *
 * @param id                 identificador de la Queja_Cliente.
 * @param clienteId          Cliente asociado.
 * @param origen             etiqueta del origen (portal/social/correo/telefono/otro).
 * @param canalSocialId      referencia al canal social de origen; {@code null} si no aplica.
 * @param descripcion        descripcion de la queja.
 * @param estado             etiqueta del estado (registrada/vinculada/atendida).
 * @param accionCorrectivaId Accion_Correctiva vinculada; {@code null} si no se vinculo.
 * @param registradaEn       instante de registro (UTC).
 * @param version            version para concurrencia optimista (Req 49).
 * @param createdAt          instante de alta (UTC).
 * @param updatedAt          instante de la ultima modificacion (UTC).
 */
public record QuejaClienteDto(
        UUID id,
        UUID clienteId,
        String origen,
        UUID canalSocialId,
        String descripcion,
        String estado,
        UUID accionCorrectivaId,
        Instant registradaEn,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link QuejaCliente} a su DTO de salida.
     *
     * @param queja entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static QuejaClienteDto de(QuejaCliente queja) {
        return new QuejaClienteDto(
                queja.getId(),
                queja.getClienteId(),
                queja.getOrigen().valorBd(),
                queja.getCanalSocialId(),
                queja.getDescripcion(),
                queja.getEstado().valorBd(),
                queja.getAccionCorrectivaId(),
                queja.getRegistradaEn(),
                queja.getVersion(),
                queja.getCreatedAt(),
                queja.getUpdatedAt());
    }
}
