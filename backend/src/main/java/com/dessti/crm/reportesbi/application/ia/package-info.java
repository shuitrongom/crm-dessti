/**
 * Puerto y contratos de la <strong>Inteligencia Artificial de la suite BI</strong>
 * (Req 48, suite BI+IA): generacion de insights ejecutivos en lenguaje natural (es-MX)
 * a partir del consolidado de indicadores.
 *
 * <p>Este subpaquete de la capa de aplicacion define la frontera hexagonal hacia el
 * proveedor de IA ({@link com.dessti.crm.reportesbi.application.ia.GeneradorInsightsPort})
 * y sus <em>records</em> inmutables de solicitud/resultado, sin dependencias de
 * framework ni de persistencia. Los adaptadores concretos (heuristico de respaldo y
 * cliente HTTP real) viven en {@code adapter.out.ia} y se seleccionan por
 * configuracion, con degradacion gracil para que la suite nunca se rompa por la IA.</p>
 */
package com.dessti.crm.reportesbi.application.ia;
