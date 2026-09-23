package com.dessti.crm.vertical.anuncios.levantamiento.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoFoto;
import com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoSitio;

/**
 * DTO de salida enriquecido de un {@link LevantamientoSitio} para la consulta de
 * detalle (Req 7.1, 7.2; diseno §C1). Extiende la forma del
 * {@link LevantamientoSitioDto} con la lista de {@link LevantamientoFotoDto fotos}
 * vinculadas al Levantamiento (lista vacia si no hay).
 *
 * <p>Se usa exclusivamente en {@code GET /levantamientos/{id}}; el listado
 * ({@code GET /levantamientos}) conserva el {@link LevantamientoSitioDto} de
 * resumen (sin fotos) para no alterar su forma.</p>
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
 * @param fotos                  fotografias vinculadas al Levantamiento; lista vacia si no hay (Req 7.1, 7.2).
 */
public record LevantamientoSitioDetalleDto(
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
        Instant updatedAt,
        List<LevantamientoFotoDto> fotos) {

    /**
     * Compone el DTO de detalle a partir de la entidad del Levantamiento y las
     * fotografias vinculadas ya proyectadas a su DTO.
     *
     * @param levantamiento entidad del Levantamiento_Sitio a proyectar.
     * @param fotos         fotografias vinculadas (posiblemente vacia; nunca {@code null}).
     * @return el DTO de detalle enriquecido con sus fotos.
     */
    public static LevantamientoSitioDetalleDto de(LevantamientoSitio levantamiento,
                                                  List<LevantamientoFotoDto> fotos) {
        return new LevantamientoSitioDetalleDto(
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
                levantamiento.getUpdatedAt(),
                List.copyOf(fotos));
    }
}
