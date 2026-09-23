package com.dessti.crm.social.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.MensajeSocial;

/**
 * Repositorio Spring Data JPA del {@link MensajeSocial} (Req 64.4, 23). El
 * aislamiento multi-tenant lo aportan el filtro global de Hibernate y la RLS de
 * PostgreSQL (V41).
 */
public interface MensajeSocialRepository extends JpaRepository<MensajeSocial, UUID> {

    /**
     * Historial paginado de mensajes de una Conversacion del tenant, ordenado por el
     * {@link Pageable} (Req 64.5).
     *
     * @param conversacionId Conversacion.
     * @param pageable       parametros de paginacion ya acotados (20/100).
     * @return la pagina de mensajes de la Conversacion.
     */
    Page<MensajeSocial> findByConversacionId(UUID conversacionId, Pageable pageable);

    /**
     * Proyeccion de <strong>solo lectura</strong> de las filas fuente de la analitica
     * social (Req 66.1): une cada {@link MensajeSocial} con su Conversacion para
     * exponer el {@code tenant_id}, el Canal_Social, la Conversacion, la direccion, la
     * marca temporal del mensaje ({@code recibido_en} si entrante, {@code enviado_en}
     * si saliente) y si la Conversacion es un lead (vinculada a Cliente o Contacto).
     * Es aditiva y no modifica dato alguno (Req 66.1).
     *
     * <p>Filtros opcionales (Req 66.4): por Canal_Social y por rango temporal UTC
     * {@code [desde, hasta)} sobre la marca del mensaje. El filtro de Canal_Social nulo
     * se ignora. Los limites {@code desde}/{@code hasta} llegan SIEMPRE no nulos:
     * el llamador ({@code ServicioAnaliticaSocial}) los acota con los centinelas de
     * {@code RangoPeriodo} ({@code INSTANTE_MINIMO}/{@code INSTANTE_MAXIMO}) cuando la
     * fecha es nula, igual que las demas consultas de indicadores. Asi los binds viajan
     * tipados y PostgreSQL puede inferir su tipo (evita el error "could not determine
     * data type of parameter" del patron {@code (:desde IS NULL OR ...)}), sin alterar
     * los resultados: todo mensaje real cae dentro del rango centinela.
     * El aislamiento por tenant lo aportan el filtro global de Hibernate y la RLS de
     * V41 (Req 23, 66.6); la proyeccion tambien expone el {@code tenant_id} para su
     * verificacion aguas arriba.</p>
     *
     * @param canal Canal_Social a filtrar; {@code null} incluye todos.
     * @param desde instante minimo (inclusivo) del mensaje; no nulo (centinela si "sin limite").
     * @param hasta instante maximo (exclusivo) del mensaje; no nulo (centinela si "sin limite").
     * @return las filas fuente de la analitica social del tenant vigente (solo lectura).
     */
    @Query("""
            SELECT m.tenantId AS tenantId,
                   c.canal AS canal,
                   c.id AS conversacionId,
                   m.direccion AS direccion,
                   COALESCE(m.recibidoEn, m.enviadoEn) AS instante,
                   (CASE WHEN (c.clienteId IS NOT NULL OR c.contactoId IS NOT NULL)
                         THEN true ELSE false END) AS esLead
            FROM MensajeSocial m
            JOIN Conversacion c ON c.id = m.conversacionId
            WHERE (:canal IS NULL OR c.canal = :canal)
              AND COALESCE(m.recibidoEn, m.enviadoEn) >= :desde
              AND COALESCE(m.recibidoEn, m.enviadoEn) < :hasta
            """)
    List<MetricaSocialProjection> agregarFilasMetricas(
            @Param("canal") CanalSocial canal,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);
}
