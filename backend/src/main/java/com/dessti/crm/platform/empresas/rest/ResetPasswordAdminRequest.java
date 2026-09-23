package com.dessti.crm.platform.empresas.rest;

import java.util.UUID;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo <strong>opcional</strong> del restablecimiento de la contrasena del
 * {@code admin_empresa} de una Empresa por el {@code super_admin} (CHANGE 3):
 * {@code POST /empresas/{id}/admin/reset-password}.
 *
 * <p>Ambos campos son opcionales, por lo que el endpoint admite tambien un
 * cuerpo vacio o ausente:</p>
 * <ul>
 *   <li>{@code password}: si se proporciona, se fija esa contrasena (8..255; un
 *       valor fuera de rango produce 422 desde el servicio); si se omite o viene
 *       en blanco, el Sistema genera una contrasena temporal robusta y la
 *       devuelve una unica vez.</li>
 *   <li>{@code usuarioId}: identificador de un Usuario concreto de la Empresa a
 *       restablecer, util para desambiguar cuando la Empresa tiene mas de un
 *       {@code admin_empresa}. Debe pertenecer a esa Empresa (si no, 404). Si se
 *       omite, se restablece el {@code admin_empresa} de la Empresa (el primero
 *       por {@code id} de forma determinista cuando hay varios).</li>
 * </ul>
 *
 * <p><strong>Secretos (Req 11.3):</strong> {@code password} es una contrasena en
 * claro; nunca se registra en logs ni se audita. La cota se valida temprano con
 * {@link Size} solo cuando se proporciona (no aplica a {@code null}).</p>
 *
 * @param password  contrasena explicita opcional (8..255 si se proporciona).
 * @param usuarioId Usuario concreto de la Empresa a restablecer; opcional.
 */
public record ResetPasswordAdminRequest(
        @Size(min = 8, max = 255) String password,
        UUID usuarioId) {
}
