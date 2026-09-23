/**
 * Utilidades transversales de paginacion reutilizables por todos los modulos
 * (Req 12).
 *
 * <p>Componentes principales:</p>
 * <ul>
 *   <li>{@link com.dessti.crm.platform.web.pagination.PaginacionConstantes}:
 *   limites centralizados (tamano por defecto 20, maximo 100).</li>
 *   <li>{@link com.dessti.crm.platform.web.pagination.PageRequestFactory}:
 *   construye un {@code Pageable} validando y aplicando la politica de tamano
 *   (rechazar {@code > 100} con 400 por defecto, o acotar de forma opcional).</li>
 *   <li>{@link com.dessti.crm.platform.web.pagination.PaginaResponse}: DTO de
 *   respuesta paginada generico con {@code content}, {@code page}, {@code size},
 *   {@code totalElements} y {@code totalPages}, con mapeo desde
 *   {@code Page<T>}.</li>
 *   <li>{@link com.dessti.crm.platform.web.pagination.ParametrosPaginacionInvalidosException}:
 *   se traduce a HTTP 400 mediante el manejador global de errores, sin duplicar
 *   su logica.</li>
 * </ul>
 *
 * <p><b>Politica de tamano de pagina:</b> el comportamiento estandar
 * <i>rechaza</i> un {@code size} superior a 100 con 400 (coherente con el
 * Req 7.8), evitando respuestas ambiguas. Para endpoints cuyo requisito solo
 * exija acotar, {@code PageRequestFactory.acotando(...)} limita el tamano a 100
 * en lugar de rechazarlo.</p>
 */
package com.dessti.crm.platform.web.pagination;
