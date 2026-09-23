/**
 * Adaptador de salida de <strong>solo lectura</strong> del area mantenimiento que
 * implementa el {@code IndicadorMantenimientoPort} del modulo reportes-bi (Req 22.1,
 * 48.1). Deriva del propio modulo de mantenimiento el cumplimiento del SLA de los
 * Tickets_Servicio resueltos mediante agregaciones {@code COUNT} sin modificar dato alguno
 * (Req 22.2). Al registrarse como bean desplaza al adaptador por defecto en cero
 * ({@code @ConditionalOnMissingBean}). El aislamiento por tenant (Req 23) lo garantizan el
 * filtro global de Hibernate y la RLS de PostgreSQL.
 */
package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.out.indicadores;
