/**
 * Capa de aplicacion del submodulo de reportes y estados financieros de contabilidad
 * (Req 39, 47).
 *
 * <p>Expone dos servicios de <strong>solo lectura</strong>:
 * {@link com.dessti.crm.contabilidad.reportes.application.ServicioReportesFinancieros}
 * (reportes financieros/fiscales: estado de cuenta por Cliente, ingresos por periodo,
 * IVA trasladado/retenido, aging y libro de polizas, con filtros por fecha/Cliente y
 * exportacion, Req 39) y
 * {@link com.dessti.crm.contabilidad.reportes.application.ServicioEstadosFinancieros}
 * (balance general, estado de resultados y balanza de comprobacion derivados de las
 * Polizas_Contables de un periodo, Req 47). Ambos agregan datos ya existentes sin
 * modificarlos (Req 39.2, 47.2) y auditan cada consulta y exportacion (Req 39.5,
 * 47.6).</p>
 *
 * <p>El balance general derivado cumple la ecuacion contable
 * {@code activo == pasivo + capital} (<strong>Property 17</strong>, Req 47.3) por
 * apoyarse en el componente PURO
 * {@link com.dessti.crm.contabilidad.reportes.domain.EstadosFinancieros}. Los DTOs
 * son distintos de las entidades de persistencia (Req 12.2).</p>
 */
package com.dessti.crm.contabilidad.reportes.application;
