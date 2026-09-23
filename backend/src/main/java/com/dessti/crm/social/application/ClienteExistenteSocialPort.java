package com.dessti.crm.social.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la existencia de un Cliente <strong>activo</strong>
 * dentro del tenant vigente, requerido al vincular una Conversacion social a un
 * Cliente del CRM (lead social, Req 64.4, 5.1).
 *
 * <p>Se introduce un puerto propio del modulo social —en lugar de inyectar
 * directamente el repositorio de Clientes del modulo comercial— para
 * <strong>desacoplar</strong> la Bandeja_Unificada del submodulo de Clientes: la
 * aplicacion social solo necesita saber si un Cliente existe en el tenant, no
 * conocer su modelo de persistencia. El adaptador
 * {@code ClienteExistenteSocialAdapter} implementa este puerto delegando en
 * {@code ClienteRepository.findByIdAndActivoTrue}, del mismo modo que
 * {@code ClienteExistentePort} en el modulo comercial.</p>
 */
public interface ClienteExistenteSocialPort {

    /**
     * Indica si existe un Cliente activo con el identificador dado en el tenant
     * vigente (Req 64.4). El aislamiento por tenant lo garantiza el filtro global
     * de Hibernate y la RLS (Req 23), de modo que un Cliente de otra Empresa no se
     * considera existente.
     *
     * @param clienteId identificador del Cliente a verificar.
     * @return {@code true} si el Cliente existe y esta activo en el tenant.
     */
    boolean existeClienteActivo(UUID clienteId);
}
