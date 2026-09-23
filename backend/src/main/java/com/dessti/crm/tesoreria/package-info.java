/**
 * Modulo <strong>tesoreria</strong>: cuentas bancarias, estados de cuenta,
 * movimientos bancarios y conciliacion bancaria (Req 43).
 *
 * <p>Arquitectura hexagonal por submodulo con paquetes {@code domain},
 * {@code application}, {@code adapter.in.rest} y {@code adapter.out.*}. Las
 * entidades de negocio extienden {@code TenantScopedEntity} (multi-tenant, Req 23)
 * y se persisten sobre las tablas de la migracion V35 con Row-Level Security.</p>
 *
 * <h2>Alcance (Req 43)</h2>
 * <ul>
 *   <li>Alta de {@code Cuenta_Bancaria} (Req 43.1).</li>
 *   <li>Importacion de {@code Estado_Cuenta_Bancario} con sus
 *       {@code Movimiento_Bancario} a traves del puerto de importacion
 *       ({@code ImportacionBancariaPort}, archivo/API) (Req 43.2).</li>
 *   <li>Emparejamiento automatico de cada {@code Movimiento_Bancario} con una
 *       {@code Poliza_Contable} (o {@code Pago}) cuando coinciden el monto, la
 *       fecha dentro de tolerancia y la referencia (Req 43.3).</li>
 *   <li>Los movimientos sin coincidencia se marcan como excepciones para revision
 *       manual (Req 43.4).</li>
 *   <li>La {@code Conciliacion_Bancaria} solo se marca COMPLETA cuando la
 *       diferencia entre el saldo bancario y el saldo contable es cero, una vez
 *       explicadas las partidas (Req 43.5; <strong>Property 18</strong>).</li>
 *   <li>Listado paginado (20/100) con filtros por Cuenta_Bancaria, periodo y estado
 *       de conciliacion (Req 43.6) y auditoria al importar o conciliar (Req 43.7).</li>
 * </ul>
 */
package com.dessti.crm.tesoreria;
