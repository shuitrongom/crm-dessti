/**
 * Submodulo <strong>Ordenes de Compra</strong> del modulo compras-abastecimiento
 * (Req 31, 23; tarea 26.3). Gestiona {@code Orden_Compra} con partidas y total,
 * analoga a la {@code Cotizacion} del modulo comercial-crm.
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code OrdenCompra} (raiz de agregado),
 *       {@code PartidaOrdenCompra}, {@code EstadoOrdenCompra} (enum + maquina de
 *       estados pura) con su convertidor JPA, y {@code OrdenCompraValidaciones}
 *       (rangos y aritmetica monetaria half-up).</li>
 *   <li>{@code application}: {@code ServicioOrdenesCompra} (casos de uso), DTOs,
 *       comandos y los puertos {@code ProveedorExistentePort} /
 *       {@code MaterialExistentePort}.</li>
 *   <li>{@code adapter.in.rest}: {@code OrdenCompraController} y sus DTOs de
 *       peticion.</li>
 *   <li>{@code adapter.out.persistence}: {@code OrdenCompraRepository} y los
 *       adaptadores de los puertos de Proveedor y Material.</li>
 * </ul>
 *
 * <h2>Reglas (Req 31)</h2>
 * <p>Alta con Proveedor existente y 1..500 partidas (cantidad 1..999999, precio
 * 0.01..999999999.99); subtotal = round(cantidad*precio, 2) half-up y total =
 * round(Σ subtotales, 2) half-up (Req 31.3). Estado inicial {@code abierta}
 * (Req 31.4); maquina de estados con finales {@code cerrada}/{@code cancelada}
 * (Req 31.6). Listado paginado con filtros por Proveedor y estado (Req 31.7);
 * auditoria del alta y del cambio de estado (Req 31.8). Puede generarse desde una
 * Requisicion_Compra aprobada (Req 30.3) via {@code crearDesdeRequisicion}.</p>
 */
package com.dessti.crm.compras.ordencompra;
