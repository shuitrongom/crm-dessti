/**
 * Adaptadores de salida por defecto de los puertos de indicadores del modulo
 * reportes-bi (Req 22.1, 22.2, 48.1, 48.2).
 *
 * <p>Cada clase implementa un {@code IndicadorAreaPort} de una area devolviendo
 * {@code IndicadoresArea.vacio(area)} (cero metricas), y {@code ReportesBiConfig} la
 * registra como bean por defecto solo si no existe otra implementacion
 * ({@code @ConditionalOnMissingBean}). Asi el agregador compila y funciona de forma
 * aislada; los modulos de cada area pueden aportar su adaptador concreto de solo
 * lectura mas adelante, que reemplaza automaticamente al placeholder. Ninguno de estos
 * adaptadores accede a datos de origen ni acopla con otros modulos (en particular, NO
 * con {@code com.dessti.crm.social}).</p>
 */
package com.dessti.crm.reportesbi.adapter.out.indicadores;
