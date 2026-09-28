package com.dessti.crm.platform.security.usuarios;

import java.util.UUID;

/**
 * Puerto de salida que desacopla la gestion de Usuarios del submodulo de Clientes:
 * al dar de alta un Usuario del Portal (rol {@code cliente_portal}, Req 45) hay que
 * verificar que el Cliente asociado exista y este activo en el tenant vigente, sin
 * que {@code platform.security} dependa de la persistencia de {@code comercial}.
 *
 * <p>El adaptador {@code UsuarioClienteExistenteAdapter} implementa este puerto
 * delegando en {@code ClienteRepository.findByIdAndActivoTrue} del submodulo de
 * Clientes, siguiendo el mismo patron que los puertos homonimos de Cotizaciones y
 * Oportunidades.</p>
 */
public interface ClienteExistentePort {

    /**
     * Indica si un Cliente existe y esta activo en el tenant vigente (Req 5, 45).
     *
     * @param clienteId identificador del Cliente; puede ser {@code null}.
     * @return {@code true} si el Cliente existe y esta activo en el tenant.
     */
    boolean existeClienteActivo(UUID clienteId);
}
