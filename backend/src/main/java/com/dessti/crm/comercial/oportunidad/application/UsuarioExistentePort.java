package com.dessti.crm.comercial.oportunidad.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la existencia de un Usuario <strong>activo</strong>
 * dentro del tenant vigente, requerido al asignar el responsable de una
 * Oportunidad (V81, forecast/responsables).
 *
 * <p>Se introduce un puerto propio del submodulo de Oportunidades —en lugar de
 * inyectar directamente el repositorio de Usuarios de plataforma— para
 * <strong>desacoplar</strong> la aplicacion de Oportunidades de la gestion de
 * Usuarios, coherente con el patron de {@code ClienteExistentePort}. El adaptador
 * {@code UsuarioExistenteAdapter} implementa este puerto delegando en
 * {@code UsuarioRepository.findByIdAndTenantIdAndActivoTrue}, acotando siempre al
 * tenant del contexto (la entidad Usuario no es tenant-scoped por RLS, su
 * aislamiento se aplica explicitamente por consulta, Req 23).</p>
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
