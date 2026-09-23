/**
 * Dominio del submodulo Cuentas_Por_Cobrar (CxC), Pagos de Cliente y Complemento
 * de Pago del modulo contabilidad-finanzas (Req 36, 37).
 *
 * <p>Contiene las raices de agregado {@link com.dessti.crm.contabilidad.cxc.domain.CuentaPorCobrar}
 * (con la regla PURA de aplicacion de pago acotada por el saldo, Property 13),
 * {@link com.dessti.crm.contabilidad.cxc.domain.PagoCliente} y
 * {@link com.dessti.crm.contabilidad.cxc.domain.AplicacionPago}, junto con la
 * maquina de estados pura {@link com.dessti.crm.contabilidad.cxc.domain.EstadoCuentaPorCobrar}
 * y su convertidor JPA. Todas las entidades son tenant-scoped (Req 23) y se mapean
 * exactamente sobre las tablas de la migracion V31.</p>
 */
package com.dessti.crm.contabilidad.cxc.domain;
