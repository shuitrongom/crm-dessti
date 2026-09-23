package com.dessti.crm.calidad.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.calidad.domain.AccionCorrectiva;

/**
 * DTO de salida de una {@link AccionCorrectiva} (Req 12.2, 70.2), distinto de la
 * entidad de persistencia.
 *
 * @param id                   identificador de la Accion_Correctiva.
 * @param noConformidadId      No_Conformidad de origen; {@code null} si no procede de una.
 * @param responsableId        Usuario responsable.
 * @param causaRaiz            causa raiz identificada.
 * @param accionesPlanificadas acciones planificadas.
 * @param evidenciaCierre      evidencia de cierre; {@code null} si no se ha cerrado.
 * @param eficaciaVerificada   indicador de eficacia verificada (guarda de cierre).
 * @param estado               etiqueta del estado.
 * @param cerradaEn            instante de cierre (UTC); {@code null} si no esta cerrada.
 * @param version              version para concurrencia optimista (Req 49).
 * @param createdAt            instante de alta (UTC).
 * @param updatedAt            instante de la ultima modificacion (UTC).
 */
public record AccionCorrectivaDto(
        UUID id,
        UUID noConformidadId,
        UUID responsableId,
        String causaRaiz,
        String accionesPlanificadas,
        String evidenciaCierre,
        boolean eficaciaVerificada,
        String estado,
        Instant cerradaEn,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link AccionCorrectiva} a su DTO de salida.
     *
     * @param accion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static AccionCorrectivaDto de(AccionCorrectiva accion) {
        return new AccionCorrectivaDto(
                accion.getId(),
                accion.getNoConformidadId(),
                accion.getResponsableId(),
                accion.getCausaRaiz(),
                accion.getAccionesPlanificadas(),
                accion.getEvidenciaCierre(),
                accion.isEficaciaVerificada(),
                accion.getEstado().valorBd(),
                accion.getCerradaEn(),
                accion.getVersion(),
                accion.getCreatedAt(),
                accion.getUpdatedAt());
    }
}
