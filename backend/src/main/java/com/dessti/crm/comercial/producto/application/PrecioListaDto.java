package com.dessti.crm.comercial.producto.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository.PrecioDeLista;

/**
 * DTO de salida de un precio asignado dentro de una Lista_Precios, enriquecido
 * con el NOMBRE del Producto para mostrarlo en la UI (bugfix: dar visibilidad al
 * precio guardado). El Usuario nunca ve UUIDs; ve el nombre del Producto y su
 * precio.
 *
 * @param productoId     id del Producto (uso interno).
 * @param productoNombre nombre del Producto.
 * @param precio         precio asignado (escala 2).
 */
public record PrecioListaDto(
        UUID productoId,
        String productoNombre,
        BigDecimal precio) {

    /** Proyecta la fila de repositorio {@link PrecioDeLista} al DTO. */
    public static PrecioListaDto de(PrecioDeLista fila) {
        return new PrecioListaDto(fila.productoId(), fila.productoNombre(), fila.precio());
    }
}
