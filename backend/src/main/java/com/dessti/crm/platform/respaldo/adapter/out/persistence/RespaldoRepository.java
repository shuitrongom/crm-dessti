package com.dessti.crm.platform.respaldo.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.platform.respaldo.domain.Respaldo;

/**
 * Repositorio de la bitacora de {@link Respaldo} (tabla de plataforma, V46).
 */
public interface RespaldoRepository extends JpaRepository<Respaldo, UUID> {

    /**
     * Historial paginado de ejecuciones, mas reciente primero.
     *
     * @param pageable parametros de paginacion.
     * @return pagina de ejecuciones ordenada por {@code instante} descendente.
     */
    Page<Respaldo> findAllByOrderByInstanteDesc(Pageable pageable);
}
