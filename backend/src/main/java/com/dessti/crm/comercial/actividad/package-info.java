/**
 * Submodulo <strong>actividades y seguimiento comercial</strong> del modulo
 * comercial-crm. Gestiona el registro y el ciclo de vida de las interacciones y
 * tareas de seguimiento con el Cliente (llamadas, correos, reuniones, tareas y
 * notas), su responsable, su calendario (fecha programada y vencimiento) y su
 * estado (pendiente/completada/cancelada), y expone el historial cronologico
 * (timeline) por Cliente y por Oportunidad, asi como el tablero de tareas
 * pendientes por responsable.
 *
 * <p>Sigue la arquitectura hexagonal del proyecto (puertos y adaptadores) y el
 * patron establecido por los submodulos {@code cliente}, {@code oportunidad} y
 * {@code cotizacion}: dominio puro, capa de aplicacion con servicios y puertos,
 * y adaptadores REST (entrada) y JPA (salida). Multi-tenant por
 * {@code TenantScopedEntity} + RLS (V79, Req 23).</p>
 */
package com.dessti.crm.comercial.actividad;
