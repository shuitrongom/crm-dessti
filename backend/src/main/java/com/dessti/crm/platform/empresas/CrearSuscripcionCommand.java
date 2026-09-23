package com.dessti.crm.platform.empresas;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando de aplicacion para asociar una Suscripcion entre una Empresa y un Plan
 * (Req 25.2), desacoplado de la entidad JPA. La Suscripcion se crea en estado
 * {@code activa}.
 *
 * @param tenantId       Empresa (tenant) titular; obligatorio.
 * @param planId         Plan a contratar; obligatorio.
 * @param vigenciaInicio inicio de vigencia; si es {@code null} se toma la fecha
 *                       actual.
 * @param vigenciaFin    fin de vigencia; opcional ({@code null} = sin fin). No
 *                       puede ser anterior al inicio.
 */
public record CrearSuscripcionCommand(
        UUID tenantId,
        UUID planId,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFin) {
}
