package com.dessti.crm.contabilidad.cxp.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puerto de entrada del submodulo Cuentas_Por_Pagar (CxP) que el modulo de compras
 * (facturas de proveedor) invoca para <strong>registrar la CxP al conciliar</strong>
 * una Factura_Proveedor (Req 42.1). Es la frontera hexagonal que desacopla compras
 * de la implementacion concreta de CxP: compras depende de esta interfaz (no de la
 * clase de servicio ni de la persistencia de CxP), lo que mantiene la dependencia
 * aciclica a nivel de interfaz.
 *
 * <h2>Contrato</h2>
 * <ul>
 *   <li>{@link #registrarPorFacturaProveedorConciliada(UUID, UUID, BigDecimal)} — al
 *       conciliar una Factura_Proveedor, registra su Cuenta_Por_Pagar por el saldo =
 *       total (Req 42.1). Es <strong>idempotente</strong>: si ya existe una CxP para
 *       esa factura, no hace nada (evita duplicados ante reintentos o doble
 *       conciliacion defensiva).</li>
 * </ul>
 *
 * <p>La implementacion ({@code ServicioCuentasPorPagar}) deriva el {@code tenant_id}
 * del contexto (nunca de la peticion, Req 23.4) y audita la operacion.</p>
 */
public interface CuentaPorPagarPort {

    /**
     * Registra la Cuenta_Por_Pagar de una Factura_Proveedor recien conciliada con
     * saldo igual al total (Req 42.1). Idempotente: si ya existe una CxP para
     * {@code facturaProveedorId} en el tenant vigente, no crea otra.
     *
     * @param facturaProveedorId Factura_Proveedor conciliada de origen; obligatoria.
     * @param proveedorId        Proveedor al que se le paga; obligatorio.
     * @param saldo              saldo (total) de la factura; no negativo.
     */
    void registrarPorFacturaProveedorConciliada(UUID facturaProveedorId, UUID proveedorId,
                                                BigDecimal saldo);
}
