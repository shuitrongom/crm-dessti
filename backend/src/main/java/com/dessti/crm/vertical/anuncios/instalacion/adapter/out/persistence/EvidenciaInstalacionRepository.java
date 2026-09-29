package com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.vertical.anuncios.instalacion.domain.EvidenciaInstalacion;

/**
 * Repositorio Spring Data JPA de las {@link EvidenciaInstalacion} adjuntas a una
 * Orden_Trabajo_Instalacion (Req 19.4, 23). Replica el patron tenant-scoped del
 * modulo, analogo a {@code LevantamientoFotoRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link EvidenciaInstalacion} extiende {@code TenantScopedEntity}, el filtro
 * global de Hibernate (Capa 1) y la RLS (Capa 2, V24) acotan estas consultas al
 * tenant vigente.</p>
 */
public interface EvidenciaInstalacionRepository extends JpaRepository<EvidenciaInstalacion, UUID> {

    /**
     * Devuelve las evidencias fotograficas adjuntas a una Orden_Trabajo_Instalacion
     * del tenant vigente (Req 19.4), ordenadas por instante de alta ascendente.
     *
     * @param ordenTrabajoInstalacionId OTI cuyas evidencias se recuperan.
     * @return la lista de evidencias (posiblemente vacia).
     */
    List<EvidenciaInstalacion> findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(
            UUID ordenTrabajoInstalacionId);

    /**
     * Indica si una Orden_Trabajo_Instalacion del tenant vigente tiene al menos una
     * evidencia fotografica adjunta (Req 19.6). Sustenta la guarda de cierre: una OTI
     * no puede completarse sin evidencia que respalde la instalacion.
     *
     * @param ordenTrabajoInstalacionId OTI cuya existencia de evidencias se comprueba.
     * @return {@code true} si existe al menos una evidencia; {@code false} en otro caso.
     */
    boolean existsByOrdenTrabajoInstalacionId(UUID ordenTrabajoInstalacionId);
}
