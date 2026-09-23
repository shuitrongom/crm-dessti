/**
 * Submodulo <strong>levantamiento en sitio</strong> del modulo operacion-produccion
 * (Req 16; tarea 21.1). Establece la raiz de paquetes
 * {@code com.dessti.crm.vertical.anuncios.levantamiento}, coherente con la organizacion de
 * los demas submodulos del modulo operacion-produccion.
 *
 * <p>Gestiona el {@code Levantamiento_Sitio}: alta con datos obligatorios
 * (mediciones, tipo de superficie o estructura y condiciones electricas) y estado
 * inicial {@code en_proceso} (Req 16.1); vinculos <em>opcionales</em> a Sitio,
 * Cotizacion u Orden_Fabricacion (Req 16.2); fotografias adjuntas (Req 16.3); marca
 * {@code completado} con actor y marca temporal UTC (Req 16.4); listado paginado
 * (Req 16.6) y auditoria del alta y del cambio de estado (Req 16.7).</p>
 *
 * <h2>Guarda de programacion de instalacion (Req 16.5)</h2>
 * <p>La instalacion de un Sitio solo puede programarse si su Levantamiento_Sitio
 * esta {@code completado}. Esa guarda la expone el
 * {@code LevantamientoCompletadoPort} (implementado por
 * {@code LevantamientoCompletadoAdapter}), que el bloque 22 (instalacion, tarea
 * 22.1) consumira.</p>
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code LevantamientoSitio} (entidad y raiz de agregado),
 *       {@code LevantamientoFoto} (entidad hija de fotografias),
 *       {@code EstadoLevantamiento} (enum + maquina de estados pura
 *       {@code en_proceso -> completado}) y su convertidor JPA.</li>
 *   <li>{@code application}: {@code ServicioLevantamientos} (casos de uso), sus DTOs
 *       y comandos, el puerto {@code EnlacesLevantamientoPort} (verificacion de
 *       vinculos opcionales) y el puerto {@code LevantamientoCompletadoPort} (guarda
 *       del Req 16.5 para el bloque 22).</li>
 *   <li>{@code adapter.in.rest}: {@code LevantamientoSitioController} y sus DTOs de
 *       peticion.</li>
 *   <li>{@code adapter.out.persistence}: {@code LevantamientoSitioRepository},
 *       {@code LevantamientoFotoRepository} y los adaptadores
 *       {@code EnlacesLevantamientoAdapter} y {@code LevantamientoCompletadoAdapter}.</li>
 * </ul>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> {@code LevantamientoSitio} y
 * {@code LevantamientoFoto} extienden {@code TenantScopedEntity}; el aislamiento se
 * refuerza con RLS (V19). Los permisos {@code levantamiento_sitio:*} ya se sembraron
 * en V5 y se asignaron al rol {@code instalacion} (Req 27.6).</p>
 */
package com.dessti.crm.vertical.anuncios.levantamiento;
