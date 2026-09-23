/**
 * Dominio puro de la analitica social (Req 66): las proyecciones inmutables de
 * entrada ({@code FilaMetricaSocial}), el resultado agregado por canal
 * ({@code MetricasSociales}) y la funcion pura de agregacion
 * ({@code CalculoMetricasSociales}).
 *
 * <p>Todo el paquete es independiente de Spring y de JPA: la agregacion es una
 * funcion pura, deterministica y no mutante, lo que la hace directamente
 * verificable (Property 40, Req 66.1, 66.6) sin base de datos ni contexto de
 * aplicacion.</p>
 */
package com.dessti.crm.social.analitica.domain;
