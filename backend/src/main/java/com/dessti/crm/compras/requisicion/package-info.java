/**
 * Submodulo <strong>Requisiciones de Compra</strong> del modulo
 * compras-abastecimiento (Req 30, 23; tarea 26.2). Gestiona
 * {@code Requisicion_Compra}: solicitud interna de Materiales que, una vez
 * aprobada, habilita generar una {@code Orden_Compra}.
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code RequisicionCompra} (raiz de agregado),
 *       {@code PartidaRequisicion}, {@code EstadoRequisicionCompra} (enum + maquina
 *       de estados pura) con su convertidor JPA, y
 *       {@code RequisicionCompraValidaciones}.</li>
 *   <li>{@code application}: {@code ServicioRequisiciones} (casos de uso), DTOs,
 *       comandos y el puerto {@code MaterialExistentePort}. Consume el
 *       {@code ServicioOrdenesCompra} para generar la Orden desde una requisicion
 *       aprobada (Req 30.3).</li>
 *   <li>{@code adapter.in.rest}: {@code RequisicionCompraController} y sus DTOs de
 *       peticion.</li>
 *   <li>{@code adapter.out.persistence}: {@code RequisicionCompraRepository} y el
 *       adaptador del puerto de Material.</li>
 * </ul>
 *
 * <h2>Reglas (Req 30)</h2>
 * <p>Alta con >=1 partida (Material + cantidad 1..999999) y estado inicial
 * {@code borrador} (Req 30.1). Maquina de estados con transiciones
 * {@code borrador->enviada}, {@code enviada->aprobada|rechazada} y finales
 * {@code aprobada}/{@code rechazada}/{@code cancelada} (Req 30.2). Generacion de
 * Orden_Compra solo desde una requisicion {@code aprobada} (422 en caso contrario,
 * Req 30.3, 30.4). Listado paginado con filtro por estado (Req 30.5); auditoria de
 * los cambios de estado (Req 30.6).</p>
 */
package com.dessti.crm.compras.requisicion;
