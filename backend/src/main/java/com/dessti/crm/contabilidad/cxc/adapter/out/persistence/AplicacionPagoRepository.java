package com.dessti.crm.contabilidad.cxc.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.contabilidad.cxc.domain.AplicacionPago;

/**
 * Repositorio Spring Data JPA de la entidad {@link AplicacionPago} (Req 36.2, 23).
 * Como {@link AplicacionPago} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate (Capa 1) y la RLS de V31 (Capa 2) acotan estas consultas al tenant
 * vigente (Req 23).
 */
public interface AplicacionPagoRepository extends JpaRepository<AplicacionPago, UUID> {

    /**
     * Recupera las aplicaciones de un Pago_Cliente dentro del tenant vigente, para
     * componer el DTO del pago con su desglose por Factura (Req 36.2).
     *
     * @param pagoClienteId Pago_Cliente de origen.
     * @return las aplicaciones del pago (puede estar vacia).
     */
    List<AplicacionPago> findByPagoClienteId(UUID pagoClienteId);
}
