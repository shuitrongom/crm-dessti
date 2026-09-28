/**
 * Adaptadores de salida de la <strong>IA de la suite BI</strong> (Req 48, suite BI+IA;
 * Req 11): implementaciones del {@link com.dessti.crm.reportesbi.application.ia.GeneradorInsightsPort}.
 *
 * <ul>
 *   <li>{@link com.dessti.crm.reportesbi.adapter.out.ia.GeneradorInsightsHeuristico}:
 *       generador determinista sin red, respaldo de degradacion gracil y bean de
 *       arranque/pruebas.</li>
 *   <li>{@link com.dessti.crm.reportesbi.adapter.out.ia.GeneradorInsightsHttpAdapter}:
 *       cliente HTTP hacia un proveedor de IA externo (contrato estilo chat completions);
 *       ante cualquier fallo o ausencia de configuracion delega en el heuristico.</li>
 *   <li>{@link com.dessti.crm.reportesbi.adapter.out.ia.IaProperties}: configuracion del
 *       proveedor ({@code crm.ia.*}); la clave de API se resuelve desde el entorno y nunca
 *       se escribe en logs.</li>
 * </ul>
 */
package com.dessti.crm.reportesbi.adapter.out.ia;
