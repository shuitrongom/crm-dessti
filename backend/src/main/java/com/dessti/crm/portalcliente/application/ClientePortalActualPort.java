package com.dessti.crm.portalcliente.application;

import java.util.UUID;

/**
 * Puerto de resolucion del <strong>Cliente</strong> al que esta vinculado el
 * usuario del Portal autenticado (rol externo {@code cliente_portal}, Req 45).
 *
 * <h2>Motivacion (Req 45.1, 45.3)</h2>
 * <p>El rol {@code cliente_portal} es un rol <em>externo y restringido</em>
 * asociado a un Cliente concreto. Toda consulta del Portal debe acotarse a
 * <strong>ese</strong> Cliente (no solo al tenant), de modo que un Cliente vea
 * unicamente su propia relacion comercial y nunca datos de otros Clientes ni
 * operaciones internas de la Empresa. Este puerto resuelve el identificador de
 * ese Cliente a partir del contexto de seguridad, y la capa de aplicacion filtra
 * <em>cada</em> consulta por el (nunca por un {@code clienteId} recibido en la
 * peticion, que jamas se acepta).</p>
 *
 * <h2>Punto de resolucion (decision de la tarea 45.1)</h2>
 * <p>El proyecto no publica hoy un vinculo explicito Usuario -&gt; Cliente para el
 * Portal ({@code UsuarioAutenticado} solo porta {@code id} y {@code tenant_id}, y
 * el JWT no incluye un claim de Cliente). Para no acoplar el Portal a la
 * emision de tokens ni al modulo de seguridad, la resolucion se declara como este
 * puerto. La implementacion por defecto
 * ({@code ClientePortalActualDesdeAuthenticationAdapter}, registrada con
 * {@code @ConditionalOnMissingBean}) lee el identificador del Cliente de una
 * <em>authority</em> del principal con el prefijo {@code cliente_id:} (por
 * ejemplo {@code cliente_id:6f...}), que la capa de autenticacion puede emitir en
 * el JWT del usuario del Portal. Cuando se introduzca un mecanismo definitivo
 * (claim dedicado o tabla de vinculo), bastara aportar otro bean que implemente
 * este puerto, sin tocar el resto del Portal.</p>
 *
 * <p>El {@code tenant_id} del usuario ya lo fija el {@code TenantContext} (Req
 * 23.4); este puerto <em>solo</em> resuelve la identidad del Cliente dentro de
 * ese tenant.</p>
 */
public interface ClientePortalActualPort {

    /**
     * Resuelve el identificador del Cliente vinculado al usuario del Portal
     * autenticado (Req 45.1).
     *
     * @return el identificador del Cliente del usuario del Portal; nunca
     *         {@code null}.
     * @throws org.springframework.security.access.AccessDeniedException si no hay
     *         un usuario del Portal autenticado o su identidad de Cliente no se
     *         puede resolver (se traduce a 403; el Portal nunca revela datos sin
     *         un Cliente resuelto, Req 45.3).
     */
    UUID clienteIdActual();
}
