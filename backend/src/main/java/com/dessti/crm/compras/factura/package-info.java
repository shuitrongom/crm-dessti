/**
 * Submodulo <strong>Facturas de Proveedor</strong> del modulo
 * compras-abastecimiento (Req 33, 23; tarea 27.2). Registra las Facturas de
 * Proveedor y las concilia contra la Orden_Compra y la mercancia recibida
 * (Conciliacion_Tres_Vias) para autorizar el pago solo cuando coinciden cantidad y
 * precio.
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code FacturaProveedor} (raiz de agregado),
 *       {@code EstadoFacturaProveedor} (enum + maquina de estados pura) con su
 *       convertidor JPA, y {@code ConciliacionTresVias} (funcion pura de la
 *       conciliacion —Property 12—).</li>
 *   <li>{@code application}: {@code ServicioFacturasProveedor} (casos de uso), DTOs,
 *       comandos y {@code ConciliacionProperties}/{@code ConciliacionConfig}
 *       (tolerancia configurable).</li>
 *   <li>{@code adapter.in.rest}: {@code FacturaProveedorController} y sus DTOs de
 *       peticion.</li>
 *   <li>{@code adapter.out.persistence}: {@code FacturaProveedorRepository}.</li>
 * </ul>
 *
 * <h2>Reglas (Req 33)</h2>
 * <p>Alta con estado inicial {@code registrada} asociada a una Orden_Compra
 * existente, con monto y folio del Proveedor (Req 33.1, 33.2). Conciliacion de tres
 * vias: cantidad facturada &lt;= recibida y precio dentro de una tolerancia
 * CONFIGURABLE ({@code crm.compras.conciliacion.tolerancia-precio}, por defecto 2%)
 * (Req 33.3); marca {@code discrepancia} (no autoriza) o {@code conciliada}
 * (habilita pago) (Req 33.4, 33.5). Maquina de estados: {@code registrada ->
 * conciliada | discrepancia}, {@code conciliada -> pagada} (Req 33.6); guarda de
 * autorizacion de pago solo desde {@code conciliada} (Req 33.7). Listado paginado
 * con filtros por Proveedor, Orden_Compra y estado (Req 33.8); auditoria del cambio
 * de estado (Req 33.9).</p>
 */
package com.dessti.crm.compras.factura;
