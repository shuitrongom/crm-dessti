package com.dessti.crm.platform.monetizacion.application;

import com.dessti.crm.platform.monetizacion.domain.Moneda;

/** DTO de salida de una {@link Moneda}. */
public record MonedaDto(String codigo, String nombre, boolean activo) {
    public static MonedaDto de(Moneda m) {
        return new MonedaDto(m.getCodigo(), m.getNombre(), m.isActivo());
    }
}