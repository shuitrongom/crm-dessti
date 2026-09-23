package com.dessti.crm.activosfijos.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.activosfijos.domain.ActivoFijo;

/**
 * DTO de salida de un {@link ActivoFijo} (Req 12.2, 44), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 * El metodo de depreciacion y el estado se exponen como sus etiquetas de negocio.
 *
 * @param id                     identificador del Activo_Fijo (Req 44.1).
 * @param nombre                 nombre descriptivo del bien.
 * @param costo                  costo de adquisicion.
 * @param fechaAdquisicion       fecha de adquisicion.
 * @param vidaUtilMeses          vida util en meses.
 * @param metodoDepreciacion     etiqueta del metodo ({@code linea_recta}/{@code saldos_decrecientes}).
 * @param valorResidual          valor residual estimado.
 * @param depreciacionAcumulada  depreciacion acumulada a la fecha.
 * @param estado                 etiqueta del estado ({@code activo}/{@code baja}).
 * @param version                version para concurrencia optimista (Req 49).
 * @param createdAt              instante de alta (UTC).
 * @param updatedAt              instante de la ultima modificacion (UTC).
 */
public record ActivoFijoDto(
        UUID id,
        String nombre,
        BigDecimal costo,
        LocalDate fechaAdquisicion,
        int vidaUtilMeses,
        String metodoDepreciacion,
        BigDecimal valorResidual,
        BigDecimal depreciacionAcumulada,
        String estado,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ActivoFijo} a su DTO de salida.
     *
     * @param activo entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ActivoFijoDto de(ActivoFijo activo) {
        return new ActivoFijoDto(
                activo.getId(),
                activo.getNombre(),
                activo.getCosto(),
                activo.getFechaAdquisicion(),
                activo.getVidaUtilMeses(),
                activo.getMetodoDepreciacion().valorBd(),
                activo.getValorResidual(),
                activo.getDepreciacionAcumulada(),
                activo.getEstado().valorBd(),
                activo.getVersion(),
                activo.getCreatedAt(),
                activo.getUpdatedAt());
    }
}
