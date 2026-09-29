package com.dessti.crm.comercial.cliente.application;

import java.util.UUID;

/**
 * Puerto de <strong>solo lectura</strong> del submodulo de Clientes que expone si un
 * Cliente tiene actividad comercial ABIERTA (Req 5.10). Lo consume
 * {@code ServicioClientes.desactivarCliente} para impedir la baja logica de un
 * Cliente con negocio vivo: no se debe dar de baja un Cliente que todavia tiene una
 * Oportunidad en curso o una Cotizacion pendiente de resolucion.
 *
 * <p>Se introduce un puerto propio para <strong>desacoplar</strong> la aplicacion de
 * Clientes de la persistencia de Oportunidades y Cotizaciones, coherente con el
 * patron del bloque comercial ({@code ClienteExistentePort},
 * {@code OportunidadDeClientePort}). El adaptador {@code PipelineClienteAdapter}
 * (submodulo Clientes) lo implementa delegando en los repositorios de Oportunidad y
 * Cotizacion, acotados al tenant vigente por el filtro global de Hibernate y la RLS
 * (Req 23). Ambos submodulos pertenecen al mismo modulo {@code comercial-crm}.</p>
 *
 * <h2>Que cuenta como "abierto"</h2>
 * <ul>
 *   <li><strong>Oportunidad abierta:</strong> etapa NO final (distinta de
 *       {@code ganado} y {@code perdido}).</li>
 *   <li><strong>Cotizacion abierta:</strong> estado {@code borrador} o
 *       {@code enviada} (los estados finales {@code aprobada}/{@code rechazada} ya
 *       estan cerrados y no impiden la baja).</li>
 * </ul>
 */
public interface PipelineClientePort {

    /**
     * Indica si el Cliente dado tiene al menos una Oportunidad en una etapa no final
     * (distinta de {@code ganado}/{@code perdido}) en el tenant vigente (Req 5.10).
     *
     * @param clienteId identificador del Cliente a verificar.
     * @return {@code true} si el Cliente tiene una Oportunidad abierta.
     */
    boolean clienteTieneOportunidadesAbiertas(UUID clienteId);

    /**
     * Indica si el Cliente dado tiene al menos una Cotizacion en estado
     * {@code borrador} o {@code enviada} en el tenant vigente (Req 5.10).
     *
     * @param clienteId identificador del Cliente a verificar.
     * @return {@code true} si el Cliente tiene una Cotizacion abierta.
     */
    boolean clienteTieneCotizacionesAbiertas(UUID clienteId);
}
