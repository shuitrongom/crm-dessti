/**
 * Adaptadores de salida de persistencia del submodulo de reportes y estados
 * financieros de contabilidad (Req 39, 47).
 *
 * <p>Contiene repositorios Spring Data JPA de <strong>solo lectura</strong> que
 * agregan datos ya existentes sin modificarlos (Req 39.2, 47.2):
 * {@link com.dessti.crm.contabilidad.reportes.adapter.out.persistence.ReportesContablesRepository}
 * agrega los saldos por Cuenta_Contable de las Polizas_Contables de un periodo
 * (balance general, estado de resultados y balanza de comprobacion), y
 * {@link com.dessti.crm.contabilidad.reportes.adapter.out.persistence.ReportesFiscalesRepository}
 * agrega los importes fiscales de las Facturas timbradas de un periodo (ingresos e
 * IVA trasladado/retenido). Las proyecciones asociadas transportan los agregados a la
 * capa de aplicacion.</p>
 */
package com.dessti.crm.contabilidad.reportes.adapter.out.persistence;
