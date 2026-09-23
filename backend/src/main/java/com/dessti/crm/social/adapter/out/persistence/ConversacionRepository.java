package com.dessti.crm.social.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.Conversacion;
import com.dessti.crm.social.domain.EstadoConversacion;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Conversacion}
 * (Req 64.5, 23). Provee el upsert por remitente (recepcion de webhooks) y el
 * listado paginado de la Bandeja_Unificada con filtros (Req 64.14).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de
 * Hibernate y la RLS de PostgreSQL (V41) acotan estas consultas al {@code tenant_id}
 * vigente.</p>
 */
public interface ConversacionRepository extends JpaRepository<Conversacion, UUID> {

    /**
     * Busca una Conversacion por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Conversacion.
     * @return la Conversacion, o vacio (404 en la aplicacion, Req 23.3).
     */
    Optional<Conversacion> findById(UUID id);

    /**
     * Busca el hilo unico de un remitente en una cuenta de canal (Req 64.5). Es el
     * punto de upsert de la recepcion de webhooks: si existe se reutiliza, si no se
     * abre una nueva Conversacion. La unicidad {@code (tenant, cuenta, remitente)}
     * de V41 lo garantiza.
     *
     * @param cuentaCanalSocialId cuenta de canal.
     * @param remitenteExterno    identificador del remitente en el canal.
     * @return la Conversacion existente, o vacio.
     */
    Optional<Conversacion> findByCuentaCanalSocialIdAndRemitenteExterno(
            UUID cuentaCanalSocialId, String remitenteExterno);

    /**
     * Listado paginado de la Bandeja_Unificada del tenant con filtros opcionales por
     * canal, Cliente y estado (Req 64.5, 64.14). Cada filtro nulo se ignora. El
     * orden lo aporta el {@link Pageable}.
     *
     * @param canal     Canal_Social a filtrar; {@code null} no filtra.
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param estado    estado a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Conversaciones que cumplen los filtros.
     */
    @Query("""
            SELECT c FROM Conversacion c
            WHERE (:canal IS NULL OR c.canal = :canal)
              AND (:clienteId IS NULL OR c.clienteId = :clienteId)
              AND (:estado IS NULL OR c.estado = :estado)
            """)
    Page<Conversacion> buscarBandeja(
            @Param("canal") CanalSocial canal,
            @Param("clienteId") UUID clienteId,
            @Param("estado") EstadoConversacion estado,
            Pageable pageable);
}
