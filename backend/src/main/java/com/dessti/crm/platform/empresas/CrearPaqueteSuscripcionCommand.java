package com.dessti.crm.platform.empresas;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Comando de aplicacion para definir un Paquete de Suscripcion (Req 3.1),
 * desacoplado de la entidad JPA. Espejo de {@link CrearPlanCommand}: un Paquete
 * lleva Giro, moneda y precio por modulo, ademas de la duracion del contrato y
 * la configuracion del periodo de prueba. Las validaciones de dominio se aplican
 * en la entidad/servicio.
 *
 * @param nombre              nombre del Paquete (unico); obligatorio.
 * @param maxUsuarios         numero maximo de Usuarios; debe ser {@code >= 0}.
 * @param duracionDias        duracion del contrato del Paquete en dias; debe ser
 *                            {@code <= 365} (se valida en la entidad).
 * @param admitePrueba        indica si el Paquete admite periodo de prueba.
 * @param duracionPruebaMeses duracion del periodo de prueba en meses; debe ser
 *                            {@code > 0} cuando {@code admitePrueba} es
 *                            {@code true} (se valida en la entidad).
 * @param giroId              Giro al que pertenece el Paquete; obligatorio.
 * @param monedaCodigo        codigo ISO 4217 de la moneda de cotizacion;
 *                            obligatorio.
 * @param preciosPorModulo    mapa {@code clave -> precio} de los modulos del
 *                            Paquete; se normaliza y valida en la entidad.
 *                            {@code null} equivale a un mapa vacio (Paquete sin
 *                            modulos).
 */
public record CrearPaqueteSuscripcionCommand(
        String nombre,
        int maxUsuarios,
        int duracionDias,
        boolean admitePrueba,
        Integer duracionPruebaMeses,
        UUID giroId,
        String monedaCodigo,
        Map<String, BigDecimal> preciosPorModulo) {
}
