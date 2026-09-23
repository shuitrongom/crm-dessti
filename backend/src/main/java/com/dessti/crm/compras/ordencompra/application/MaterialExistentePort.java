package com.dessti.crm.compras.ordencompra.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la existencia de un Material <strong>activo</strong>
 * dentro del tenant vigente, requerido al crear las partidas de una Orden_Compra
 * (Req 31.1). Desacopla el submodulo de Ordenes de Compra del modulo de inventario
 * de Materiales: el adaptador {@code MaterialExistenteAdapter} lo implementa
 * delegando en {@code MaterialRepository.findByIdAndActivoTrue}.
 */
public interface MaterialExistentePort {

    /**
     * Indica si existe un Material activo con el identificador dado en el tenant
     * vigente. El aislamiento por tenant lo garantiza el filtro global de Hibernate
     * y la RLS (Req 23).
     *
     * @param materialId identificador del Material a verificar.
     * @return {@code true} si el Material existe y esta activo en el tenant.
     */
    boolean existeMaterialActivo(UUID materialId);
}
