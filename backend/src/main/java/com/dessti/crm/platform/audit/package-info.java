/**
 * Servicio de auditoria inmutable encadenado por hash (Req 10, Tarea 7.1).
 *
 * <p>Contiene el puerto de dominio {@link com.dessti.crm.platform.audit.AuditoriaPort}
 * (registrar/consultar/exportar), la entidad append-only
 * {@link com.dessti.crm.platform.audit.RegistroAuditoria}, su repositorio,
 * el calculo puro del hash encadenado
 * {@link com.dessti.crm.platform.audit.CalculadoraHashCadena} y la
 * implementacion {@link com.dessti.crm.platform.audit.ServicioAuditoria}.</p>
 *
 * <p>La bitacora es append-only (trigger de BD + permisos del rol de app) y usa
 * una cadena de hash SHA-256 <strong>global</strong> ordenada por {@code id}
 * para el no repudio de toda la bitacora, incluidos los eventos de plataforma
 * ({@code tenant_id} nulo).</p>
 *
 * <h2>Tarea 7.2</h2>
 * <p>Se anaden en esta misma capa:</p>
 * <ul>
 *   <li><strong>Verificacion de integridad de la cadena bajo demanda</strong>
 *       (Req 10.13): {@link com.dessti.crm.platform.audit.VerificadorCadenaAuditoria}
 *       recorre la cadena (completa o por rango de id) y reporta la primera
 *       ruptura en un {@link com.dessti.crm.platform.audit.ResultadoVerificacionCadena}.</li>
 *   <li><strong>Alertas configurables</strong> (Alerta_Auditoria, Req 10.11):
 *       {@link com.dessti.crm.platform.audit.AlertaAuditoria} y su repositorio,
 *       {@link com.dessti.crm.platform.audit.ServicioAlertasAuditoria} que
 *       detecta patrones sensibles por ventana/umbral y emite la Notificacion a
 *       traves del puerto {@link com.dessti.crm.platform.audit.NotificadorAlertasPort}
 *       (implementacion placeholder
 *       {@link com.dessti.crm.platform.audit.NotificadorAlertasRegistroLog}
 *       hasta la Tarea 43).</li>
 * </ul>
 *
 * <p>El {@code traceId} (Req 10.12) lo establece por peticion el
 * {@code TraceIdFilter} de la capa web y lo lee este servicio desde el MDC.</p>
 */
package com.dessti.crm.platform.audit;
