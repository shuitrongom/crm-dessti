package com.dessti.crm.platform.security.roles;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Rol} (Req 27, 28).
 *
 * <p>Dado que {@code rol} no es tenant-scoped a nivel de Hibernate (su
 * {@code tenant_id} es nullable, ver {@link Rol}), el aislamiento por Empresa de
 * los roles personalizados se aplica explicitamente en las consultas de este
 * repositorio incluyendo {@code tenant_id} en los criterios.</p>
 */
public interface RolRepository extends JpaRepository<Rol, UUID> {

    /**
     * Comprueba si ya existe un rol con ese nombre dentro de la Empresa. Sirve
     * para anticipar el conflicto de unicidad por tenant (Req 28.2) antes de
     * confiar en el indice parcial {@code uq_rol_nombre_por_tenant} de la BD.
     *
     * @param tenantId Empresa propietaria.
     * @param nombre   nombre del rol.
     * @return {@code true} si ya existe.
     */
    boolean existsByTenantIdAndNombre(UUID tenantId, String nombre);

    /**
     * Busca un rol personalizado por id dentro del ambito de una Empresa,
     * garantizando el aislamiento por tenant (un rol de otra Empresa no se
     * resuelve, Req 23).
     *
     * @param id       identificador del rol.
     * @param tenantId Empresa propietaria esperada.
     * @return el rol si pertenece a esa Empresa.
     */
    Optional<Rol> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Busca un rol predefinido de Sistema por su nombre (tenant_id NULL). Sirve
     * para localizar roles predefinidos como {@code admin_empresa} al
     * aprovisionar el primer Usuario de una Empresa recien creada (Req 24.2),
     * sin depender de sus UUID fijos sembrados en V5.
     *
     * @param nombre nombre del rol predefinido (por ejemplo {@code admin_empresa}).
     * @return el rol predefinido si existe.
     */
    Optional<Rol> findByNombreAndTenantIdIsNull(String nombre);
}
