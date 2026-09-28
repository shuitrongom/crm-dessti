package com.dessti.crm.tesoreria.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.tesoreria.domain.TransferenciaBancaria;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link TransferenciaBancaria}
 * (Req 43, 23). Como {@link TransferenciaBancaria} extiende
 * {@code TenantScopedEntity}, el filtro global de Hibernate (Capa 1) y la RLS de V76
 * (Capa 2) acotan estas consultas al tenant vigente (Req 23).
 */
public interface TransferenciaBancariaRepository
        extends JpaRepository<TransferenciaBancaria, UUID> {

    /**
     * Busca una Transferencia_Bancaria por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la transferencia.
     * @return la transferencia, o vacio (que la aplicacion traduce a 404).
     */
    Optional<TransferenciaBancaria> findById(UUID id);

    /**
     * Listado paginado de Transferencias_Bancarias del tenant vigente con filtro
     * opcional por Cuenta_Bancaria (origen o destino). Un filtro nulo se ignora.
     *
     * @param cuentaId Cuenta_Bancaria a filtrar (origen o destino); {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de transferencias que cumplen el filtro, mas recientes primero.
     */
    @Query("""
            SELECT t FROM TransferenciaBancaria t
            WHERE (:cuentaId IS NULL
                   OR t.cuentaOrigenId = :cuentaId
                   OR t.cuentaDestinoId = :cuentaId)
            ORDER BY t.fecha DESC, t.fechaRegistro DESC
            """)
    Page<TransferenciaBancaria> buscarConFiltros(
            @Param("cuentaId") UUID cuentaId,
            Pageable pageable);
}
