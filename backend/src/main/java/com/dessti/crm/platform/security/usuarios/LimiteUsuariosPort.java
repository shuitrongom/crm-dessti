package com.dessti.crm.platform.security.usuarios;

import java.util.UUID;

/**
 * Puerto de consulta para el <em>limite de Usuarios</em> del Plan de una Empresa
 * (Req 25.3).
 *
 * <p>Permite a {@link ServicioUsuarios} comprobar, antes de crear una cuenta, si
 * la Empresa aun tiene cupo segun el {@code max_usuarios} del Plan vigente, sin
 * depender directamente de la persistencia del modulo de plataforma
 * (Empresas/Planes/Suscripciones). De este modo se conserva la frontera
 * hexagonal: {@code ServicioUsuarios} (seguridad) depende de este contrato, y el
 * adaptador que lo implementa vive en el modulo de plataforma (empresas).</p>
 *
 * <p>La implementacion definitiva ({@code LimiteUsuariosPlanAdapter}) resuelve la
 * Suscripcion activa de la Empresa, carga su Plan y compara {@code max_usuarios}
 * con el numero de cuentas activas actuales de la Empresa. Si no hay Suscripcion
 * activa o Plan, deniega la creacion (Req 25.3).</p>
 */
public interface LimiteUsuariosPort {

    /**
     * Indica si la Empresa puede crear una nueva cuenta de Usuario sin exceder el
     * limite de su Plan (Req 25.3).
     *
     * @param tenantId Empresa (tenant) para la que se evalua el limite; se toma
     *                 del contexto autenticado, nunca de la peticion (Req 23.4).
     * @return {@code true} si la creacion de una cuenta adicional respeta el
     *         limite del Plan; {@code false} si crearla alcanzaria o excederia el
     *         limite, o si no hay Suscripcion/Plan vigente.
     */
    boolean puedeCrearUsuario(UUID tenantId);
}
