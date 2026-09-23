package com.dessti.crm.contabilidad.cxp.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.contabilidad.cxp.domain.ProgramacionPago;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link ProgramacionPago}
 * (Req 42, 23). Como {@link ProgramacionPago} extiende {@code TenantScopedEntity},
 * el filtro global de Hibernate (Capa 1) y la RLS de V33 (Capa 2) acotan estas
 * consultas al tenant vigente (Req 23).
 */
public interface ProgramacionPagoRepository extends JpaRepository<ProgramacionPago, UUID> {

    /**
     * Busca una Programacion_Pago por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Programacion_Pago.
     * @return la Programacion_Pago, o vacio (que la aplicacion traduce a 404).
     */
    Optional<ProgramacionPago> findById(UUID id);

    /**
     * Listado paginado de Programaciones de Pago del tenant vigente con filtro
     * opcional por Cuenta_Por_Pagar (Req 42.6). Un filtro nulo se ignora.
     *
     * @param cuentaPorPagarId Cuenta_Por_Pagar a filtrar; {@code null} no filtra.
     * @param pageable         parametros de paginacion ya acotados (20/100).
     * @return la pagina de Programaciones de Pago que cumplen el filtro.
     */
    @Query("""
            SELECT p FROM ProgramacionPago p
            WHERE (:cuentaPorPagarId IS NULL OR p.cuentaPorPagarId = :cuentaPorPagarId)
            """)
    Page<ProgramacionPago> buscarConFiltros(
            @Param("cuentaPorPagarId") UUID cuentaPorPagarId,
            Pageable pageable);
}
