package com.dessti.crm.notificaciones.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.notificaciones.domain.IntentoEnvioNotificacion;

/**
 * Repositorio Spring Data JPA del historial de {@link IntentoEnvioNotificacion}
 * (Req 46.3, 23). Cada intento de envio de una Notificacion se persiste como una
 * fila (append-only), preservando el resultado de cada intento.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link IntentoEnvioNotificacion} extiende {@code TenantScopedEntity}, el filtro
 * global de Hibernate acota las consultas al {@code tenant_id} vigente, reforzado
 * por la RLS de V42.</p>
 */
public interface IntentoEnvioNotificacionRepository
        extends JpaRepository<IntentoEnvioNotificacion, UUID> {

    /**
     * Recupera los intentos registrados de una Notificacion en el tenant vigente,
     * ordenados por numero de intento ascendente (Req 46.3).
     *
     * @param notificacionId identificador de la Notificacion.
     * @return la lista de intentos ordenada por {@code numeroIntento}.
     */
    List<IntentoEnvioNotificacion> findByNotificacionIdOrderByNumeroIntentoAsc(UUID notificacionId);
}
