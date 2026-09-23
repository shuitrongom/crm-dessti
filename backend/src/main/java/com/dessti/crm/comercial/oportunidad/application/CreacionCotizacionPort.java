package com.dessti.crm.comercial.oportunidad.application;

import java.util.UUID;

/**
 * Puerto de salida que crea una Cotizacion a partir de una Oportunidad ganada
 * (Req 14.5). Es el <strong>punto de extension</strong> mediante el cual el
 * submodulo de Oportunidades desencadena la conversion sin depender del submodulo
 * de Cotizaciones.
 *
 * <p><strong>Coordinacion (tarea 17.2):</strong> la tabla y el dominio de
 * Cotizacion aun no existen en la tarea 17.1. Este puerto define el contrato para
 * que la tarea 17.2 provea su implementacion (creando la Cotizacion vinculada al
 * mismo Cliente y a la Oportunidad de origen y devolviendo su identificador). La
 * <em>guarda</em> de que la Oportunidad este en etapa {@code ganado} (Req 14.6) y
 * la auditoria de la conversion las aplica {@link ServicioOportunidades} antes de
 * invocar este puerto; el puerto solo materializa la creacion de la Cotizacion.</p>
 *
 * <p>Mientras la tarea 17.2 no aporte una implementacion, no existe ningun bean
 * que satisfaga este puerto; {@link ServicioOportunidades} lo recibe como
 * dependencia opcional y, en su ausencia, rechaza la conversion de forma
 * controlada indicando que la funcionalidad aun no esta disponible.</p>
 */
public interface CreacionCotizacionPort {

    /**
     * Crea una Cotizacion en estado inicial vinculada al Cliente y a la
     * Oportunidad indicados y devuelve su identificador (Req 14.5).
     *
     * @param oportunidadId identificador de la Oportunidad de origen (en etapa
     *                      {@code ganado}, ya verificado por el servicio).
     * @param clienteId     identificador del Cliente de la Oportunidad.
     * @param actor         identificador de quien realiza la conversion.
     * @return el identificador de la Cotizacion creada.
     */
    UUID crearDesdeOportunidad(UUID oportunidadId, UUID clienteId, String actor);
}
