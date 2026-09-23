package com.dessti.crm.platform.security.roles;

/**
 * DTO de salida de un Rol ASIGNABLE por el {@code admin_empresa} de un tenant
 * (plataforma-multigiro): un rol predefinido de nivel empresa cuyo modulo
 * requerido (si lo tiene) esta contratado por la Empresa.
 *
 * <p>Sirve a la interfaz para ofrecer SOLO los roles que la Empresa puede
 * asignar, evitando que el operador teclee UUIDs o intente asignar roles de
 * modulos no contratados (que la capa de aplicacion rechazaria con 422).</p>
 *
 * @param id          identificador del rol (para usarlo directamente al crear/
 *                    editar un Usuario).
 * @param nombre      nombre del rol predefinido (p. ej. {@code ventas}).
 * @param modulo      clave del modulo REPRESENTATIVO que habilita el rol, o
 *                    {@code null} para los roles transversales de administracion/
 *                    direccion (siempre asignables).
 * @param descripcion breve descripcion legible del alcance del rol; puede ser
 *                    {@code null}.
 */
public record RolAsignableDto(
        java.util.UUID id,
        String nombre,
        String modulo,
        String descripcion) {
}
