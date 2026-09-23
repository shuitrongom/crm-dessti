package com.dessti.crm.platform.security.usuarios.rest;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para actualizar los datos descriptivos de un Usuario
 * (Req 4): por ahora, unicamente el nombre PARA MOSTRAR.
 *
 * <p>El {@code identificador_acceso} (login) y la contrasena NO se editan por
 * esta via (el identificador es inmutable, V1; la contrasena tiene su propio
 * flujo de restablecimiento). El nombre visible es opcional: un valor
 * {@code null}/en blanco lo limpia. El {@code tenant_id} se deriva del contexto
 * autenticado (Req 23.4), nunca de la peticion.</p>
 *
 * @param nombreVisible nombre para mostrar; opcional (max 200).
 */
public record ActualizarUsuarioRequest(
        @Size(max = 200) String nombreVisible) {
}
