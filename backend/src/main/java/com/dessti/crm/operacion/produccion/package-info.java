/**
 * Submodulo <strong>ordenes de fabricacion (produccion)</strong> del Nucleo del
 * modulo {@code operacion} (Decision D1). Se ubica en la raiz de paquetes
 * {@code com.dessti.crm.operacion.produccion}, de forma coherente con
 * {@code operacion.inventario}, de modo que la Orden_Fabricacion es transversal a
 * cualquier giro. El flujo especifico de anuncios (genesis desde Cotizacion) se
 * conserva sin cambios.
 *
 * <p>Gestiona {@code Orden_Fabricacion} generadas a partir de una {@code Cotizacion}
 * <em>aprobada</em>: generacion condicionada a tres precondiciones (Property 7),
 * estado inicial {@code pendiente} (Req 7.4), maquina de estados con finales
 * {@code terminada}/{@code cancelada} (Req 7.5, 7.6), listado paginado con filtro
 * por estado y por Cliente (Req 7.7, 7.9) y auditoria del cambio de estado
 * (Req 7.10).</p>
 *
 * <h2>Precondiciones de generacion (Property 7)</h2>
 * <p>Una Orden_Fabricacion se genera <em>si y solo si</em> la Cotizacion esta en
 * estado {@code aprobada} (Req 7.1, 7.2), no tiene ya una OF vinculada (Req 7.3) y
 * tiene al menos una Prueba_Diseno {@code aprobada} (Req 15.5). El
 * {@code ServicioOrdenesFabricacion} aplica las tres en orden y devuelve las
 * excepciones correspondientes (422 / 409 / 422) sin crear ninguna OF en caso de
 * rechazo.</p>
 *
 * <h2>Consumo del Nucleo por puerto (Req 10.3, 10.5)</h2>
 * <p>El flujo consulta la {@code Cotizacion} de origen exclusivamente a traves del
 * puerto del Nucleo
 * {@code com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort}
 * (via el adaptador {@code CotizacionParaFabricacionAdapter}), sin acoplarse a la
 * persistencia interna de Cotizaciones del Nucleo, invirtiendo asi la dependencia
 * vertical&rarr;persistencia-del-nucleo.</p>
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code OrdenFabricacion} (entidad y raiz de agregado),
 *       {@code EstadoOrdenFabricacion} (enum + maquina de estados pura) y su
 *       convertidor JPA.</li>
 *   <li>{@code application}: {@code ServicioOrdenesFabricacion} (casos de uso),
 *       {@code OrdenFabricacionDto}, y el puerto {@code CotizacionParaFabricacionPort}
 *       (con su vista {@code CotizacionParaFabricacion}). Consume el puerto
 *       {@code PruebaDisenoAprobadaPort} del vertical (Req 15.5).</li>
 *   <li>{@code adapter.in.rest}: {@code OrdenFabricacionController} y sus DTOs de
 *       peticion.</li>
 *   <li>{@code adapter.out.persistence}: {@code OrdenFabricacionRepository} y los
 *       adaptadores {@code CotizacionParaFabricacionAdapter},
 *       {@code OrdenFabricacionTerminadaAdapter} y
 *       {@code OrdenFabricacionParaFacturaAdapter} (implementacion del puerto del
 *       Nucleo de facturacion, Req 10.5).</li>
 * </ul>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> {@code OrdenFabricacion} extiende
 * {@code TenantScopedEntity}; el aislamiento se refuerza con RLS (V17). La regla
 * "una OF por Cotizacion" (Req 7.3) se garantiza ademas con la unicidad
 * {@code (tenant_id, cotizacion_id)} de V17.</p>
 */
package com.dessti.crm.operacion.produccion;
