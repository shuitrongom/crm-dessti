/**
 * Adaptador de salida de <strong>solo lectura</strong> del area redes sociales que
 * implementa el {@code IndicadorSocialPort} del modulo reportes-bi (Req 22.1, 48.1, 66.3).
 * Reutiliza el asiento (seam) {@code ServicioAnaliticaSocial.metricasDominio} del propio
 * modulo social para derivar los mensajes por canal, el tiempo de respuesta promedio y los
 * leads sin modificar dato alguno (Req 22.2, 66.1). Al registrarse como bean desplaza al
 * adaptador por defecto en cero ({@code @ConditionalOnMissingBean}). El aislamiento por
 * tenant (Req 23, 66.6) lo garantizan el filtro global de Hibernate y la RLS de PostgreSQL.
 */
package com.dessti.crm.social.analitica.adapter.out.indicadores;
