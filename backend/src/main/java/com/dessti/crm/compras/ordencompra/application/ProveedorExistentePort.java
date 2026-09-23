package com.dessti.crm.compras.ordencompra.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la existencia de un Proveedor
 * <strong>activo</strong> dentro del tenant vigente, requerido al crear una
 * Orden_Compra (Req 31.1).
 *
 * <p>Se introduce un puerto propio del submodulo de Ordenes de Compra —en lugar de
 * inyectar directamente el repositorio de Proveedores— para
 * <strong>desacoplar</strong> este submodulo del de Proveedores: la aplicacion de
 * Ordenes de Compra solo necesita saber si un Proveedor existe, no conocer su
 * modelo de persistencia. El adaptador {@code ProveedorExistenteAdapter} lo
 * implementa delegando en {@code ProveedorRepository.existsByIdAndActivoTrue}
 * (ambos en el mismo modulo compras). Sigue el mismo patron que
 * {@code ClienteExistentePort} del submodulo de Cotizaciones.</p>
 */
public interface ProveedorExistentePort {

    /**
     * Indica si existe un Proveedor activo con el identificador dado en el tenant
     * vigente (Req 31.1). El aislamiento por tenant lo garantiza el filtro global
     * de Hibernate y la RLS (Req 23).
     *
     * @param proveedorId identificador del Proveedor a verificar.
     * @return {@code true} si el Proveedor existe y esta activo en el tenant.
     */
    boolean existeProveedorActivo(UUID proveedorId);
}
