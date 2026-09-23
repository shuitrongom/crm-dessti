/**
 * Submodulo <strong>inventario de Materiales</strong> del modulo operacion-produccion
 * (Req 18; tarea 20.1). Establece la raiz de paquetes
 * {@code com.dessti.crm.operacion.inventario}, coherente con la organizacion del
 * submodulo {@code ordenfabricacion} y del modulo comercial-crm. Es el inventario BASE
 * del Req 18; el inventario AVANZADO por Almacen (Almacenes, Kardex, lotes, costeo) del
 * Req 60 (bloque 23) lo amplia.
 *
 * <p>Gestiona {@code Material} y su saldo de existencias, y el historial append-only de
 * {@code Movimiento_Inventario}: alta con existencias iniciales 0 (Req 18.1), movimientos
 * de entrada/salida/ajuste que actualizan las existencias (Req 18.2), rechazo de una
 * salida que dejaria las existencias por debajo de 0 (Req 18.3, Property 9), consumo por
 * Orden_Fabricacion (Req 18.4), notificacion de stock bajo (Req 18.5), listado paginado
 * con filtro por nombre y por stock bajo (Req 18.6) y auditoria del alta y de los
 * movimientos (Req 18.7).</p>
 *
 * <h2>No negatividad de existencias (Property 9)</h2>
 * <p>La invariante "las existencias nunca son negativas" se impone en DOS capas: el
 * dominio ({@code Material.aplicarMovimiento}) rechaza una salida/ajuste que dejaria el
 * saldo &lt; 0 con {@code ReglaNegocioException} (422, mensaje "existencias insuficientes")
 * conservando las existencias; y la BD la refuerza con {@code CHECK (existencias >= 0)} en
 * V18 como segunda capa de defensa.</p>
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code Material} (raiz de agregado con el motor puro
 *       {@code aplicarMovimiento}), {@code MovimientoInventario} (historial append-only),
 *       {@code TipoMovimientoInventario} (enum + convertidor JPA) y {@code ResultadoMovimiento}.</li>
 *   <li>{@code application}: {@code ServicioInventario} (casos de uso; implementa
 *       {@code ConsumoMaterialPort} del Req 18.4), los DTOs {@code MaterialDto}/
 *       {@code MovimientoInventarioDto}, el puerto de notificacion de stock bajo
 *       {@code NotificadorStockPort} (con la implementacion por defecto
 *       {@code NotificadorStockRegistroLog} y {@code InventarioConfig}), y
 *       {@code ConsumoMaterial}/{@code ConsumoMaterialPort} (Req 18.4).</li>
 *   <li>{@code adapter.in.rest}: {@code MaterialController} y sus DTOs de peticion.</li>
 *   <li>{@code adapter.out.persistence}: {@code MaterialRepository} y
 *       {@code MovimientoInventarioRepository}.</li>
 * </ul>
 *
 * <h2>Coordinacion</h2>
 * <ul>
 *   <li>La tarea 20.2 (Property 9) verifica la no negatividad sobre
 *       {@code Material.aplicarMovimiento} como funcion pura.</li>
 *   <li>Los bloques 19/22 (Orden_Fabricacion e instalacion) invocan
 *       {@code ConsumoMaterialPort} para descontar Materiales (Req 18.4).</li>
 *   <li>El bloque 23 (inventario avanzado: Almacenes, Kardex, costeo) amplia este
 *       inventario base.</li>
 *   <li>El bloque 43 (notificaciones) sustituira {@code NotificadorStockRegistroLog} por
 *       el adaptador real (correo/WhatsApp) del {@code NotificacionPort} (Req 18.5, 46).</li>
 * </ul>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> {@code Material} y {@code MovimientoInventario}
 * extienden {@code TenantScopedEntity}; el aislamiento se refuerza con RLS (V18).
 * <strong>Concurrencia (Req 49):</strong> la columna {@code version} de {@code Material}
 * protege las actualizaciones de existencias; un conflicto se traduce a 409.</p>
 */
package com.dessti.crm.operacion.inventario;
