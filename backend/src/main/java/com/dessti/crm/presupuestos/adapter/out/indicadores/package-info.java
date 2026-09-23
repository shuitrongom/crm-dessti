/**
 * Adaptador de salida de <strong>solo lectura</strong> del area presupuesto que implementa
 * el {@code IndicadorPresupuestoPort} del modulo reportes-bi (Req 22.1, 48.1). Deriva del
 * propio modulo de presupuestos la variacion presupuestal (estimado frente al real via el
 * {@code RealEjercidoPort}) sin modificar dato alguno (Req 22.2, 62.2). Al registrarse como
 * bean desplaza al adaptador por defecto en cero ({@code @ConditionalOnMissingBean}). El
 * aislamiento por tenant (Req 23) lo garantizan el filtro global de Hibernate y la RLS.
 */
package com.dessti.crm.presupuestos.adapter.out.indicadores;
