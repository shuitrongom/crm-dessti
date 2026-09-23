/**
 * Modulo <strong>Recursos Humanos / Nomina</strong> (rhnomina) del CRM
 * (Req 40 y siguientes). Agrupa la gestion del personal y su nomina siguiendo el
 * patron hexagonal por submodulo del resto del sistema.
 *
 * <p>Submodulos previstos:</p>
 * <ul>
 *   <li>{@code empleado} - modulo base de RH: Empleado, Contrato_Laboral e
 *       Incidencia (tarea 34.1, Req 40).</li>
 *   <li><em>calculo de nomina</em> - Periodo_Nomina y calculo (bloque 35).</li>
 *   <li><em>organizacion</em> - Puesto, organigrama y Evaluacion_Desempeno
 *       (bloque 36).</li>
 * </ul>
 *
 * <p>Todas las entidades del modulo son multi-tenant (extienden
 * {@code TenantScopedEntity}) y estan protegidas por RBAC y Row-Level Security
 * (Req 23), con auditoria de las operaciones de creacion, modificacion y baja
 * (Req 40.7).</p>
 */
package com.dessti.crm.rhnomina;
