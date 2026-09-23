/**
 * Modulo calidad: cumplimiento y calidad ISO 9001:2026 (Req 70). Habilita y evidencia el
 * Sistema de Gestion de Calidad de la Empresa con los agregados Queja_Cliente,
 * No_Conformidad, Accion_Correctiva (con guarda de cierre por eficacia verificada,
 * Property 43), Riesgo y Oportunidad_Calidad (registros separados de las clausulas 6.1.2 y
 * 6.1.3), Cambio_SGC (gestion del cambio, clausula 6.3) y Contexto_Organizacion (contexto y
 * cambio climatico, clausulas 4.1/4.2). Ofrece ademas indicadores de cultura de calidad de
 * solo lectura (Req 70.7) y una vista de trazabilidad de clausulas (Req 70.10). Reutiliza
 * los servicios transversales de auditoria (evidencia documentada, Req 10), tablero y redes
 * sociales, respetando el aislamiento por tenant (Req 23), la autorizacion RBAC (Req 3), la
 * paginacion estandar (Req 12) y la concurrencia optimista (Req 49). Ver design.md.
 */
package com.dessti.crm.calidad;
