package com.dessti.crm.social.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.CuentaCanalSocial;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link CuentaCanalSocial}
 * (Req 64.1, 23). Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link CuentaCanalSocial} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas
 * consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V41) lo
 * refuerza.</p>
 */
public interface CuentaCanalSocialRepository extends JpaRepository<CuentaCanalSocial, UUID> {

    /**
     * Busca una cuenta por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Cuenta_Canal_Social.
     * @return la cuenta, o vacio (que la aplicacion traduce a 404, Req 23.3).
     */
    Optional<CuentaCanalSocial> findById(UUID id);

    /**
     * Busca la cuenta de un canal por su identificador externo dentro del tenant.
     * Es el punto de entrada de la recepcion de webhooks (resolver la cuenta que
     * recibio el evento).
     *
     * @param canal                 Canal_Social.
     * @param identificadorExterno  identificador externo de la cuenta (numero WA /
     *                              page id / ig id).
     * @return la cuenta, o vacio.
     */
    Optional<CuentaCanalSocial> findByCanalAndIdentificadorExterno(
            CanalSocial canal, String identificadorExterno);

    /**
     * Indica si ya existe una cuenta para el canal e identificador externo en el
     * tenant (pre-verificacion de la unicidad de V41).
     *
     * @param canal                Canal_Social.
     * @param identificadorExterno identificador externo de la cuenta.
     * @return {@code true} si ya existe.
     */
    boolean existsByCanalAndIdentificadorExterno(CanalSocial canal, String identificadorExterno);

    /**
     * Listado paginado de cuentas del tenant con filtro opcional por canal
     * (Req 64.1).
     *
     * @param canal    Canal_Social a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de cuentas.
     */
    @Query("""
            SELECT c FROM CuentaCanalSocial c
            WHERE (:canal IS NULL OR c.canal = :canal)
            """)
    Page<CuentaCanalSocial> buscarConFiltros(@Param("canal") CanalSocial canal, Pageable pageable);
}
