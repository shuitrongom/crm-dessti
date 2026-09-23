package com.dessti.crm.estrategia.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.estrategia.domain.EsenciaEmpresa;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link EsenciaEmpresa}
 * (Req 58.1, 23). Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link EsenciaEmpresa} extiende {@code TenantScopedEntity}, la RLS de PostgreSQL
 * (Capa 2, V38) y el filtro global de Hibernate {@code tenantFilter} (Capa 1)
 * acotan las consultas al {@code tenant_id} vigente. Existe a lo sumo una esencia
 * por tenant (unicidad {@code (tenant_id)} en V38).</p>
 *
 * <p><strong>Lectura tenant-explicita (fix del 404):</strong> la consulta de lectura
 * filtra el {@code tenant_id} de forma EXPLICITA
 * ({@link #findFirstByTenantIdOrderByCreatedAtAsc(UUID)}) en lugar de depender solo
 * del filtro de Hibernate. Con {@code open-in-view=false} el filtro que
 * {@code TenantResolutionFilter} habilita en el filtro web se aplica sobre una
 * sesion efimera que la transaccion de servicio NO reutiliza, por lo que el
 * {@code WHERE tenant_id = ?} implicito podia perderse. Al filtrar el tenant en la
 * propia consulta, el servicio ({@code ServicioEstrategia}) resuelve el tenant desde
 * {@code TenantContext.require()} y la lectura es correcta con independencia del
 * estado del filtro de Hibernate; la RLS (Capa 2) lo refuerza porque el servicio
 * fija {@code app.current_tenant} en la misma transaccion.</p>
 */
public interface EsenciaEmpresaRepository extends JpaRepository<EsenciaEmpresa, UUID> {

    /**
     * Devuelve la esencia del tenant indicado de forma EXPLICITA, si existe. Se usa en
     * la lectura del servicio para no depender unicamente del filtro global de
     * Hibernate (que con {@code open-in-view=false} puede no estar habilitado sobre la
     * sesion transaccional del servicio). La unicidad {@code (tenant_id)} (V38)
     * garantiza a lo sumo una fila por tenant.
     *
     * @param tenantId identificador de la Empresa (tenant); obligatorio.
     * @return la esencia del tenant indicado, o vacio.
     */
    Optional<EsenciaEmpresa> findFirstByTenantIdOrderByCreatedAtAsc(UUID tenantId);
}
