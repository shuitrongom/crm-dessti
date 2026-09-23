package com.dessti.crm.platform.monetizacion.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.platform.monetizacion.domain.EmpresaModuloPrecio;

/** DTO de salida de un {@link EmpresaModuloPrecio} (precio especial por Empresa). */
public record EmpresaModuloPrecioDto(UUID id, UUID tenantId, UUID catalogoModuloId,
                                     String monedaCodigo, BigDecimal precio, long version) {
    public static EmpresaModuloPrecioDto de(EmpresaModuloPrecio p) {
        return new EmpresaModuloPrecioDto(p.getId(), p.getTenantId(), p.getCatalogoModuloId(),
                p.getMonedaCodigo(), p.getPrecio(), p.getVersion());
    }
}