package com.dessti.crm.platform.empresas;

import java.util.UUID;

/**
 * Resultado del restablecimiento de la contrasena del {@code admin_empresa} de
 * una Empresa por parte del {@code super_admin} (CHANGE 3).
 *
 * <p><strong>Secreto expuesto una unica vez (Req 11.3):</strong> el campo
 * {@link #passwordTemporal} contiene la contrasena en claro <em>solo</em> cuando
 * el {@code super_admin} no proporciono una explicita y el Sistema la genero. Se
 * devuelve una sola vez en esta respuesta para que el {@code super_admin} la
 * entregue al Administrador_Empresa; nunca se persiste en claro ni se incluye en
 * la auditoria. Si el {@code super_admin} fijo una contrasena explicita, este
 * campo es {@code null}.</p>
 *
 * @param usuarioId       identificador del Usuario {@code admin_empresa} afectado.
 * @param identificador   identificador de acceso del {@code admin_empresa}.
 * @param passwordTemporal contrasena temporal en claro generada por el Sistema;
 *                        {@code null} si el {@code super_admin} fijo una explicita.
 */
public record ResetPasswordAdminDto(
        UUID usuarioId,
        String identificador,
        String passwordTemporal) {
}
