package com.dessti.crm.platform.security.usuarios;

import java.util.List;
import java.util.UUID;

/**
 * DTO de salida de una cuenta de Usuario (Req 12.2), distinto de la entidad de
 * persistencia {@link Usuario}.
 *
 * <p><strong>Sin secretos (Req 11.3):</strong> este DTO NO incluye
 * {@code hash_password} ni ningun dato de contrasena. Expone unicamente los
 * datos administrativos: identificador de acceso, estado {@code activo} y los
 * identificadores/nombres de los Roles asignados.</p>
 *
 * @param id                  identificador de la cuenta.
 * @param identificadorAcceso identificador de acceso (login).
 * @param nombreVisible       nombre para mostrar (Req 4); puede ser {@code null}.
 * @param activo              si la cuenta puede iniciar sesion.
 * @param roles               roles asignados (id y nombre).
 */
public record UsuarioDto(UUID id, String identificadorAcceso, String nombreVisible,
                         boolean activo, List<RolAsignadoDto> roles) {

    /**
     * Vista minima de un Rol asignado, sin exponer sus permisos internos.
     *
     * @param id     identificador del rol.
     * @param nombre nombre del rol.
     */
    public record RolAsignadoDto(UUID id, String nombre) {
    }

    /**
     * Proyecta una entidad {@link Usuario} a su DTO de salida, omitiendo por
     * completo el hash de la contrasena (Req 11.3).
     *
     * @param usuario entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static UsuarioDto de(Usuario usuario) {
        List<RolAsignadoDto> roles = usuario.getRoles().stream()
                .map(r -> new RolAsignadoDto(r.getId(), r.getNombre()))
                .sorted((a, b) -> a.nombre().compareToIgnoreCase(b.nombre()))
                .toList();
        return new UsuarioDto(
                usuario.getId(),
                usuario.getIdentificadorAcceso(),
                usuario.getNombreVisible(),
                usuario.isActivo(),
                roles);
    }
}
