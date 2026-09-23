package com.dessti.crm.estrategia.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.estrategia.domain.ResultadoClave;

/**
 * Repositorio Spring Data JPA del {@link ResultadoClave} (Req 58.8, 23). Aunque el
 * resultado clave es una entidad hija del agregado {@code ObjetivoEstrategico}, se
 * expone un repositorio para localizar un resultado por su identificador al
 * actualizar su valor actual (Req 58.8), coherente con el patron del sistema.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> {@link ResultadoClave}
 * extiende {@code TenantScopedEntity}; el filtro global de Hibernate y la RLS de
 * V38 acotan automaticamente estas consultas al {@code tenant_id} vigente. Un
 * resultado de otro tenant produce {@link Optional#empty()} (que la aplicacion
 * traduce a 404, Req 23.3).</p>
 */
public interface ResultadoClaveRepository extends JpaRepository<ResultadoClave, UUID> {

    /**
     * Busca un resultado clave por su identificador dentro del tenant vigente.
     *
     * @param id identificador del resultado clave.
     * @return el resultado clave, o vacio.
     */
    Optional<ResultadoClave> findById(UUID id);
}
