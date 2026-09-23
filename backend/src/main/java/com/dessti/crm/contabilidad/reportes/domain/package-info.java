/**
 * Modelo de dominio <strong>puro</strong> del submodulo de reportes y estados
 * financieros de contabilidad (Req 39, 47).
 *
 * <p>Contiene los valores inmutables
 * {@link com.dessti.crm.contabilidad.reportes.domain.SaldoCuenta} (saldo de una
 * Cuenta_Contable derivado de sus cargos/abonos segun su naturaleza),
 * {@link com.dessti.crm.contabilidad.reportes.domain.BalanceGeneral},
 * {@link com.dessti.crm.contabilidad.reportes.domain.EstadoResultados} y
 * {@link com.dessti.crm.contabilidad.reportes.domain.BalanzaComprobacion}, y el
 * componente PURO
 * {@link com.dessti.crm.contabilidad.reportes.domain.EstadosFinancieros} que los
 * deriva.</p>
 *
 * <p>La ecuacion contable del balance general
 * (<strong>Property 17</strong>, Req 47.3) —{@code activo == pasivo + capital}, con
 * el resultado del ejercicio {@code (ingresos - gastos)} integrado en el capital— se
 * cumple por construccion cuando las Polizas_Contables del periodo estan balanceadas
 * (Property 16, Req 38.3), y se verifica directamente sobre estas funciones puras sin
 * base de datos ni framework.</p>
 */
package com.dessti.crm.contabilidad.reportes.domain;
