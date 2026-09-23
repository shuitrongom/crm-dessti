package com.dessti.crm.platform.monetizacion.application;

import java.util.UUID;

import com.dessti.crm.platform.monetizacion.domain.CatalogoModulo;

/** DTO de salida de un {@link CatalogoModulo}. */
public record CatalogoModuloDto(UUID id, String clave, String nombre, String descripcion,
                                boolean activo, long version) {
    public static CatalogoModuloDto de(CatalogoModulo m) {
        return new CatalogoModuloDto(m.getId(), m.getClave(), m.getNombre(), m.getDescripcion(),
                m.isActivo(), m.getVersion());
    }
}