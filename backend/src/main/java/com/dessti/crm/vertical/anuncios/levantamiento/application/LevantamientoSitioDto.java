package com.dessti.crm.vertical.anuncios.levantamiento.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoSitio;

/**
 * DTO de salida de un {@link LevantamientoSitio} (Req 12.2, 16), distinto de la
 * entidad de persistencia. El controlador REST lo serializa; nunca se expone la
 * entidad JPA. El estado se expone como su etiqueta de negocio ({@code en_proceso},
 * {@code completado}) coherente con el Req 16.
 *
 * @param id                     identificador del Levantamiento_Sitio (Req 16.1).
 * @param sitioId                Sitio vinculado; {@code null} si no se vinculo (Req 16.2).
 * @param cotizacionId           Cotizacion vinculada; {@code null} si no se vinculo (Req 16.2).
 * @param ordenFabricacionId     Orden_Fabricacion vinculada; {@code null} si no se vinculo (Req 16.2).
 * @param mediciones             mediciones del sitio (Req 16.1).
 * @param tipoSuperficie         tipo de superficie o estructura (Req 16.1).
 * @param condicionesElectricas  condiciones electricas (Req 16.1).
 * @param estado                 etiqueta del estado (Req 16.1, 16.4).
 * @param completadoPor          actor que completo; {@code null} si en proceso (Req 16.4).
 * @param completadoEn           instante UTC de la finalizacion; {@code null} si en proceso (Req 16.4).
 * @param version                version para concurrencia optimista (Req 49).
 * @param createdAt              instante de alta (UTC).
 * @param updatedAt              instante de la ultima modificacion (UTC).
 */
public record LevantamientoSitioDto(
        UUID id,
        UUID sitioId,
        UUID cotizacionId,
        UUID ordenFabricacionId,
        String mediciones,
        String tipoSuperficie,
        String condicionesElectricas,
        String estado,
        String completadoPor,
        Instant completadoEn,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link LevantamientoSitio} a su DTO de salida.
     *
     * @param levantamiento entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static LevantamientoSitioDto de(LevantamientoSitio levantamiento) {
        return new LevantamientoSitioDto(
                levantamiento.getId(),
                levantamiento.getSitioId(),
                levantamiento.getCotizacionId(),
                levantamiento.getOrdenFabricacionId(),
                levantamiento.getMediciones(),
                levantamiento.getTipoSuperficie(),
                levantamiento.getCondicionesElectricas(),
                levantamiento.getEstado().valorBd(),
                levantamiento.getCompletadoPor(),
                levantamiento.getCompletadoEn(),
                levantamiento.getVersion(),
                levantamiento.getCreatedAt(),
                levantamiento.getUpdatedAt());
    }
}
