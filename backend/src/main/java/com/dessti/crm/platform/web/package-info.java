/**
 * Utilidades web transversales.
 *
 * <p>Incluye el manejo global de errores mediante
 * {@link com.dessti.crm.platform.web.ManejadorGlobalErrores}, que traduce las
 * excepciones de dominio e infraestructura a respuestas Problem Details
 * (RFC 7807) sin filtrar detalles internos (Req 8, 56). Las utilidades de
 * paginacion transversal residen en el subpaquete
 * {@link com.dessti.crm.platform.web.pagination} (Req 12).</p>
 */
package com.dessti.crm.platform.web;
