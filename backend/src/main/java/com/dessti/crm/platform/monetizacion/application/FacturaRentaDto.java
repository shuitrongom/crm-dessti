package com.dessti.crm.platform.monetizacion.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.platform.monetizacion.domain.FacturaRenta;

/** DTO de salida de una factura de renta con sus lineas. */
public record FacturaRentaDto(UUID id, UUID tenantId, LocalDate periodo, String monedaCodigo,
                              BigDecimal total, String estado, Instant emitidaEn,
                              List<FacturaRentaLineaDto> lineas) {
    public static FacturaRentaDto de(FacturaRenta f) {
        List<FacturaRentaLineaDto> lineas = f.getLineas().stream()
                .map(FacturaRentaLineaDto::de).toList();
        return new FacturaRentaDto(f.getId(), f.getTenantId(), f.getPeriodo(), f.getMonedaCodigo(),
                f.getTotal(), f.getEstado(), f.getEmitidaEn(), lineas);
    }
}