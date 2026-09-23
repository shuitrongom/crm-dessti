/**
 * Capa de aplicacion de la analitica social (Req 66): el servicio de solo lectura
 * {@code ServicioAnaliticaSocial} y sus DTO de salida
 * ({@code MetricasSocialesDto}, {@code ResumenAnaliticaSocialDto}).
 *
 * <p>El servicio compone las metricas por Canal_Social invocando las consultas de
 * agregacion de solo lectura de los repositorios social (acotadas al
 * {@code tenant_id} vigente, Req 66.6) y la funcion pura
 * {@code CalculoMetricasSociales}; permite filtrar por rango de fechas y por
 * Canal_Social y exportar (Req 66.4); y audita cada consulta y exportacion (Req
 * 66.7). No expone ninguna operacion de escritura (Req 66.1).</p>
 */
package com.dessti.crm.social.analitica.application;
