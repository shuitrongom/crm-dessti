package com.dessti.crm.platform.empresas.rest;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para definir un Paquete de Suscripcion (Req 3.1).
 * Rediseno {@code plan-vs-suscripcion-contratacion}: es el <strong>espejo</strong>
 * de {@link CrearPlanRequest} para el catalogo de <strong>contratos de corto
 * plazo</strong> (duracion de un año o menos) con opcion de periodo de prueba.
 * Un Paquete pertenece a un Giro, se cotiza en una moneda y lleva un precio por
 * modulo; las claves de los modulos habilitados se derivan de las claves de
 * {@code preciosModulos} (no hay un campo separado).
 *
 * @param nombre              nombre del Paquete (unico); obligatorio.
 * @param maxUsuarios         numero maximo de Usuarios; {@code >= 0}.
 * @param duracionDias        duracion del contrato en dias; {@code 1..365}
 *                            (un año o menos; el dominio exige {@code <= 365}).
 * @param admitePrueba        indica si el Paquete admite periodo de prueba.
 * @param duracionPruebaMeses duracion del periodo de prueba en meses; opcional
 *                            ({@code null} cuando no admite prueba). Si se indica
 *                            debe ser {@code >= 1}; la coherencia con
 *                            {@code admitePrueba} se valida en el dominio.
 * @param giroId              Giro al que pertenece el Paquete; obligatorio.
 * @param monedaCodigo        codigo ISO 4217 de la moneda; obligatorio (3 letras).
 * @param preciosModulos      mapa {@code clave -> precio} de los modulos del
 *                            Paquete; obligatorio (puede ir vacio). Se normaliza
 *                            y valida en el dominio.
 */
public record CrearPaqueteSuscripcionRequest(
        @NotBlank @Size(max = 120) String nombre,
        @Min(0) int maxUsuarios,
        @Min(1) @Max(365) int duracionDias,
        boolean admitePrueba,
        @Min(1) Integer duracionPruebaMeses,
        @NotNull UUID giroId,
        @NotBlank @Size(min = 3, max = 3) String monedaCodigo,
        @NotNull Map<@NotBlank @Size(max = 60) String, @NotNull BigDecimal> preciosModulos) {
}
