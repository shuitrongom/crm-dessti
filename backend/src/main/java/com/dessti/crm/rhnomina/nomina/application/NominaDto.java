package com.dessti.crm.rhnomina.nomina.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.rhnomina.nomina.domain.Nomina;

/**
 * DTO de salida de una {@link Nomina} (Req 12.2, 41), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA. El
 * estado se expone como su etiqueta de negocio ({@code borrador}, {@code calculada},
 * {@code autorizada}, {@code timbrada}, {@code pagada}) coherente con el Req 41.5.
 *
 * @param id                 identificador de la Nomina (Req 41.1).
 * @param periodoNomina      codigo del Periodo_Nomina (AAAA-MM).
 * @param estado             etiqueta del estado (Req 41.5).
 * @param totalPercepciones  total de percepciones agregado (Req 41.1).
 * @param totalDeducciones   total de deducciones agregado (Req 41.1).
 * @param totalNeto          total neto agregado (Req 41.1).
 * @param version            version para concurrencia optimista (Req 49).
 * @param createdAt          instante de alta (UTC).
 * @param updatedAt          instante de la ultima modificacion (UTC).
 */
public record NominaDto(
        UUID id,
        String periodoNomina,
        String estado,
        BigDecimal totalPercepciones,
        BigDecimal totalDeducciones,
        BigDecimal totalNeto,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Nomina} a su DTO de salida.
     *
     * @param nomina entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static NominaDto de(Nomina nomina) {
        return new NominaDto(
                nomina.getId(),
                nomina.getPeriodoNomina(),
                nomina.getEstado().valorBd(),
                nomina.getTotalPercepciones(),
                nomina.getTotalDeducciones(),
                nomina.getTotalNeto(),
                nomina.getVersion(),
                nomina.getCreatedAt(),
                nomina.getUpdatedAt());
    }
}
