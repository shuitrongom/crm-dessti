package com.dessti.crm.rhnomina.empleado.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de registro de una
 * {@link com.dessti.crm.rhnomina.empleado.domain.Incidencia} para un Empleado en
 * un Periodo_Nomina (Req 40.3). Objeto de entrada de la capa de aplicacion,
 * distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> este comando NO incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * @param empleadoId    Empleado al que se vincula; obligatorio.
 * @param periodoNomina codigo del Periodo_Nomina (AAAA-MM); obligatorio.
 * @param tipo          tipo de incidencia (etiqueta ASCII); obligatorio.
 * @param cantidad      cantidad asociada (horas/dias); opcional.
 * @param descripcion   nota opcional (<= 500 caracteres).
 */
public record RegistrarIncidenciaCommand(
        UUID empleadoId,
        String periodoNomina,
        String tipo,
        BigDecimal cantidad,
        String descripcion) {
}
