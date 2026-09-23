package com.dessti.crm.contabilidad.cxc.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.contabilidad.cxc.domain.PagoCliente;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link PagoCliente} (Req 36,
 * 23). Como {@link PagoCliente} extiende {@code TenantScopedEntity}, el filtro
 * global de Hibernate (Capa 1) y la RLS de V31 (Capa 2) acotan estas consultas al
 * tenant vigente (Req 23).
 */
public interface PagoClienteRepository extends JpaRepository<PagoCliente, UUID> {

    /**
     * Busca un Pago_Cliente por su identificador dentro del tenant vigente.
     *
     * @param id identificador del Pago_Cliente.
     * @return el Pago_Cliente, o vacio (que la aplicacion traduce a 404).
     */
    Optional<PagoCliente> findById(UUID id);

    /**
     * Listado paginado de Pagos de Cliente del tenant vigente con filtro opcional
     * por Cliente (Req 36.6). Un filtro nulo se ignora.
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Pagos de Cliente que cumplen el filtro.
     */
    @Query("""
            SELECT p FROM PagoCliente p
            WHERE (:clienteId IS NULL OR p.clienteId = :clienteId)
            """)
    Page<PagoCliente> buscarConFiltros(
            @Param("clienteId") UUID clienteId,
            Pageable pageable);
}
