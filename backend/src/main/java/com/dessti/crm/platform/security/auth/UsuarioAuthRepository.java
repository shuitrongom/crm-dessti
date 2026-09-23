package com.dessti.crm.platform.security.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de solo lectura para la autenticacion (tarea 9.1).
 *
 * <p>Resuelve al Usuario por su {@code identificador_acceso} (unico global,
 * ver decision de la migracion V1) y permite cargar sus roles y permisos
 * atomicos para construir los claims del token. Las consultas de roles/permisos
 * son <b>nativas</b> porque las entidades JPA de {@code rol}/{@code permiso} se
 * introducen en tareas posteriores (10/11); aqui solo se necesitan sus nombres.</p>
 */
public interface UsuarioAuthRepository extends JpaRepository<UsuarioAuth, UUID> {

    /**
     * Busca un Usuario por su identificador de acceso (case-sensitive, tal como
     * se almacena). La verificacion de contrasena y del estado {@code activo} la
     * realiza el servicio de autenticacion.
     */
    Optional<UsuarioAuth> findByIdentificadorAcceso(String identificadorAcceso);

    /**
     * @return los nombres de los roles asignados al Usuario.
     */
    @Query(value = """
            SELECT r.nombre
            FROM usuario_rol ur
            JOIN rol r ON r.id = ur.rol_id
            WHERE ur.usuario_id = :usuarioId
            """, nativeQuery = true)
    List<String> buscarNombresRoles(@Param("usuarioId") UUID usuarioId);

    /**
     * @return los permisos atomicos del Usuario en formato
     *         {@code recurso:operacion}, derivados de sus roles.
     */
    @Query(value = """
            SELECT DISTINCT p.recurso || ':' || p.operacion
            FROM usuario_rol ur
            JOIN rol_permiso rp ON rp.rol_id = ur.rol_id
            JOIN permiso p ON p.id = rp.permiso_id
            WHERE ur.usuario_id = :usuarioId
            """, nativeQuery = true)
    List<String> buscarPermisos(@Param("usuarioId") UUID usuarioId);
}
