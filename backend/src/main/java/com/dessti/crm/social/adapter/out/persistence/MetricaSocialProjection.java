package com.dessti.crm.social.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.DireccionMensaje;

/**
 * Proyeccion Spring Data de <strong>solo lectura</strong> de una fila fuente de la
 * analitica social (Req 66.1): un Mensaje_Social unido a su Conversacion, con el
 * minimo de campos para agregar las metricas por Canal_Social y periodo. No expone
 * ninguna operacion de escritura: es una lectura que no modifica los datos de origen
 * (Req 66.1).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23, 66.6):</strong> como
 * {@code MensajeSocial} y {@code Conversacion} extienden {@code TenantScopedEntity},
 * el filtro global de Hibernate y la RLS de PostgreSQL (V41) acotan la consulta al
 * {@code tenant_id} vigente; ademas la proyeccion lo expone para poder verificar el
 * aislamiento aguas arriba.</p>
 */
public interface MetricaSocialProjection {

    /**
     * @return el {@code tenant_id} (Empresa) de la fila (Req 66.6).
     */
    UUID getTenantId();

    /**
     * @return el Canal_Social de la Conversacion.
     */
    CanalSocial getCanal();

    /**
     * @return la Conversacion a la que pertenece el Mensaje_Social (base del alcance).
     */
    UUID getConversacionId();

    /**
     * @return el sentido del Mensaje_Social (entrante/saliente).
     */
    DireccionMensaje getDireccion();

    /**
     * @return la marca temporal UTC del mensaje: recepcion si es entrante, envio si
     *         es saliente.
     */
    Instant getInstante();

    /**
     * @return {@code true} si la Conversacion esta vinculada a un Cliente o Contacto,
     *         lo que se contabiliza como lead/conversion (Req 66.1).
     */
    boolean getEsLead();
}
