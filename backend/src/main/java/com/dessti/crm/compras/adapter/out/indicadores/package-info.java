/**
 * Adaptador de salida de <strong>solo lectura</strong> del area compras que implementa el
 * {@code IndicadorComprasPort} del modulo reportes-bi (Req 22.1, 48.1). Deriva del propio
 * modulo de compras las Ordenes de Compra por estado y las Facturas de Proveedor con
 * discrepancia mediante agregaciones {@code COUNT} sin modificar dato alguno (Req 22.2). Al
 * registrarse como bean desplaza al adaptador por defecto en cero
 * ({@code @ConditionalOnMissingBean}). El aislamiento por tenant (Req 23) lo garantizan el
 * filtro global de Hibernate y la RLS de PostgreSQL.
 */
package com.dessti.crm.compras.adapter.out.indicadores;
