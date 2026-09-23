package com.dessti.crm.comercial.producto.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.comercial.producto.domain.ListaPrecios;

/**
 * DTO de salida de una {@link ListaPrecios} (Req 12.2, 59), distinto de la
 * entidad de persistencia.
 *
 * @param id              identificador de la Lista_Precios.
 * @param nombre          nombre de la lista.
 * @param prioridad       prioridad de aplicacion (mayor = antes, Req 59.9).
 * @param segmento        segmento de Cliente; {@code null} = lista general.
 * @param vigenciaInicio  inicio de la vigencia (inclusive).
 * @param vigenciaFin     fin de la vigencia (inclusive); {@code null} = abierta.
 * @param activo          {@code true} si la lista esta vigente (no dada de baja).
 * @param version         version para concurrencia optimista (Req 49).
 * @param createdAt       instante de alta (UTC).
 * @param updatedAt       instante de la ultima modificacion (UTC).
 */
public record ListaPreciosDto(
        UUID id,
        String nombre,
        int prioridad,
        String segmento,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFin,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ListaPrecios} a su DTO de salida.
     *
     * @param lista entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ListaPreciosDto de(ListaPrecios lista) {
        return new ListaPreciosDto(
                lista.getId(),
                lista.getNombre(),
                lista.getPrioridad(),
                lista.getSegmento(),
                lista.getVigenciaInicio(),
                lista.getVigenciaFin(),
                lista.isActivo(),
                lista.getVersion(),
                lista.getCreatedAt(),
                lista.getUpdatedAt());
    }
}
