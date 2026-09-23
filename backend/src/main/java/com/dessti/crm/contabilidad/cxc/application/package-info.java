/**
 * Capa de aplicacion del submodulo Cuentas_Por_Cobrar (CxC), Pagos de Cliente y
 * Complemento de Pago (Req 36, 37).
 *
 * <p>Expone el puerto de entrada
 * {@link com.dessti.crm.contabilidad.cxc.application.CuentaPorCobrarPort} que
 * facturacion invoca para cerrar la integracion con CxC (registro al timbrar,
 * disminucion por nota de credito), y el servicio
 * {@link com.dessti.crm.contabilidad.cxc.application.ServicioCuentasPorCobrar} que
 * gobierna el registro y la aplicacion de pagos acotada por el saldo (Property 13),
 * el Complemento_Pago para parcialidades, la antiguedad de saldos y los listados
 * paginados. Los comandos y DTOs son distintos de las entidades de persistencia
 * (Req 12.2).</p>
 */
package com.dessti.crm.contabilidad.cxc.application;
