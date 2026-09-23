/**
 * Submodulo <strong>Proveedores</strong> del modulo compras-abastecimiento
 * (Req 29, 23; tarea 26.1). Establece la disposicion hexagonal por agregado que
 * los demas submodulos de compras replican:
 *
 * <ul>
 *   <li>{@code domain}                   - entidad {@code Proveedor} y sus reglas
 *       de validacion ({@code ProveedorValidaciones}) y borrado logico.</li>
 *   <li>{@code application}              - servicio {@code ServicioProveedores},
 *       DTO y comandos.</li>
 *   <li>{@code adapter.in.rest}          - {@code ProveedorController} y sus DTOs
 *       de peticion.</li>
 *   <li>{@code adapter.out.persistence}  - {@code ProveedorRepository}.</li>
 * </ul>
 *
 * <p>El Proveedor es analogo al {@code Cliente} del modulo comercial-crm: alta con
 * datos obligatorios, RFC unico POR TENANT entre activos (indice unico parcial de
 * V28), borrado logico ({@code activo}) y listado paginado filtrable por nombre o
 * RFC (Req 29). El aislamiento por tenant se refuerza con RLS (V28).</p>
 */
package com.dessti.crm.compras.proveedor;
