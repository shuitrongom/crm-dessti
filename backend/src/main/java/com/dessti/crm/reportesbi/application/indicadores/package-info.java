/**
 * Puertos de indicadores de solo lectura del modulo reportes-bi (Req 22.1, 48.1).
 *
 * <p>Este paquete declara un {@link com.dessti.crm.reportesbi.application.indicadores.IndicadorAreaPort
 * puerto} por cada area de negocio ({@link com.dessti.crm.reportesbi.application.indicadores.AreaIndicador})
 * que el Tablero y el analisis consolidado agregan. Cada puerto recibe un
 * {@link com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores filtro}
 * (fecha/Cliente/area/dimension) y devuelve un
 * {@link com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea conjunto de
 * indicadores} inmutable formado por
 * {@link com.dessti.crm.reportesbi.application.indicadores.ValorIndicador valores}.</p>
 *
 * <p><strong>Diseno desacoplado:</strong> reportes-bi solo depende de estas
 * abstracciones, no de los servicios/entidades de cada modulo. Cada modulo de area
 * aporta su adaptador concreto de solo lectura mas adelante; mientras tanto,
 * {@code ReportesBiConfig} registra un adaptador por defecto por puerto
 * ({@code @ConditionalOnMissingBean}) que devuelve indicadores vacios/cero, de modo que
 * el modulo compila y el Tablero renderiza aunque un area aun no este conectada.</p>
 */
package com.dessti.crm.reportesbi.application.indicadores;
