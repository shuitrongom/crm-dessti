package com.dessti.crm.comercial.cliente.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la existencia de un Usuario <strong>activo</strong>
 * dentro del tenant vigente, requerido al asignar el propietario/vendedor de un
 * Cliente (V81).
 *
 * <p>Se introduce un puerto propio del submodulo de Clientes para desacoplar la
 * aplicacion de la gestion de Usuarios de plataforma. El adaptador
 * {@code UsuarioExistenteAdapter} lo implementa delegando en
 * {@code UsuarioRepository.findByIdAndTenantIdAndActivoTrue}, acotando al tenant
 * del contexto (la entidad Usuario no es tenant-scoped por RLS, Req 23).</p>
 */
public interface UsuarioExistentePort {

    /**
     * Indica si existe un Usuario activo con el identificador dado en el tenant
     * vigente.
     *
     * @param usuarioId identificador del Usuario a verificar.
     * @return {@code true} si el Usuario existe y esta activo en el tenant.
     */
    boolean existeUsuarioActivo(UUID usuarioId);
}
