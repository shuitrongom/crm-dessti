package com.dessti.crm.tesoreria.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.tesoreria.domain.CuentaBancaria;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link CuentaBancaria}
 * (Req 43, 23). Como {@link CuentaBancaria} extiende {@code TenantScopedEntity}, el
 * filtro global de Hibernate (Capa 1) y la RLS de V35 (Capa 2) acotan estas
 * consultas al tenant vigente (Req 23).
 */
public interface CuentaBancariaRepository extends JpaRepository<CuentaBancaria, UUID> {

    /**
     * Busca una Cuenta_Bancaria por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la cuenta.
     * @return la cuenta, o vacio (que la aplicacion traduce a 404).
     */
    Optional<CuentaBancaria> findById(UUID id);

    /**
     * Listado paginado de Cuentas_Bancarias del tenant vigente con filtro opcional
     * por bandera de actividad (Req 43.1). Un filtro nulo se ignora.
     *
     * @param activa   bandera de actividad a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de cuentas que cumplen el filtro.
     */
    @Query("""
            SELECT c FROM CuentaBancaria c
            WHERE (:activa IS NULL OR c.activa = :activa)
            """)
    Page<CuentaBancaria> buscarConFiltros(
            @Param("activa") Boolean activa,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Cuentas_Bancarias activas
     * del tenant vigente (Req 22.1). Acotada al {@code tenant_id} vigente por el filtro de
     * Hibernate y la RLS de V35 (Req 23); no modifica dato alguno (Req 22.2).
     *
     * @return el conteo de Cuentas_Bancarias activas del tenant.
     */
    long countByActivaTrue();
}
