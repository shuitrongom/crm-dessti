package com.dessti.crm.operacion.produccion.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la accesibilidad de un Material <strong>activo</strong>
 * dentro del tenant vigente, requerido al registrar las partidas de una
 * Orden_Fabricacion (Req 5.3, §B1). Desacopla el modulo de produccion del modulo de
 * inventario de Materiales: el adaptador {@code MaterialAccesibleAdapter} lo
 * implementa delegando en {@code MaterialRepository.findByIdAndActivoTrue}, mismo
 * mecanismo real que usa {@code MaterialExistentePort} en Requisiciones.
 *
 * <p>El aislamiento por tenant lo garantiza el filtro global de Hibernate y la RLS
 * (Req 23), de modo que un Material de otro tenant no se considera accesible.</p>
 */
public interface MaterialAccesiblePort {

    /**
     * Indica si existe un Material activo con el identificador dado en el tenant
     * vigente.
     *
     * @param materialId identificador del Material a verificar.
     * @return {@code true} si el Material existe y esta activo en el tenant.
     */
    boolean esAccesible(UUID materialId);
}
