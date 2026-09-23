package com.dessti.crm.platform.monetizacion.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.platform.monetizacion.domain.PrecioModulo;

/** Repositorio de {@link PrecioModulo} (precios de lista por moneda, V22). */
public interface PrecioModuloRepository extends JpaRepository<PrecioModulo, UUID> {

    /** Precio de lista de un modulo en una moneda concreta (unico por par). */
    Optional<PrecioModulo> findByCatalogoModuloIdAndMonedaCodigo(UUID catalogoModuloId, String monedaCodigo);

    /** Todos los precios de lista de un modulo (en sus distintas monedas). */
    List<PrecioModulo> findByCatalogoModuloId(UUID catalogoModuloId);

    /**
     * Todos los precios de lista definidos en una moneda concreta (una sola
     * consulta para armar el mapa modulo -&gt; precio de la moneda principal).
     */
    List<PrecioModulo> findByMonedaCodigo(String monedaCodigo);
}