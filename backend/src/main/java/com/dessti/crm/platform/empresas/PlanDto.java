package com.dessti.crm.platform.empresas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO de salida de un {@link Plan} (Req 25), distinto de la entidad de
 * persistencia. Rediseno {@code plataforma-multigiro}: expone el Giro, la moneda
 * de cotizacion, el precio por modulo y el total (suma de precios), ademas de
 * los limites del Plan.
 *
 * @param id                 identificador del Plan.
 * @param nombre             nombre del Plan (unico).
 * @param maxUsuarios        numero maximo de Usuarios del Plan (Req 25.1, 25.3).
 * @param duracionDias       duracion del contrato del Plan en dias; siempre
 *                           {@code > 365} (un contrato de un año o menos es una
 *                           Suscripcion) (V64).
 * @param giroId             Giro al que pertenece el Plan; {@code null} en Planes
 *                           legado anteriores al rediseno.
 * @param monedaCodigo       codigo ISO 4217 de la moneda de cotizacion; {@code null}
 *                           en Planes legado.
 * @param preciosModulos     precio por modulo del Plan ({@code clave -> precio}).
 * @param total              total del Plan: suma de los precios de sus modulos
 *                           (escala 2).
 * @param modulosHabilitados modulos habilitados por el Plan (claves de
 *                           {@code preciosModulos}) (Req 25.1, 25.4).
 * @param version            version para bloqueo optimista (Req 49).
 * @param createdAt          instante de alta (UTC).
 * @param updatedAt          instante de la ultima modificacion (UTC).
 */
public record PlanDto(
        UUID id,
        String nombre,
        int maxUsuarios,
        int duracionDias,
        UUID giroId,
        String monedaCodigo,
        Map<String, BigDecimal> preciosModulos,
        BigDecimal total,
        List<String> modulosHabilitados,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Plan} a su DTO de salida.
     *
     * @param plan entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PlanDto de(Plan plan) {
        return new PlanDto(
                plan.getId(),
                plan.getNombre(),
                plan.getMaxUsuarios(),
                plan.getDuracionDias(),
                plan.getGiroId(),
                plan.getMonedaCodigo(),
                plan.getPreciosModulos(),
                plan.getTotal(),
                plan.getModulosHabilitados(),
                plan.getVersion(),
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }
}
