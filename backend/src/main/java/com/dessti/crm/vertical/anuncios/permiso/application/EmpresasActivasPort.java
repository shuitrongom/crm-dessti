package com.dessti.crm.vertical.anuncios.permiso.application;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de lectura (Decisión D8) que enumera los {@code tenantId} de las
 * Empresas <strong>activas</strong> de la plataforma, para que el barrido
 * multi-tenant de vencimientos de permisos (Req 13.6) pueda iterarlas y operar
 * en el contexto de cada una.
 *
 * <p>Se define en la capa {@code application} del submódulo de permisos (vertical
 * anuncios) para no acoplar el planificador al modelo de plataforma de Empresa:
 * su adaptador delega en {@code ServicioEmpresas}/{@code EmpresaRepository}
 * (nivel plataforma). Cada {@code tenantId} devuelto es la propia PK de la
 * Empresa ({@code empresa.getId()}), que es el {@code tenant_id} usado por la
 * Row-Level Security.</p>
 */
public interface EmpresasActivasPort {

    /**
     * Devuelve los identificadores de tenant de todas las Empresas en estado
     * {@code activa}.
     *
     * @return la lista de {@code tenantId} de Empresas activas; vacía si no hay
     *         ninguna. Nunca {@code null}.
     */
    List<UUID> tenantsActivos();
}
