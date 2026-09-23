/**
 * Nucleo transversal de la plataforma: arranque y configuracion compartida
 * entre todos los modulos de negocio. Ver design.md, "Estructura de Paquetes".
 *
 * <p>Subpaquetes:</p>
 * <ul>
 *   <li>{@code config}   - configuracion de Spring, seguridad, OpenAPI, cache.</li>
 *   <li>{@code tenant}   - TenantContext, filtro RLS, filtro Hibernate (multi-tenant).</li>
 *   <li>{@code security} - JWT, RBAC, rate limiting, bloqueo.</li>
 *   <li>{@code audit}    - servicio de auditoria encadenado.</li>
 *   <li>{@code web}      - manejo global de errores (RFC 7807) y paginacion.</li>
 * </ul>
 */
package com.dessti.crm.platform;
