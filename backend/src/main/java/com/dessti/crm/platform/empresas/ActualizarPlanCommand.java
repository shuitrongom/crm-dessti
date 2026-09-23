package com.dessti.crm.platform.empresas;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Comando de aplicacion para actualizar un Plan (Req 25.1), desacoplado de la
 * entidad JPA. Rediseno {@code plataforma-multigiro}: un Plan lleva Giro, moneda
 * y precio por modulo.
 *
 * @param nombre           nuevo nombre del Plan (unico); obligatorio.
 * @param maxUsuarios      nuevo maximo de Usuarios; debe ser {@code >= 0}.
 * @param duracionDias     nueva duracion del contrato del Plan en dias; debe ser
 *                         {@code > 365} (se valida en la entidad, V64).
 * @param giroId           nuevo Giro del Plan; obligatorio.
 * @param monedaCodigo     nuevo codigo ISO 4217 de la moneda; obligatorio.
 * @param preciosPorModulo nuevo mapa {@code clave -> precio}; se normaliza y
 *                         valida en la entidad. {@code null} equivale a un mapa
 *                         vacio (Plan sin modulos).
 */
public record ActualizarPlanCommand(
        String nombre,
        int maxUsuarios,
        int duracionDias,
        UUID giroId,
        String monedaCodigo,
        Map<String, BigDecimal> preciosPorModulo) {
}
