package com.dessti.crm.platform.monetizacion.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.platform.monetizacion.domain.Moneda;

/** Repositorio de {@link Moneda} (catalogo de monedas de plataforma, V22). */
public interface MonedaRepository extends JpaRepository<Moneda, String> {

    /** Busca una moneda por su codigo ISO 4217 (clave primaria). */
    Optional<Moneda> findByCodigo(String codigo);

    /** Lista las monedas activas. */
    List<Moneda> findByActivoTrueOrderByCodigoAsc();
}