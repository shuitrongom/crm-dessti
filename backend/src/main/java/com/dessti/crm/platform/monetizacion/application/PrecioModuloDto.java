package com.dessti.crm.platform.monetizacion.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.platform.monetizacion.domain.PrecioModulo;

/** DTO de salida de un {@link PrecioModulo} (precio de lista por moneda). */
public record PrecioModuloDto(UUID id, UUID catalogoModuloId, String monedaCodigo,
                              BigDecimal precio, long version) {
    public static PrecioModuloDto de(PrecioModulo p) {
        return new PrecioModuloDto(p.getId(), p.getCatalogoModuloId(), p.getMonedaCodigo(),
                p.getPrecio(), p.getVersion());
    }
}