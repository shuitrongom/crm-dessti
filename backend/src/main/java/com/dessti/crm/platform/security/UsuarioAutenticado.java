package com.dessti.crm.platform.security;

import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantAware;

/**
 * Principal autenticado derivado del {@code Token_Acceso} JWT (Req 1, Req 23).
 *
 * <p>Se coloca como {@code principal} del {@code Authentication} de Spring
 * Security tras validar el token. Implementa {@link TenantAware} para que el
 * {@code TenantResolutionFilter} obtenga el {@code tenant_id} del principal y
 * lo fije en el {@code TenantContext} (integracion multi-tenant de la tarea
 * 4.1 con la autenticacion de la tarea 9.1).</p>
 *
 * <p>Un {@code tenantId} nulo indica un principal de plataforma
 * (super_admin): en ese caso no se fija ningun tenant de negocio (Req 24.3).</p>
 *
 * @param id       identificador del Usuario (claim {@code sub}).
 * @param tenantId empresa del Usuario, o {@code null} para super_admin.
 */
public record UsuarioAutenticado(String id, UUID tenantId) implements TenantAware {

    @Override
    public UUID getTenantId() {
        return tenantId;
    }

    @Override
    public String toString() {
        // No expone datos sensibles; util para logs de diagnostico.
        return "UsuarioAutenticado{id=" + id + ", tenant=" + (tenantId == null ? "<plataforma>" : tenantId) + '}';
    }
}
