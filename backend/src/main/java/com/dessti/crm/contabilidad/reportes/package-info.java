/**
 * Submodulo de <strong>reportes y estados financieros</strong> de contabilidad-finanzas
 * (Req 39, 47).
 *
 * <p>Produce, en modo de <strong>solo lectura</strong>, los reportes financieros y
 * fiscales (estado de cuenta por Cliente, ingresos por periodo, IVA
 * trasladado/retenido, antiguedad de saldos y libro de polizas, Req 39) y los estados
 * financieros (balance general, estado de resultados y balanza de comprobacion
 * derivados de las Polizas_Contables de un periodo, Req 47). Todas las salidas son
 * agregaciones que no modifican los datos de origen (Req 39.2, 47.2), filtrables por
 * fecha/Cliente/periodo y exportables, protegidas por permisos contables (403 sin
 * permiso, Req 39.4/47.5) y auditadas en consulta y exportacion (Req 39.5/47.6).</p>
 *
 * <p>El corazon del submodulo es el dominio PURO
 * {@link com.dessti.crm.contabilidad.reportes.domain.EstadosFinancieros}, cuyo
 * balance general cumple la ecuacion contable {@code activo == pasivo + capital}
 * (<strong>Property 17</strong>, Req 47.3) apoyandose en el balance de cada
 * Poliza_Contable (Property 16). Sigue la arquitectura hexagonal del proyecto con
 * subpaquetes {@code domain}, {@code application} y {@code adapter.in.rest} /
 * {@code adapter.out.persistence}.</p>
 */
package com.dessti.crm.contabilidad.reportes;
