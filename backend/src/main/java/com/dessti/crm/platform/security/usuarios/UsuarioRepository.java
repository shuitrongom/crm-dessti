package com.dessti.crm.platform.security.usuarios;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de la entidad de gestion {@link Usuario} (Req 4).
 *
 * <p>Como {@code usuario} no es tenant-scoped a nivel de Hibernate (su
 * {@code tenant_id} es nullable, ver {@link Usuario}), el aislamiento por
 * Empresa se aplica explicitamente incluyendo {@code tenant_id} en los
 * criterios de las consultas de gestion (Req 23).</p>
 */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /**
     * Busca una cuenta por id dentro del ambito de una Empresa, garantizando el
     * aislamiento por tenant: una cuenta de otra Empresa no se resuelve, lo que
     * el servicio traduce a 404 para no revelar su existencia (Req 23.3).
     *
     * @param id       identificador de la cuenta.
     * @param tenantId Empresa propietaria esperada.
     * @return la cuenta si pertenece a esa Empresa.
     */
    Optional<Usuario> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Comprueba si ya existe una cuenta con ese identificador de acceso. El
     * identificador es UNICO <strong>GLOBAL</strong> segun la decision de la
     * migracion V1 ({@code uq_usuario_identificador_acceso}), por lo que la
     * comprobacion NO se acota por tenant. Sirve para anticipar el conflicto de
     * duplicado (Req 4.4) antes de confiar en el indice unico de la BD.
     *
     * @param identificadorAcceso identificador de acceso a comprobar.
     * @return {@code true} si ya existe una cuenta con ese identificador.
     */
    boolean existsByIdentificadorAcceso(String identificadorAcceso);

    /**
     * Cuenta las cuentas <strong>activas</strong> de una Empresa (Req 25.3).
     * Sirve para aplicar el limite de Usuarios del Plan: antes de crear una
     * cuenta, el servicio compara este conteo con {@code max_usuarios} del Plan
     * vigente de la Empresa. Solo cuentan las cuentas {@code activo = true}: una
     * cuenta desactivada no consume cupo del Plan.
     *
     * @param tenantId Empresa (tenant) cuyas cuentas activas se cuentan.
     * @return el numero de cuentas activas de esa Empresa.
     */
    long countByTenantIdAndActivoTrue(UUID tenantId);

    /**
     * Localiza las cuentas de una Empresa que poseen un Rol con el nombre dado
     * (por ejemplo {@code admin_empresa}), ordenadas de forma <strong>estable</strong>
     * por {@code id} ascendente. Sirve al restablecimiento de contrasena del
     * {@code admin_empresa} por el {@code super_admin} (CHANGE 3): resuelve de
     * forma determinista al administrador destino de un tenant sin depender del
     * UUID del rol.
     *
     * <p>El orden por {@code id} garantiza que, cuando una Empresa tiene mas de
     * un {@code admin_empresa}, la seleccion del "primer" administrador sea
     * determinista y reproducible entre ejecuciones.</p>
     *
     * @param tenantId   Empresa (tenant) cuyas cuentas se buscan.
     * @param nombreRol  nombre del Rol requerido (p. ej. {@code admin_empresa}).
     * @return las cuentas de esa Empresa con ese Rol, ordenadas por {@code id}.
     */
    @Query("select distinct u from Usuario u join u.roles r "
            + "where u.tenantId = :tenantId and r.nombre = :nombreRol "
            + "order by u.id asc")
    List<Usuario> buscarPorTenantYRol(@Param("tenantId") UUID tenantId,
                                      @Param("nombreRol") String nombreRol);

    /**
     * Lista paginada de las cuentas de una Empresa (tenant), con una busqueda
     * textual OPCIONAL {@code q} que coincide (contiene, sin distinguir
     * mayusculas/minusculas) en el identificador de acceso o en el nombre
     * visible (Req 4). Sirve al listado de Usuarios de la Empresa en la interfaz
     * de administracion, para operar seleccionando una fila sin teclear UUID.
     *
     * <p><strong>Null-safe:</strong> cuando {@code q} es {@code null} (sin
     * busqueda) la condicion {@code (:q IS NULL OR ...)} deja pasar TODAS las
     * cuentas del tenant. El bind {@code :q} se envuelve en {@code CAST(:q AS
     * string)} dentro de {@code CONCAT} para que Hibernate 6 emita
     * {@code cast(? as varchar)} y PostgreSQL infiera el tipo textual del
     * parametro: sin el cast, con {@code :q} nulo el planificador type-checkea
     * {@code LOWER(CONCAT('%', :q, '%'))} antes del corto-circuito y falla con
     * {@code lower(bytea)}. El {@code nombre_visible} puede ser {@code null} (columna
     * opcional de V57): se protege con {@code COALESCE(u.nombreVisible, '')}
     * para que {@code LIKE} nunca reciba {@code NULL} en ese campo.</p>
     *
     * @param tenantId Empresa (tenant) cuyas cuentas se listan.
     * @param q        texto de busqueda ya recortado por el servicio, o
     *                 {@code null} para listar todas.
     * @param pageable parametros de paginacion (acotados a 20/100).
     * @return la pagina de cuentas de la Empresa que coinciden con la busqueda.
     */
    @Query("""
            SELECT u FROM Usuario u
            WHERE u.tenantId = :tenantId
              AND (:q IS NULL
                   OR LOWER(u.identificadorAcceso) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(COALESCE(u.nombreVisible, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    Page<Usuario> buscarPorTenant(@Param("tenantId") UUID tenantId,
                                  @Param("q") String q, Pageable pageable);
}
