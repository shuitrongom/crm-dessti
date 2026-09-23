/**
 * Adaptador de salida de <strong>solo lectura</strong> del area finanzas/facturacion que
 * implementa el {@code IndicadorFinanzasPort} del modulo reportes-bi (Req 22.1, 48.1).
 * Deriva la facturacion timbrada del periodo y el IVA del modulo de facturacion, y las
 * Cuentas_Por_Cobrar vencidas del modulo de contabilidad, mediante agregaciones
 * {@code SUM}/{@code COUNT} sin modificar dato alguno (Req 22.2). Al registrarse como bean
 * desplaza al adaptador por defecto en cero ({@code @ConditionalOnMissingBean}). El
 * aislamiento por tenant (Req 23) lo garantizan el filtro global de Hibernate y la RLS.
 */
package com.dessti.crm.contabilidad.adapter.out.indicadores;
