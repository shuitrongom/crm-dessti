/**
 * Modelo de dominio del submodulo de contabilidad: catalogo de Cuentas_Contables y
 * Polizas_Contables balanceadas (Req 38).
 *
 * <p>Contiene la entidad {@link com.dessti.crm.contabilidad.polizas.domain.CuentaContable}
 * (catalogo por tenant, Req 38.1) y el agregado
 * {@link com.dessti.crm.contabilidad.polizas.domain.PolizaContable} con sus renglones
 * {@link com.dessti.crm.contabilidad.polizas.domain.MovimientoPoliza}. La fabrica
 * {@code PolizaContable.crear} es la regla PURA de balance (suma de cargos == suma de
 * abonos) que rechaza las polizas desbalanceadas informando la diferencia
 * (<strong>Property 16</strong>, Req 38.3, 38.4); {@code reversar} crea la poliza de
 * reverso que preserva la inmutabilidad contable (Req 38.5).</p>
 */
package com.dessti.crm.contabilidad.polizas.domain;
