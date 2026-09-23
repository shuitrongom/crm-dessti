package com.dessti.crm.platform.security.rbac;

import java.util.UUID;

/**
 * Puerto de consulta para el <em>gating</em> de modulos por Plan (Req 25.4).
 *
 * <p>Define el contrato que permite al {@link Autorizador} decidir si un modulo
 * de negocio esta habilitado en el Plan contratado por una Empresa. La
 * implementacion completa —que carga el Plan y su conjunto de
 * {@code modulos_habilitados} a partir de la Suscripcion vigente— corresponde a
 * la <strong>tarea 14.2 (Planes y Suscripciones)</strong>.</p>
 *
 * <p>Hasta que dicha tarea provea una implementacion respaldada por persistencia,
 * el sistema usa {@link PlanModulosPermisivoPorDefecto}, que habilita todos los
 * modulos salvo que se configuren restricciones. De este modo la capa de
 * autorizacion queda lista para todos los modulos sin bloquear el desarrollo.</p>
 */
public interface PlanModulosPort {

    /**
     * Indica si un modulo esta habilitado para la Empresa indicada.
     *
     * @param tenantId identificador de la Empresa (tenant); nunca se toma de la
     *                 peticion sino del contexto autenticado (Req 23.4).
     * @param modulo   nombre canonico del modulo (p. ej. {@code facturacion}).
     * @return {@code true} si el modulo esta habilitado en el Plan de la Empresa;
     *         {@code false} en caso contrario (lo que deriva en 403, Req 25.4).
     */
    boolean moduloHabilitado(UUID tenantId, String modulo);
}
