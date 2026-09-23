package com.dessti.crm.social.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.ConsentimientoCanal;

/**
 * Repositorio Spring Data JPA del {@link ConsentimientoCanal} (Req 64.8, 64.9, 23).
 * El aislamiento multi-tenant lo aportan el filtro global de Hibernate y la RLS de
 * PostgreSQL (V41).
 *
 * <p>La <strong>vigencia</strong> del Opt_In de un sujeto en un canal la determina
 * el <em>ultimo</em> registro por {@code (canal, sujeto_externo)}: se considera
 * vigente si su estado es {@code opt_in} (Req 64.9). Este repositorio expone la
 * consulta del ultimo registro; la interpretacion la realiza la capa de
 * aplicacion.</p>
 */
public interface ConsentimientoCanalRepository extends JpaRepository<ConsentimientoCanal, UUID> {

    /**
     * Recupera el <strong>ultimo</strong> registro de consentimiento de un sujeto en
     * un canal dentro del tenant vigente, ordenado por marca temporal descendente
     * (Req 64.9). El estado de este registro determina la vigencia del Opt_In.
     *
     * @param canal         Canal_Social.
     * @param sujetoExterno identificador del sujeto en el canal (remitente).
     * @return el ultimo registro de consentimiento, o vacio si no hay ninguno.
     */
    Optional<ConsentimientoCanal> findFirstByCanalAndSujetoExternoOrderByRegistradoEnDesc(
            CanalSocial canal, String sujetoExterno);
}
