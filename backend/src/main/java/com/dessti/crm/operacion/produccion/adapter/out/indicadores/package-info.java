/**
 * Adaptador de salida de <strong>solo lectura</strong> del area produccion que implementa
 * el {@code IndicadorProduccionPort} del modulo reportes-bi (Req 22.1, 48.1). Deriva del
 * propio submodulo de Ordenes de Fabricacion del vertical de anuncios las Ordenes por
 * estado mediante agregaciones {@code COUNT} sin modificar dato alguno (Req 22.2). Al
 * registrarse como bean desplaza al adaptador por defecto en cero
 * ({@code @ConditionalOnMissingBean}). El aislamiento por tenant (Req 23) lo garantizan el
 * filtro global de Hibernate y la RLS de PostgreSQL.
 */
package com.dessti.crm.operacion.produccion.adapter.out.indicadores;
