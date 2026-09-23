package com.dessti.crm.platform.security.perfil;

import java.util.List;
import java.util.UUID;

import com.dessti.crm.platform.security.usuarios.Usuario;

/**
 * DTO de salida del <strong>perfil propio</strong> del Usuario autenticado
 * (CHANGE 2). Expone unicamente los datos que el frontend necesita para el menu
 * de cuenta: el identificador de acceso legible, los roles y la Empresa.
 *
 * <p><strong>Sin secretos (Req 11.3):</strong> nunca incluye el hash de la
 * contrasena ni ningun otro dato sensible.</p>
 *
 * <p>El {@code id} es el UUID interno del Usuario (coincide con el {@code sub}
 * del JWT). El {@code identificador} es el login legible (p. ej.
 * {@code superadmin@dessti}). El {@code tenantId} es la Empresa del Usuario, o
 * {@code null} para el {@code super_admin} de plataforma.</p>
 *
 * @param id            identificador (UUID) del Usuario.
 * @param identificador identificador de acceso legible (login).
 * @param roles         nombres de los Roles asignados, ordenados de forma estable.
 * @param tenantId      Empresa del Usuario; {@code null} para el super_admin.
 */
public record PerfilDto(UUID id, String identificador, List<String> roles, UUID tenantId) {

    /**
     * Proyecta una entidad {@link Usuario} a su vista de perfil propio.
     *
     * @param usuario entidad a proyectar.
     * @return el DTO de perfil correspondiente.
     */
    public static PerfilDto de(Usuario usuario) {
        List<String> roles = usuario.getRoles().stream()
                .map(rol -> rol.getNombre())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new PerfilDto(
                usuario.getId(),
                usuario.getIdentificadorAcceso(),
                roles,
                usuario.getTenantId());
    }
}
