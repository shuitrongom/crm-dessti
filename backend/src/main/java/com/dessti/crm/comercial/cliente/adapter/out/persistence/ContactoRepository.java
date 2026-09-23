package com.dessti.crm.comercial.cliente.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.comercial.cliente.domain.Contacto;

/**
 * Repositorio Spring Data JPA de la entidad {@link Contacto} (Req 5.5, 23).
 *
 * <p>Como {@link Contacto} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate (Capa 1) y la Row-Level Security (Capa 2, V11) acotan las
 * consultas al tenant vigente automaticamente (Req 23).</p>
 */
public interface ContactoRepository extends JpaRepository<Contacto, UUID> {

    /**
     * Lista los Contactos <strong>activos</strong> asociados a un Cliente dentro
     * del tenant vigente, ordenados por nombre. Util para exponer los Contactos
     * de un Cliente (la API es de la tarea 15.2).
     *
     * @param clienteId identificador del Cliente propietario.
     * @return la lista de Contactos activos del Cliente en el tenant.
     */
    List<Contacto> findByClienteIdAndActivoTrueOrderByNombreAsc(UUID clienteId);
}
