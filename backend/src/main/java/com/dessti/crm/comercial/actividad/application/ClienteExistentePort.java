package com.dessti.crm.comercial.actividad.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la existencia de un Cliente <strong>activo</strong>
 * dentro del tenant vigente, requerido al registrar una {@link
 * com.dessti.crm.comercial.actividad.domain.Actividad} de seguimiento.
 *
 * <p>Se introduce un puerto propio del submodulo de Actividades —en lugar de
 * inyectar directamente el repositorio de Clientes— para <strong>desacoplar</strong>
 * la aplicacion de Actividades de la persistencia de Clientes, coherente con el
 * patron de {@code oportunidad}. El adaptador {@code ClienteExistenteAdapter}
 * implementa este puerto delegando en {@code ClienteRepository.findByIdAndActivoTrue}.
 * El aislamiento por tenant lo garantizan el filtro global de Hibernate y la RLS
 * (Req 23).</p>
 */
public interface ClienteExistentePort {

    /**
     * Indica si existe un Cliente activo con el identificador dado en el tenant
     * vigente.
     *
     * @param clienteId identificador del Cliente a verificar.
     * @return {@code true} si el Cliente existe y esta activo en el tenant.
     */
    boolean existeClienteActivo(UUID clienteId);
}
