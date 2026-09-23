package com.dessti.crm.platform.empresas.rest;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para definir un Plan (Req 25.1). Rediseno
 * {@code plataforma-multigiro}: un Plan pertenece a un Giro, se cotiza en una
 * moneda y lleva un precio por modulo. Las claves de los modulos habilitados se
 * derivan de las claves de {@code preciosModulos} (no hay un campo separado).
 *
 * @param nombre         nombre del Plan (unico); obligatorio.
 * @param maxUsuarios    numero maximo de Usuarios; {@code >= 0}.
 * @param duracionDias   duracion del contrato del Plan en dias; {@code >= 366}
 *                       (mas de un año; el dominio exige {@code > 365}).
 * @param giroId         Giro al que pertenece el Plan; obligatorio.
 * @param monedaCodigo   codigo ISO 4217 de la moneda; obligatorio (3 letras).
 * @param preciosModulos mapa {@code clave -> precio} de los modulos del Plan;
 *                       obligatorio (puede ir vacio). Se normaliza y valida en el
 *                       dominio.
 */
public record CrearPlanRequest(
        @NotBlank @Size(max = 120) String nombre,
        @Min(0) int maxUsuarios,
        @Min(366) int duracionDias,
        @NotNull UUID giroId,
        @NotBlank @Size(min = 3, max = 3) String monedaCodigo,
        @NotNull Map<@NotBlank @Size(max = 60) String, @NotNull BigDecimal> preciosModulos) {
}
