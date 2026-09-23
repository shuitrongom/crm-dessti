package com.dessti.crm.platform.monetizacion.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.platform.monetizacion.domain.CatalogoModulo;

/** Repositorio de {@link CatalogoModulo} (catalogo de modulos de plataforma, V22). */
public interface CatalogoModuloRepository extends JpaRepository<CatalogoModulo, UUID> {

    /** Busca un modulo por su clave canonica (unica). */
    Optional<CatalogoModulo> findByClave(String clave);

    /** Indica si ya existe un modulo con esa clave (para el conflicto 409). */
    boolean existsByClave(String clave);

    /** Busca un modulo activo por id. */
    Optional<CatalogoModulo> findByIdAndActivoTrue(UUID id);

    /** Listado paginado de modulos activos. */
    Page<CatalogoModulo> findByActivoTrue(Pageable pageable);
}