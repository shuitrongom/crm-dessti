package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.operacion.inventario.avanzado.domain.AlertaInventario;

/**
 * DTO de salida de una {@link AlertaInventario} (Req 60), distinto de la entidad de
 * persistencia. El {@code tipo} se expone como su etiqueta ASCII
 * ({@code minimo|maximo|reabastecimiento}) para el contrato REST.
 *
 * @param id             identificador de la alerta.
 * @param tipo           tipo de la alerta ({@code minimo|maximo|reabastecimiento}).
 * @param almacenId      Almacen afectado.
 * @param materialId     Material afectado.
 * @param nombreMaterial nombre del Material al detectar la alerta, o {@code null}.
 * @param cantidad       saldo en el momento de la deteccion.
 * @param umbral         umbral cruzado (punto de reorden o stock maximo).
 * @param atendida       estado de seguimiento.
 * @param detectadaEn    instante de la deteccion (UTC).
 */
public record AlertaInventarioDto(
        UUID id,
        String tipo,
        UUID almacenId,
        UUID materialId,
        String nombreMaterial,
        BigDecimal cantidad,
        BigDecimal umbral,
        boolean atendida,
        Instant detectadaEn) {

    /**
     * Proyecta una entidad {@link AlertaInventario} a su DTO de salida.
     *
     * @param alerta entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static AlertaInventarioDto de(AlertaInventario alerta) {
        return new AlertaInventarioDto(
                alerta.getId(),
                alerta.getTipo().valorBd(),
                alerta.getAlmacenId(),
                alerta.getMaterialId(),
                alerta.getNombreMaterial(),
                alerta.getCantidad(),
                alerta.getUmbral(),
                alerta.isAtendida(),
                alerta.getDetectadaEn());
    }
}
