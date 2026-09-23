/**
 * Submodulo de <strong>analitica social</strong> (Req 66): metricas de
 * <em>solo lectura</em> sobre los datos de la mensajeria omnicanal (Conversacion
 * y Mensaje_Social de los bloques 40/41) agregadas por Canal_Social y periodo.
 *
 * <p>Produce, sin modificar los datos de origen (Req 66.1), el alcance, las
 * interacciones, los mensajes recibidos/enviados, el tiempo de respuesta promedio
 * y las conversiones/leads por canal; permite filtrar por rango de fechas y por
 * Canal_Social y exportar el resultado (Req 66.4); exige permiso analitico
 * ({@code analitica_social}, Req 66.5); acota todo al {@code tenant_id} vigente
 * (Req 66.6, 23); y audita cada consulta y exportacion (Req 66.7).</p>
 *
 * <h2>Nucleo puro (Property 40)</h2>
 * <p>El calculo vive en la funcion pura {@code CalculoMetricasSociales}, sin Spring
 * ni JPA, sobre proyecciones inmutables de filas ({@code FilaMetricaSocial}). Esto
 * la hace deterministica, no mutante y directamente verificable por tenant.</p>
 *
 * <h2>Consolidacion en Tablero/BI (Req 66.3)</h2>
 * <p>El servicio expone un metodo/DTO reutilizable por un futuro adaptador de
 * {@code IndicadorSocialPort} (bloque 56, modulo reportes-bi). Este submodulo NO
 * importa ni referencia el paquete {@code com.dessti.crm.reportesbi}: solo provee
 * el asiento (seam) para no acoplarse ni colisionar con desarrollos en paralelo.</p>
 */
package com.dessti.crm.social.analitica;
