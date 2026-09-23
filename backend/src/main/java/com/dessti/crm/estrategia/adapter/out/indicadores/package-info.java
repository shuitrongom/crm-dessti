/**
 * Adaptador de salida de <strong>solo lectura</strong> del area estrategia que implementa
 * el {@code IndicadorEstrategiaPort} del modulo reportes-bi (Req 22.1, 48.1). Deriva del
 * propio modulo de estrategia el avance de los Objetivos_Estrategicos mediante agregaciones
 * {@code AVG}/{@code COUNT} sin modificar dato alguno (Req 22.2). Al registrarse como bean
 * desplaza al adaptador por defecto en cero ({@code @ConditionalOnMissingBean}). El
 * aislamiento por tenant (Req 23) lo garantizan el filtro global de Hibernate y la RLS.
 */
package com.dessti.crm.estrategia.adapter.out.indicadores;
