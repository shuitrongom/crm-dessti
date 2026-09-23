/**
 * Submodulo <strong>pruebas de diseno</strong> (aprobacion de arte) del vertical
 * de anuncios luminosos (Req 15, 10.1; tarea 8.2).
 *
 * <p>Gestiona {@code Prueba_Diseno} <em>versionadas</em> vinculadas a una
 * {@code Cotizacion}: generacion con version 1 en estado {@code pendiente}
 * (Req 15.1), aprobacion/rechazo con actor y marca temporal UTC (Req 15.2, 15.3),
 * versionado <strong>monotono</strong> (el rechazo genera una nueva version con
 * numero incrementado en 1, Property 8), historial inmutable (Req 15.4), listado
 * paginado (Req 15.6) y auditoria del cambio de estado (Req 15.7).</p>
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code PruebaDiseno} (entidad y raiz de agregado, con la
 *       version de negocio {@code numero_version} distinta de la columna de
 *       concurrencia optimista {@code version}), {@code EstadoPruebaDiseno} (enum +
 *       maquina de estados pura) y su convertidor JPA.</li>
 *   <li>{@code application}: {@code ServicioPruebasDiseno} (casos de uso), DTOs, el
 *       puerto {@code CotizacionExistentePort} y el puerto
 *       {@code PruebaDisenoAprobadaPort} que consume el bloque 19
 *       (Orden_Fabricacion, Req 15.5).</li>
 *   <li>{@code adapter.in.rest}: {@code PruebaDisenoController}.</li>
 *   <li>{@code adapter.out.persistence}: {@code PruebaDisenoRepository} y los
 *       adaptadores de los puertos de salida.</li>
 * </ul>
 *
 * <p><strong>Consumo del Nucleo por puerto (Req 10.3):</strong> el flujo consulta
 * la existencia de la {@code Cotizacion} exclusivamente a traves del puerto
 * {@code com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort}
 * del Nucleo, sin acoplarse a la persistencia interna de Cotizaciones.</p>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> {@code PruebaDiseno} extiende
 * {@code TenantScopedEntity}; el aislamiento se refuerza con RLS (V16). El
 * versionado monotono se garantiza ademas con la unicidad
 * {@code (tenant_id, cotizacion_id, numero_version)} de V16.</p>
 */
package com.dessti.crm.vertical.anuncios.pruebadiseno;
