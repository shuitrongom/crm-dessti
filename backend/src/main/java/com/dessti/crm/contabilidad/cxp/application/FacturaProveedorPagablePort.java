package com.dessti.crm.contabilidad.cxp.application;

import java.util.UUID;

/**
 * Puerto de <strong>salida</strong> que el submodulo Cuentas_Por_Pagar (CxP)
 * <em>declara</em> y el modulo de compras (facturas de proveedor)
 * <em>implementa</em>, para marcar una Factura_Proveedor como pagada cuando el saldo
 * de su CxP llega a 0 (Req 42.3, conforme al Req 33). Al declararse aqui e
 * implementarse en compras, la dependencia entre modulos permanece
 * <strong>aciclica a nivel de interfaz</strong>: {@code contabilidad.cxp} depende de
 * una interfaz que declara; {@code compras.factura} la implementa.
 *
 * <p>La transicion {@code conciliada -> pagada} de la Factura_Proveedor ya existe en
 * su maquina de estados (bloque 27); esta operacion la reutiliza.</p>
 */
public interface FacturaProveedorPagablePort {

    /**
     * Marca la Factura_Proveedor como pagada cuando su Cuenta_Por_Pagar se liquida
     * (saldo == 0, Req 42.3). La implementacion transita la factura de
     * {@code conciliada} a {@code pagada} (Req 33) y la persiste. Es idempotente
     * frente a facturas ya pagadas.
     *
     * @param facturaProveedorId Factura_Proveedor a marcar como pagada; obligatoria.
     */
    void marcarPagada(UUID facturaProveedorId);
}
