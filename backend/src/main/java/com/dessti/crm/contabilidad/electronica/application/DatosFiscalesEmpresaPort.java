package com.dessti.crm.contabilidad.electronica.application;

import java.util.UUID;

/**
 * Puerto de aplicacion que resuelve los datos fiscales de una Empresa (tenant)
 * necesarios para el encabezado de los XML de Contabilidad Electronica del SAT: el
 * RFC del contribuyente (Req 2.2, 3.2, 4.2).
 *
 * <p>Desacopla el submodulo {@code contabilidad.electronica} de
 * {@code platform.empresas}: la implementacion resuelve el RFC de la Empresa a
 * partir de su identificador (que es el {@code tenant_id}).</p>
 */
public interface DatosFiscalesEmpresaPort {

    /**
     * Recupera el RFC de la Empresa (tenant) indicada.
     *
     * @param tenantId identificador de la Empresa (su {@code tenant_id}).
     * @return el RFC normalizado (mayusculas) de la Empresa.
     * @throws com.dessti.crm.platform.error.RecursoNoEncontradoException si la
     *         Empresa no existe.
     */
    String rfcDe(UUID tenantId);
}
