package com.dessti.crm.platform.empresas;

import java.util.UUID;

/**
 * Resultado del alta de una Empresa (Req 24.2): la Empresa creada junto con los
 * datos del primer Usuario {@code admin_empresa} aprovisionado y, cuando se
 * genero automaticamente, su contrasena temporal.
 *
 * <p><strong>Secreto expuesto una unica vez (Req 11.3):</strong> el campo
 * {@link #adminPasswordTemporal} contiene la contrasena en claro <em>solo</em>
 * cuando el {@code super_admin} no la proporciono y el Sistema la genero. Se
 * devuelve una sola vez en esta respuesta de creacion para que el
 * {@code super_admin} la entregue al Administrador_Empresa; nunca se persiste en
 * claro, no se vuelve a exponer en ninguna consulta posterior y no se incluye en
 * la auditoria (Req 10.10, 11.3). Si el {@code super_admin} proporciono la
 * contrasena, este campo es {@code null}.</p>
 *
 * @param empresa               la Empresa creada (datos de plataforma).
 * @param adminUsuarioId        identificador del primer Usuario {@code admin_empresa}.
 * @param adminIdentificador    identificador de acceso del {@code admin_empresa}.
 * @param adminPasswordTemporal contrasena temporal en claro generada por el
 *                              Sistema; {@code null} si el {@code super_admin} la
 *                              proporciono. Se expone una unica vez.
 */
public record EmpresaCreadaDto(
        EmpresaDto empresa,
        UUID adminUsuarioId,
        String adminIdentificador,
        String adminPasswordTemporal) {
}
