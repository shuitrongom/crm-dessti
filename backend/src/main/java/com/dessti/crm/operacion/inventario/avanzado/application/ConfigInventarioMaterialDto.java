package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.operacion.inventario.avanzado.domain.ConfigInventarioMaterial;

/**
 * DTO de salida de la configuracion de inventario por Material (Req 12.2, 60),
 * distinto de la entidad de persistencia. Incluye el {@code puntoReorden} DERIVADO
 * ({@code consumo_promedio * tiempo_entrega_dias + stock_seguridad}, Req 60) para que
 * el cliente distinga los Materiales por reabastecer sin recalcularlo.
 *
 * @param id                identificador de la configuracion.
 * @param materialId        Material al que aplica (1:1).
 * @param metodoCosteo      etiqueta del metodo de costeo ({@code promedio}/{@code peps}).
 * @param stockMaximo       stock maximo configurado; {@code null} si no hay tope.
 * @param controlLote       {@code true} si el Material controla lotes.
 * @param consumoPromedio   consumo promedio por dia (parametro del punto de reorden).
 * @param tiempoEntregaDias tiempo de entrega en dias (parametro del punto de reorden).
 * @param stockSeguridad    stock de seguridad (parametro del punto de reorden).
 * @param puntoReorden      punto de reorden derivado (Req 60).
 * @param version           version para concurrencia optimista (Req 49).
 * @param createdAt         instante de alta (UTC).
 * @param updatedAt         instante de la ultima modificacion (UTC).
 */
public record ConfigInventarioMaterialDto(
        UUID id,
        UUID materialId,
        String metodoCosteo,
        BigDecimal stockMaximo,
        boolean controlLote,
        BigDecimal consumoPromedio,
        int tiempoEntregaDias,
        BigDecimal stockSeguridad,
        BigDecimal puntoReorden,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ConfigInventarioMaterial} a su DTO de salida,
     * calculando el punto de reorden derivado.
     *
     * @param config entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ConfigInventarioMaterialDto de(ConfigInventarioMaterial config) {
        return new ConfigInventarioMaterialDto(
                config.getId(),
                config.getMaterialId(),
                config.getMetodoCosteo().valorBd(),
                config.getStockMaximo(),
                config.isControlLote(),
                config.getConsumoPromedio(),
                config.getTiempoEntregaDias(),
                config.getStockSeguridad(),
                config.puntoReorden(),
                config.getVersion(),
                config.getCreatedAt(),
                config.getUpdatedAt());
    }
}
