package com.dessti.crm.portalcliente.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida que el <strong>Portal del Cliente</strong> define para consultar
 * los datos del propio Cliente (Req 45.1). Lo implementa el modulo comercial
 * (Clientes), invirtiendo la dependencia: el Portal (Nucleo) no conoce la
 * persistencia del Cliente.
 *
 * <p>El {@code clienteId} lo resuelve siempre {@link ServicioPortalCliente} desde el
 * usuario del Portal (nunca de la peticion), de modo que un Cliente solo ve sus
 * propios datos.</p>
 */
public interface PerfilClientePortalPort {

    /**
     * Consulta el perfil del Cliente indicado, acotado al tenant vigente (Req 45.1,
     * 23). Devuelve vacio si el Cliente no existe o no esta activo en el tenant, que
     * la aplicacion traduce a 404.
     *
     * @param clienteId Cliente del Portal; obligatorio.
     * @return el resumen del perfil del Cliente, o vacio si no es accesible.
     */
    Optional<PerfilClienteResumen> buscarPerfil(UUID clienteId);
}
