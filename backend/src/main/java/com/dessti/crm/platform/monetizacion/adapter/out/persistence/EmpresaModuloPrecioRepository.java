package com.dessti.crm.platform.monetizacion.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.platform.monetizacion.domain.EmpresaModuloPrecio;

/** Repositorio de {@link EmpresaModuloPrecio} (precios especiales por Empresa, V22). */
public interface EmpresaModuloPrecioRepository extends JpaRepository<EmpresaModuloPrecio, UUID> {

    /** Precio especial de una Empresa para un modulo en una moneda (unico por trio). */
    Optional<EmpresaModuloPrecio> findByTenantIdAndCatalogoModuloIdAndMonedaCodigo(
            UUID tenantId, UUID catalogoModuloId, String monedaCodigo);

    /** Todos los precios especiales negociados por una Empresa. */
    List<EmpresaModuloPrecio> findByTenantId(UUID tenantId);
}