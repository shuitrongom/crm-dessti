package com.dessti.crm.activosfijos.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.activosfijos.domain.Depreciacion;

/**
 * DTO de salida de una {@link Depreciacion} de periodo (Req 12.2, 44.3), distinto
 * de la entidad de persistencia.
 *
 * @param id                              identificador de la Depreciacion.
 * @param activoFijoId                    Activo_Fijo depreciado (Req 44.3).
 * @param periodo                         periodo mensual {@code 'AAAA-MM'}.
 * @param monto                           monto aplicado en el periodo.
 * @param depreciacionAcumuladaResultante acumulada tras aplicar el periodo.
 * @param polizaContableId                Poliza_Contable generada; {@code null} si no se genero.
 * @param registradaEn                    instante de registro (UTC).
 * @param version                         version para concurrencia optimista (Req 49).
 * @param createdAt                       instante de alta (UTC).
 * @param updatedAt                       instante de la ultima modificacion (UTC).
 */
public record DepreciacionDto(
        UUID id,
        UUID activoFijoId,
        String periodo,
        BigDecimal monto,
        BigDecimal depreciacionAcumuladaResultante,
        UUID polizaContableId,
        Instant registradaEn,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Depreciacion} a su DTO de salida.
     *
     * @param depreciacion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static DepreciacionDto de(Depreciacion depreciacion) {
        return new DepreciacionDto(
                depreciacion.getId(),
                depreciacion.getActivoFijoId(),
                depreciacion.getPeriodo(),
                depreciacion.getMonto(),
                depreciacion.getDepreciacionAcumuladaResultante(),
                depreciacion.getPolizaContableId(),
                depreciacion.getRegistradaEn(),
                depreciacion.getVersion(),
                depreciacion.getCreatedAt(),
                depreciacion.getUpdatedAt());
    }
}
