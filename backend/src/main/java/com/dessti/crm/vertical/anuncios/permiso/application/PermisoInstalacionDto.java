package com.dessti.crm.vertical.anuncios.permiso.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.permiso.domain.PermisoInstalacion;

/**
 * DTO de salida de un {@link PermisoInstalacion} (Req 12.2, 17), distinto de la
 * entidad de persistencia. El controlador REST lo serializa; nunca se expone la
 * entidad JPA. El tipo y el estado se exponen como sus etiquetas de negocio
 * ({@code municipal}/{@code arrendador}; {@code solicitado}/{@code aprobado}/
 * {@code rechazado}) coherentes con el Req 17.
 *
 * @param id               identificador del Permiso_Instalacion (Req 17.1).
 * @param sitioId          Sitio vinculado (Req 17.1); {@code null} si no se registro.
 * @param tipo             etiqueta del tipo (Req 17.1).
 * @param fechaVencimiento fecha de vencimiento (Req 17.1).
 * @param estado           etiqueta del estado (Req 17.1, 17.2).
 * @param decididoPor      actor que decidio; {@code null} si sigue solicitado (Req 17.2).
 * @param decididoEn       instante UTC de la decision; {@code null} si sigue solicitado (Req 17.2).
 * @param version          version para concurrencia optimista (Req 49).
 * @param createdAt        instante de alta (UTC).
 * @param updatedAt        instante de la ultima modificacion (UTC).
 */
public record PermisoInstalacionDto(
        UUID id,
        UUID sitioId,
        String tipo,
        LocalDate fechaVencimiento,
        String estado,
        String decididoPor,
        Instant decididoEn,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link PermisoInstalacion} a su DTO de salida.
     *
     * @param permiso entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PermisoInstalacionDto de(PermisoInstalacion permiso) {
        return new PermisoInstalacionDto(
                permiso.getId(),
                permiso.getSitioId(),
                permiso.getTipo().valorBd(),
                permiso.getFechaVencimiento(),
                permiso.getEstado().valorBd(),
                permiso.getDecididoPor(),
                permiso.getDecididoEn(),
                permiso.getVersion(),
                permiso.getCreatedAt(),
                permiso.getUpdatedAt());
    }
}
