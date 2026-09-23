package com.dessti.crm.tesoreria.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.tesoreria.domain.EstadoCuentaBancario;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link EstadoCuentaBancario}
 * (Req 43, 23). Como {@link EstadoCuentaBancario} extiende {@code TenantScopedEntity},
 * el filtro global de Hibernate (Capa 1) y la RLS de V35 (Capa 2) acotan estas
 * consultas al tenant vigente (Req 23).
 */
public interface EstadoCuentaBancarioRepository
        extends JpaRepository<EstadoCuentaBancario, UUID> {

    /**
     * Busca un Estado_Cuenta_Bancario por su identificador dentro del tenant vigente,
     * con su desglose de movimientos (fetch EAGER del agregado).
     *
     * @param id identificador del estado de cuenta.
     * @return el estado de cuenta, o vacio (que la aplicacion traduce a 404).
     */
    Optional<EstadoCuentaBancario> findById(UUID id);
}
