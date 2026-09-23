/**
 * Submodulo <strong>Catalogo de Productos y Listas de Precios</strong> del
 * modulo comercial-crm (Req 59, 23). Replica el patron hexagonal por submodulo
 * establecido por {@code com.dessti.crm.comercial.cliente}:
 *
 * <ul>
 *   <li>{@code domain}                   - entidades {@code Producto},
 *       {@code ListaPrecios} y {@code PrecioProducto}, con sus reglas de
 *       validacion ({@code CatalogoValidaciones}), borrado logico y el rango de
 *       precios 0.01..999,999,999.99 (Req 59.3, 59.10).</li>
 *   <li>{@code application}              - servicios de aplicacion
 *       {@code ServicioProductos}, {@code ServicioListasPrecios} y
 *       {@code ServicioSeleccionPrecio}; DTOs y comandos. Expone el puerto
 *       {@code SugerenciaPrecioPort} que el submodulo de Cotizaciones (bloque
 *       17) consume para sugerir el precio de una Partida_Cotizacion (Req 59.4)
 *       sin crear un ciclo de dependencias.</li>
 *   <li>{@code adapter.out.persistence}  - repositorios Spring Data JPA.</li>
 *   <li>{@code adapter.in.rest}          - controladores REST del catalogo,
 *       guardados por RBAC (Req 59.7).</li>
 * </ul>
 *
 * <p><strong>Seleccion de precio (Req 59.9):</strong> ante varias
 * {@code ListaPrecios} vigentes aplicables, se elige la de MAYOR prioridad y se
 * prefiere la lista <em>especifica del segmento</em> del Cliente sobre la
 * general. Solo participan las listas vigentes a la fecha indicada.</p>
 */
package com.dessti.crm.comercial.producto;
