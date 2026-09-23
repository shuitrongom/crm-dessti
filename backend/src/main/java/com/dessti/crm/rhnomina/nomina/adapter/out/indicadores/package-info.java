/**
 * Adaptador de salida de <strong>solo lectura</strong> del area RH/nomina que implementa
 * el {@code IndicadorRhNominaPort} del modulo reportes-bi (Req 22.1, 48.1). Deriva del
 * propio modulo de RH/nomina el costo de nomina del periodo mediante agregaciones
 * {@code SUM}/{@code COUNT} sin modificar dato alguno (Req 22.2). Al registrarse como bean
 * desplaza al adaptador por defecto en cero ({@code @ConditionalOnMissingBean}). El
 * aislamiento por tenant (Req 23) lo garantizan el filtro global de Hibernate y la RLS.
 */
package com.dessti.crm.rhnomina.nomina.adapter.out.indicadores;
