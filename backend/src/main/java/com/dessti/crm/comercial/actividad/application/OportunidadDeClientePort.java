package com.dessti.crm.comercial.actividad.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica que una Oportunidad exista en el tenant vigente y
 * pertenezca a un Cliente dado, requerido al vincular una {@link
 * com.dessti.crm.comercial.actividad.domain.Actividad} a una Oportunidad.
 *
 * <p>Comprueba <em>ambas</em> condiciones (existencia y pertenencia al Cliente)
 * para impedir que una Actividad quede vinculada a una Oportunidad de otro
 * Cliente, preservando la coherencia del timeline. Se introduce un puerto propio
 * para desacoplar la aplicacion de Actividades de la persistencia de
 * Oportunidades (coherente con el patron de {@code oportunidad}). El aislamiento
 * por tenant lo garantizan el filtro global de Hibernate y la RLS (Req 23).</p>
 */
public interface OportunidadDeClientePort {

    /**
     * Indica si existe una Oportunidad con el identificador dado en el tenant
     * vigente cuyo Cliente coincide con {@code clienteId}.
     *
     * @param oportunidadId identificador de la Oportunidad a verificar.
     * @param clienteId     Cliente que debe ser dueno de la Oportunidad.
     * @return {@code true} si la Oportunidad existe y pertenece al Cliente.
     */
    boolean existeOportunidadDeCliente(UUID oportunidadId, UUID clienteId);
}
