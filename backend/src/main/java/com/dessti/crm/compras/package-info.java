/**
 * Modulo <strong>compras-abastecimiento</strong> (Req 29, 30, 31). Agrupa los
 * submodulos de aprovisionamiento del CRM, organizados en agregados hexagonales
 * independientes bajo la raiz {@code com.dessti.crm.compras}, de forma coherente
 * con la organizacion del modulo comercial-crm y operacion-produccion.
 *
 * <h2>Submodulos (un agregado por sub-paquete)</h2>
 * <ul>
 *   <li>{@code proveedor} - alta/edicion/baja logica de {@code Proveedor} con RFC
 *       unico por tenant entre activos y listado filtrable (Req 29).</li>
 *   <li>{@code requisicion} - {@code Requisicion_Compra} con materiales, maquina de
 *       estados y generacion de {@code Orden_Compra} desde una requisicion
 *       aprobada (Req 30).</li>
 *   <li>{@code ordencompra} - {@code Orden_Compra} con partidas, totales half-up y
 *       maquina de estados (Req 31).</li>
 * </ul>
 *
 * <p><strong>Decision de organizacion (bloque 26):</strong> se eligio la
 * disposicion por agregado ({@code compras/proveedor}, {@code compras/requisicion},
 * {@code compras/ordencompra}), cada uno con sus paquetes {@code domain},
 * {@code application}, {@code adapter.in.rest} y {@code adapter.out.persistence},
 * espejando como {@code operacion} agrupa sus submodulos. Asi cada agregado
 * mantiene su propio ciclo de vida y limites transaccionales.</p>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> todas las entidades extienden
 * {@code TenantScopedEntity}; el aislamiento se refuerza con RLS (V28). Las
 * unicidades de negocio son POR TENANT (Req 23.6).</p>
 *
 * <p><strong>Autorizacion (Req 3, 27.5):</strong> los permisos
 * {@code proveedor:{crear,leer,listar,actualizar}},
 * {@code requisicion_compra:{crear,leer,listar,cambiar_estado}} y
 * {@code orden_compra:{crear,leer,listar,cambiar_estado}} ya se sembraron en V5 y
 * se asignaron al rol {@code almacen}, por lo que V28 no siembra permisos nuevos.</p>
 */
package com.dessti.crm.compras;
