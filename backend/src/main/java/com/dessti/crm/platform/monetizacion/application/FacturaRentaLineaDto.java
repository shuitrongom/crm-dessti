package com.dessti.crm.platform.monetizacion.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.platform.monetizacion.domain.FacturaRentaLinea;

/** DTO de salida de una linea de la factura de renta. */
public record FacturaRentaLineaDto(UUID id, String moduloClave, String moduloNombre,
                                   BigDecimal precioAplicado) {
    public static FacturaRentaLineaDto de(FacturaRentaLinea l) {
        return new FacturaRentaLineaDto(l.getId(), l.getModuloClave(), l.getModuloNombre(),
                l.getPrecioAplicado());
    }
}