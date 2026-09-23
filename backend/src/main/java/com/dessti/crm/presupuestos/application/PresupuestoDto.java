package com.dessti.crm.presupuestos.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.presupuestos.domain.Presupuesto;

/**
 * DTO de salida de un {@link Presupuesto} (Req 62.1, 12.2), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 * Solo proyecta los montos ESTIMADOS; la variacion frente al real se expone aparte via
 * {@link VariacionPresupuestoDto}.
 *
 * @param id                identificador del Presupuesto (Req 62.1).
 * @param area              area funcional (Req 62.1, 62.4).
 * @param periodo           periodo 'AAAA-MM' o codigo (Req 62.1, 62.4).
 * @param ingresosEstimados ingresos estimados (escala 2).
 * @param egresosEstimados  egresos estimados (escala 2).
 * @param version           version para concurrencia optimista (Req 49).
 * @param createdAt         instante de alta (UTC).
 * @param updatedAt         instante de la ultima modificacion (UTC).
 */
public record PresupuestoDto(
        UUID id,
        String area,
        String periodo,
        BigDecimal ingresosEstimados,
        BigDecimal egresosEstimados,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Presupuesto} a su DTO de salida.
     *
     * @param presupuesto entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PresupuestoDto de(Presupuesto presupuesto) {
        return new PresupuestoDto(
                presupuesto.getId(),
                presupuesto.getArea(),
                presupuesto.getPeriodo(),
                presupuesto.getIngresosEstimados(),
                presupuesto.getEgresosEstimados(),
                presupuesto.getVersion(),
                presupuesto.getCreatedAt(),
                presupuesto.getUpdatedAt());
    }
}
