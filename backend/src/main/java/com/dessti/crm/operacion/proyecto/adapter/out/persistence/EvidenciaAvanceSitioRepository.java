package com.dessti.crm.operacion.proyecto.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.operacion.proyecto.domain.FaseSitioGenerica;
import com.dessti.crm.operacion.proyecto.domain.evidencia.EstadoEvidencia;
import com.dessti.crm.operacion.proyecto.domain.evidencia.EvidenciaAvanceSitio;

/**
 * Repositorio Spring Data JPA de {@link EvidenciaAvanceSitio} (V84, Req 3.2, 23):
 * las evidencias documentales que respaldan el avance de un Sitio, con flujo de
 * aprobacion.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como la entidad extiende
 * {@code TenantScopedEntity}, el filtro global de Hibernate {@code tenantFilter}
 * (Capa 1) acota estas consultas al tenant vigente y la RLS de PostgreSQL (Capa 2,
 * V84) lo refuerza.</p>
 */
public interface EvidenciaAvanceSitioRepository extends JpaRepository<EvidenciaAvanceSitio, UUID> {

    /**
     * Lista las evidencias de un avance de Sitio, mas reciente primero.
     *
     * @param avanceSitioId avance de Sitio cuyas evidencias se solicitan.
     * @return las evidencias del avance (posiblemente vacio).
     */
    List<EvidenciaAvanceSitio> findByAvanceSitioIdOrderBySubidaEnDesc(UUID avanceSitioId);

    /**
     * Cuenta las evidencias de un avance en un estado concreto y fase dada. Sustenta
     * la guarda de entrega (Req 3.2): una fase solo puede entregarse si tiene al
     * menos una evidencia {@link EstadoEvidencia#APROBADA} de la fase que se cierra.
     *
     * @param avanceSitioId avance de Sitio.
     * @param fase          fase que la evidencia respalda.
     * @param estado        estado de aprobacion buscado.
     * @return numero de evidencias que cumplen; nunca negativo.
     */
    long countByAvanceSitioIdAndFaseAndEstado(UUID avanceSitioId, FaseSitioGenerica fase,
                                              EstadoEvidencia estado);
}
