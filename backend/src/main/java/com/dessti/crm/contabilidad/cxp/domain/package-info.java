/**
 * Modelo de dominio del submodulo Cuentas_Por_Pagar (CxP) y Programacion_Pago
 * (Req 42). Es la imagen espejo del dominio de Cuentas_Por_Cobrar.
 *
 * <p>Contiene el agregado
 * {@link com.dessti.crm.contabilidad.cxp.domain.CuentaPorPagar} (saldo pendiente de
 * pago a un Proveedor, con la regla PURA {@code aplicarPago} acotada por el saldo,
 * <strong>Property 15</strong>, Req 42.3, 42.4), su maquina de estados
 * {@link com.dessti.crm.contabilidad.cxp.domain.EstadoCuentaPorPagar}, y la entidad
 * {@link com.dessti.crm.contabilidad.cxp.domain.ProgramacionPago} (calendario de
 * pago por CxP, Req 42.2).</p>
 */
package com.dessti.crm.contabilidad.cxp.domain;
