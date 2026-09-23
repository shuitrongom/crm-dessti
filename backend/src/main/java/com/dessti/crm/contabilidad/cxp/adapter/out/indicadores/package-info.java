/**
 * Adaptador de salida de <strong>solo lectura</strong> del area cuentas por pagar que
 * implementa el {@code IndicadorCxpPort} del modulo reportes-bi (Req 22.1, 48.1). Deriva
 * del propio submodulo de CxP el saldo y el numero de Cuentas_Por_Pagar vencidas mediante
 * agregaciones {@code SUM}/{@code COUNT} sin modificar dato alguno (Req 22.2). Al
 * registrarse como bean desplaza al adaptador por defecto en cero
 * ({@code @ConditionalOnMissingBean}). El aislamiento por tenant (Req 23) lo garantizan el
 * filtro global de Hibernate y la RLS de PostgreSQL.
 */
package com.dessti.crm.contabilidad.cxp.adapter.out.indicadores;
