package com.dessti.crm.platform.empresas;

import java.util.List;
import java.util.UUID;

import com.dessti.crm.platform.security.usuarios.Usuario;

/**
 * Vista de <strong>plataforma</strong> de una cuenta de Usuario de una Empresa,
 * para que el {@code super_admin} pueda consultar el identificador de acceso
 * (login) de los Usuarios de un tenant cuando su Administrador_Empresa lo olvida
 * (caso de soporte). Expone datos de identidad/estado de la cuenta, nunca datos
 * de negocio de la Empresa (Req 24.3) ni el hash de la contrasena (Req 11.3).
 *
 * @param id                  identificador del Usuario.
 * @param identificadorAcceso identificador de acceso (login) del Usuario.
 * @param nombreVisible       nombre para mostrar; puede ser {@code null}.
 * @param activo              si la cuenta puede iniciar sesion.
 * @param roles               nombres de los roles asignados a la cuenta.
 */
public record UsuarioEmpresaDto(
        UUID id,
        String identificadorAcceso,
        String nombreVisible,
        boolean activo,
        List<String> roles) {

    /**
     * Proyecta una entidad {@link Usuario} a su vista de plataforma, omitiendo por
     * completo el hash de la contrasena (Req 11.3) y ordenando los nombres de rol
     * de forma estable (sin distinguir mayusculas/minusculas) para una salida
     * determinista.
     *
     * @param usuario entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static UsuarioEmpresaDto de(Usuario usuario) {
        List<String> roles = usuario.getRoles().stream()
                .map(rol -> rol.getNombre())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new UsuarioEmpresaDto(
                usuario.getId(),
                usuario.getIdentificadorAcceso(),
                usuario.getNombreVisible(),
                usuario.isActivo(),
                roles);
    }
}
