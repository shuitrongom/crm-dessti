package com.dessti.crm.comercial.oportunidad.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la existencia de un Cliente <strong>activo</strong>
 * dentro del tenant vigente, requerido al registrar una Oportunidad (Req 14.1).
 *
 * <p>Se introduce un puerto propio —en lugar de inyectar directamente el
 * repositorio de Clientes— para <strong>desacoplar</strong> el submodulo de
 * Oportunidades del de Clientes: la aplicacion de Oportunidades solo necesita
 * saber si un Cliente existe, no conocer su modelo de persistencia. El
 * adaptador {@code ClienteExistenteAdapter} implementa este puerto delegando en
 * {@code ClienteRepository.findByIdAndActivoTrue} (ambos en el mismo modulo
 * comercial-crm), preservando la portabilidad del nucleo (design.md, puertos y
 * adaptadores).</p>
 */
public interface ClienteExistentePort {

    /**
     * Indica si existe un Cliente activo con el identificador dado en el tenant
     * vigente (Req 14.1). El aislamiento por tenant lo garantiza el filtro global
     * de Hibernate y la RLS (Req 23).
     *
     * @param clienteId identificador del Cliente a verificar.
     * @return {@code true} si el Cliente existe y esta activo en el tenant.
     */
    boolean existeClienteActivo(UUID clienteId);
}
