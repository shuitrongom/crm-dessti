package com.dessti.crm.contabilidad.polizas.application;

import java.time.Instant;

import com.dessti.crm.contabilidad.polizas.domain.EstadoPeriodo;
import com.dessti.crm.contabilidad.polizas.domain.PeriodoContable;

/**
 * DTO de salida del estado de un {@link PeriodoContable} (cierre de periodo mensual),
 * distinto de la entidad de persistencia. El estado se expone como su etiqueta de
 * negocio ({@code abierto}/{@code cerrado}).
 *
 * <p>Un periodo sin fila materializada se representa con
 * {@link #abiertoPorDefecto(int, int)} (estado {@code abierto} y metadatos nulos).</p>
 *
 * @param anio             anio del periodo.
 * @param mes              mes del periodo (1..12).
 * @param estado           etiqueta del estado ({@code abierto}/{@code cerrado}).
 * @param fechaCierre      instante del ultimo cierre (UTC), o {@code null}.
 * @param cerradoPor       actor del ultimo cierre, o {@code null}.
 * @param fechaReapertura  instante de la ultima reapertura (UTC), o {@code null}.
 * @param reabiertoPor     actor de la ultima reapertura, o {@code null}.
 * @param motivoReapertura motivo de la ultima reapertura, o {@code null}.
 */
public record PeriodoContableDto(
        int anio,
        int mes,
        String estado,
        Instant fechaCierre,
        String cerradoPor,
        Instant fechaReapertura,
        String reabiertoPor,
        String motivoReapertura) {

    /**
     * Proyecta una entidad {@link PeriodoContable} materializada a su DTO.
     *
     * @param periodo entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PeriodoContableDto de(PeriodoContable periodo) {
        return new PeriodoContableDto(
                periodo.getAnio(),
                periodo.getMes(),
                periodo.getEstado().valorBd(),
                periodo.getFechaCierre(),
                periodo.getCerradoPor(),
                periodo.getFechaReapertura(),
                periodo.getReabiertoPor(),
                periodo.getMotivoReapertura());
    }

    /**
     * DTO de un periodo ABIERTO por defecto (sin fila materializada): estado
     * {@code abierto} y metadatos de cierre/reapertura nulos.
     *
     * @param anio anio del periodo.
     * @param mes  mes del periodo (1..12).
     * @return el DTO de un periodo abierto por defecto.
     */
    public static PeriodoContableDto abiertoPorDefecto(int anio, int mes) {
        return new PeriodoContableDto(
                anio, mes, EstadoPeriodo.ABIERTO.valorBd(), null, null, null, null, null);
    }
}
