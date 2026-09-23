package com.dessti.crm.platform.empresas;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Comando de aplicacion para definir un Plan (Req 25.1), desacoplado de la
 * entidad JPA. Rediseno {@code plataforma-multigiro}: un Plan lleva Giro, moneda
 * y precio por modulo.
 *
 * @param nombre           nombre del Plan (unico); obligatorio.
 * @param maxUsuarios      numero maximo de Usuarios; debe ser {@code >= 0}.
 * @param duracionDias     duracion del contrato del Plan en dias; debe ser
 *                         {@code > 365} (se valida en la entidad, V64).
 * @param giroId           Giro al que pertenece el Plan; obligatorio.
 * @param monedaCodigo     codigo ISO 4217 de la moneda de cotizacion; obligatorio.
 * @param preciosPorModulo mapa {@code clave -> precio} de los modulos del Plan;
 *                         se normaliza y valida en la entidad. {@code null}
 *                         equivale a un mapa vacio (Plan sin modulos).
 */
public record CrearPlanCommand(
        String nombre,
        int maxUsuarios,
        int duracionDias,
        UUID giroId,
        String monedaCodigo,
        Map<String, BigDecimal> preciosPorModulo) {
}
