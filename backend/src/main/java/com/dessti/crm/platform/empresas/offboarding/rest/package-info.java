/**
 * Adaptadores de entrada REST del offboarding y la portabilidad del tenant
 * (Req 69, tarea 14.4). Exponen la exportacion estructurada de datos por
 * {@code tenant_id} (Req 69.1), la cancelacion con Periodo_Gracia (Req 69.2) y
 * la eliminacion definitiva tras la gracia (Req 69.3), guardados por
 * {@code @PreAuthorize} conforme al RBAC (Req 3).
 */
package com.dessti.crm.platform.empresas.offboarding.rest;
