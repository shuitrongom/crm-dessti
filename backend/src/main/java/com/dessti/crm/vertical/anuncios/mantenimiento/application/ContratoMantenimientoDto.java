package com.dessti.crm.vertical.anuncios.mantenimiento.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.mantenimiento.domain.ContratoMantenimiento;

/**
 * DTO de salida de un {@link ContratoMantenimiento} (Req 20.1), distinto de la
 * entidad de persistencia. El controlador REST lo serializa; nunca se expone la
 * entidad JPA. El tipo se expone como su etiqueta de negocio ({@code preventivo}/
 * {@code correctivo}).
 *
 * @param id                 identificador del Contrato_Mantenimiento (Req 20.1).
 * @param clienteId          Cliente al que pertenece el contrato (Req 20.1).
 * @param tipo               etiqueta del tipo ({@code preventivo}/{@code correctivo}).
 * @param slaRespuestaHoras  tiempo de respuesta del SLA en horas (Req 20.1).
 * @param slaResolucionHoras tiempo de resolucion del SLA en horas (Req 20.1).
 * @param activo             indica si el contrato esta vigente.
 * @param version            version para concurrencia optimista (Req 49).
 * @param createdAt          instante de alta (UTC).
 * @param updatedAt          instante de la ultima modificacion (UTC).
 */
public record ContratoMantenimientoDto(
        UUID id,
        UUID clienteId,
        String tipo,
        int slaRespuestaHoras,
        int slaResolucionHoras,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ContratoMantenimiento} a su DTO de salida.
     *
     * @param contrato entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ContratoMantenimientoDto de(ContratoMantenimiento contrato) {
        return new ContratoMantenimientoDto(
                contrato.getId(),
                contrato.getClienteId(),
                contrato.getTipo().valorBd(),
                contrato.getSlaRespuestaHoras(),
                contrato.getSlaResolucionHoras(),
                contrato.isActivo(),
                contrato.getVersion(),
                contrato.getCreatedAt(),
                contrato.getUpdatedAt());
    }
}
