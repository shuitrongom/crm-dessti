package com.dessti.crm.contabilidad.polizas.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.contabilidad.polizas.domain.PolizaContable;

/**
 * DTO de salida de una {@link PolizaContable} con el desglose de sus renglones
 * (Req 12.2, 38.2, 38.3), distinto de la entidad de persistencia.
 *
 * @param id                identificador de la poliza.
 * @param fecha             fecha contable.
 * @param tipo              etiqueta del tipo de poliza.
 * @param concepto          concepto descriptivo.
 * @param origen            evento contable de origen; {@code null} si no aplica.
 * @param origenId          identificador del recurso de origen; {@code null} si no aplica.
 * @param polizaRevertidaId poliza revertida por esta poliza de reverso; {@code null} si no lo es.
 * @param totalCargos       suma de los cargos.
 * @param totalAbonos       suma de los abonos (igual a los cargos, Req 38.3).
 * @param renglones         desglose de cargos/abonos por Cuenta_Contable.
 * @param version           version para concurrencia optimista (Req 49).
 * @param createdAt         instante de alta (UTC).
 * @param updatedAt         instante de la ultima modificacion (UTC).
 */
public record PolizaContableDto(
        UUID id,
        LocalDate fecha,
        String tipo,
        String concepto,
        String origen,
        UUID origenId,
        UUID polizaRevertidaId,
        BigDecimal totalCargos,
        BigDecimal totalAbonos,
        List<MovimientoPolizaDto> renglones,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link PolizaContable} a su DTO de salida con sus
     * renglones.
     *
     * @param poliza entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PolizaContableDto de(PolizaContable poliza) {
        List<MovimientoPolizaDto> renglones = poliza.getRenglones().stream()
                .map(MovimientoPolizaDto::de)
                .toList();
        return new PolizaContableDto(
                poliza.getId(),
                poliza.getFecha(),
                poliza.getTipo().valorBd(),
                poliza.getConcepto(),
                poliza.getOrigen(),
                poliza.getOrigenId(),
                poliza.getPolizaRevertidaId(),
                poliza.getTotalCargos(),
                poliza.getTotalAbonos(),
                renglones,
                poliza.getVersion(),
                poliza.getCreatedAt(),
                poliza.getUpdatedAt());
    }
}
