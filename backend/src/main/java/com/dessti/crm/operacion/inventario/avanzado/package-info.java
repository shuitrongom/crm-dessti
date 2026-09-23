/**
 * Submodulo <strong>inventario avanzado por Almacen</strong> del modulo
 * operacion-produccion (Req 60; bloque 23). Amplia el inventario BASE del Req 18
 * (paquete {@code com.dessti.crm.operacion.inventario}) con la gestion por Almacen:
 * Almacenes, existencias por Almacen, Kardex por Almacen (read-model), lotes, capas de
 * costo PEPS, la configuracion de inventario por Material (metodo de costeo, stock
 * maximo, punto de reorden derivado y control de lote) y las notificaciones de stock
 * minimo/maximo y reabastecimiento. Se apoya en el esquema de la migracion V26.
 *
 * <h2>Reparto entre tareas 23.1 y 23.2</h2>
 * <p>La migracion V26 crea TODO el esquema avanzado de una sola vez (incluidas las tablas
 * {@code capa_costo} y las columnas de costeo/transferencia de {@code movimiento_almacen})
 * para que la tarea 23.2 NO requiera migraciones adicionales. El reparto de la LOGICA es:</p>
 * <ul>
 *   <li><strong>Tarea 23.1 (este entregable):</strong> CRUD de Almacenes, configuracion de
 *       inventario por Material (upsert con punto de reorden derivado), listado de
 *       existencias por Almacen, Kardex por Almacen de SOLO LECTURA y los helpers de
 *       evaluacion de notificaciones (min/max/reabastecimiento). Se definen ademas las
 *       entidades y repositorios de {@code Lote} y {@code CapaCosto} y el enum
 *       {@code MetodoCosteo}, listos para 23.2, sin implementar aun su logica.</li>
 *   <li><strong>Tarea 23.2 (posterior):</strong> el motor de costeo (promedio ponderado y
 *       PEPS con consumo de capas), el uso efectivo de lotes en los movimientos y las
 *       transferencias entre Almacenes; el registro de movimientos reutilizara el helper
 *       {@code evaluarNotificaciones} de {@code ServicioInventarioAvanzado} de 23.1.</li>
 * </ul>
 *
 * <h2>Decision de diseno: configuracion 1:1 (no modificar el Req 18)</h2>
 * <p>La configuracion avanzada por Material se mantiene en la tabla 1:1
 * {@code config_inventario_material}, SEPARADA de {@code material}, para NO modificar la
 * tabla del Req 18 (V18). Asi el inventario base permanece intacto y el avanzado se activa
 * por Material bajo demanda.</p>
 *
 * <h2>Escalas y costeo</h2>
 * <p>Las cantidades usan {@code NUMERIC(18,3)} (BigDecimal escala 3) y los costos
 * {@code NUMERIC(18,4)} (BigDecimal escala 4); todo redondeo en el dominio es HALF_UP. El
 * Kardex {@code movimiento_almacen} es append-only y coexiste con el
 * {@code movimiento_inventario} del Req 18 (historial por Material sin costo ni Almacen).</p>
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code Almacen}, {@code ExistenciaAlmacen},
 *       {@code ConfigInventarioMaterial} (con el punto de reorden derivado), {@code Lote},
 *       {@code CapaCosto}, {@code MovimientoAlmacen} (Kardex) y los enums {@code MetodoCosteo}
 *       y {@code TipoMovimientoAlmacen} con sus convertidores JPA.</li>
 *   <li>{@code application}: {@code ServicioInventarioAvanzado} (casos de uso 23.1), los DTOs
 *       de salida, los comandos de entrada, el puerto de notificacion
 *       {@code NotificadorInventarioAvanzadoPort} (con su placeholder de log
 *       {@code NotificadorInventarioAvanzadoRegistroLog} y {@code InventarioAvanzadoConfig})
 *       y los payloads de notificacion.</li>
 *   <li>{@code adapter.in.rest}: {@code InventarioAvanzadoController} y sus DTOs de peticion.</li>
 *   <li>{@code adapter.out.persistence}: los repositorios Spring Data JPA.</li>
 * </ul>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> todas las entidades extienden
 * {@code TenantScopedEntity}; el aislamiento se refuerza con RLS (V26).
 * <strong>Concurrencia (Req 49):</strong> la columna {@code version} protege las
 * actualizaciones; un conflicto se traduce a 409. <strong>Notificaciones (Req 60):</strong>
 * el bloque 43 sustituira el placeholder de log por el adaptador real (correo/WhatsApp).</p>
 */
package com.dessti.crm.operacion.inventario.avanzado;
