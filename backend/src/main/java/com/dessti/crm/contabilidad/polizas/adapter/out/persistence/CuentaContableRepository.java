package com.dessti.crm.contabilidad.polizas.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.contabilidad.polizas.domain.CuentaContable;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link CuentaContable}
 * (Req 38, 23). Como {@link CuentaContable} extiende {@code TenantScopedEntity}, el
 * filtro global de Hibernate (Capa 1) y la RLS de V33 (Capa 2) acotan estas
 * consultas al tenant vigente (Req 23).
 */
public interface CuentaContableRepository extends JpaRepository<CuentaContable, UUID> {

    /**
     * Busca una Cuenta_Contable por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la cuenta.
     * @return la cuenta, o vacio (que la aplicacion traduce a 404).
     */
    Optional<CuentaContable> findById(UUID id);

    /**
     * Indica si ya existe una Cuenta_Contable con el codigo indicado en el tenant
     * vigente (pre-check del UNIQUE (tenant_id, codigo) de V33, Req 38.1).
     *
     * @param codigo codigo de la cuenta.
     * @return {@code true} si ya existe una cuenta con ese codigo.
     */
    boolean existsByCodigo(String codigo);

    /**
     * Busca una Cuenta_Contable por su codigo dentro del tenant vigente (Req 38.1).
     * Permite a los productores de eventos contables (por ejemplo la depreciacion de
     * Activos_Fijos, Req 44.3/38.2) resolver las cuentas estandar por su codigo
     * estable antes de armar la Poliza_Contable. Una cuenta inexistente o de otro
     * tenant produce {@link Optional#empty()}.
     *
     * @param codigo codigo de la cuenta (normalizado a mayusculas al persistir).
     * @return la cuenta, o vacio.
     */
    Optional<CuentaContable> findByCodigo(String codigo);

    /**
     * Listado paginado de Cuentas_Contables del tenant vigente con filtro opcional
     * por bandera de actividad (Req 38.1). Un filtro nulo se ignora.
     *
     * @param activa   bandera de actividad a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de cuentas que cumplen el filtro.
     */
    @Query("""
            SELECT c FROM CuentaContable c
            WHERE (:activa IS NULL OR c.activa = :activa)
            """)
    Page<CuentaContable> buscarConFiltros(
            @Param("activa") Boolean activa,
            Pageable pageable);
}
