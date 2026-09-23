package com.dessti.crm.platform.monetizacion.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.platform.monetizacion.domain.FacturaRenta;

/** Repositorio de {@link FacturaRenta} (facturas de renta de plataforma, V23). */
public interface FacturaRentaRepository extends JpaRepository<FacturaRenta, UUID> {

    /** Factura existente para una Empresa, periodo y moneda (unica). */
    Optional<FacturaRenta> findByTenantIdAndPeriodoAndMonedaCodigo(
            UUID tenantId, LocalDate periodo, String monedaCodigo);

    /** Facturas de una Empresa, mas recientes primero por periodo. */
    List<FacturaRenta> findByTenantIdOrderByPeriodoDesc(UUID tenantId);
}