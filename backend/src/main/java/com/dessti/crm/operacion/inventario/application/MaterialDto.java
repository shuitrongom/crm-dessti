package com.dessti.crm.operacion.inventario.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.operacion.inventario.domain.Material;

/**
 * DTO de salida de un {@link Material} (Req 12.2, 18), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 * Incluye el indicador derivado {@code stockBajo} (existencias &lt; stock minimo,
 * Req 18.5) para que el cliente distinga los Materiales por reabastecer.
 *
 * @param id           identificador del Material (Req 18.1).
 * @param nombre       nombre del Material.
 * @param unidadMedida unidad de medida.
 * @param stockMinimo  stock minimo configurado.
 * @param existencias  existencias actuales (inventario perpetuo, Req 18.2).
 * @param stockBajo    {@code true} si existencias &lt; stock minimo (Req 18.5).
 * @param activo       {@code true} si el Material esta activo (no dado de baja).
 * @param version      version para concurrencia optimista (Req 49).
 * @param createdAt    instante de alta (UTC).
 * @param updatedAt    instante de la ultima modificacion (UTC).
 */
public record MaterialDto(
        UUID id,
        String nombre,
        String unidadMedida,
        BigDecimal stockMinimo,
        BigDecimal existencias,
        boolean stockBajo,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Material} a su DTO de salida.
     *
     * @param material entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static MaterialDto de(Material material) {
        return new MaterialDto(
                material.getId(),
                material.getNombre(),
                material.getUnidadMedida(),
                material.getStockMinimo(),
                material.getExistencias(),
                material.estaEnStockBajo(),
                material.isActivo(),
                material.getVersion(),
                material.getCreatedAt(),
                material.getUpdatedAt());
    }
}
