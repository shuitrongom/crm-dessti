/**
 * Capa de aplicacion del submodulo de contabilidad: catalogo de Cuentas_Contables y
 * Polizas_Contables balanceadas (Req 38).
 *
 * <p>Expone el servicio
 * {@link com.dessti.crm.contabilidad.polizas.application.ServicioContabilidad} que
 * mantiene el catalogo de cuentas (codigo unico por tenant, 409 si se duplica),
 * registra polizas balanceadas (validacion de balance del dominio, Property 16),
 * crea polizas de reverso (Req 38.5) y lista polizas con filtros por rango de fechas
 * y por Cuenta_Contable (Req 38.6). Implementa
 * {@link com.dessti.crm.contabilidad.polizas.application.PolizaContablePort}, la
 * frontera que los productores de eventos contables pueden invocar para generar
 * polizas (Req 38.2). Los comandos y DTOs son distintos de las entidades de
 * persistencia (Req 12.2).</p>
 */
package com.dessti.crm.contabilidad.polizas.application;
