package com.dessti.crm.contabilidad.cxc.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puerto de entrada del submodulo Cuentas_Por_Cobrar (CxC) que el modulo de
 * facturacion-cfdi invoca para <strong>cerrar la integracion con CxC</strong> que
 * el bloque 28 dejo apuntada (Req 36.1, 37.1). Es la frontera hexagonal que
 * desacopla facturacion de la implementacion concreta de CxC: facturacion depende
 * de esta interfaz (no de la clase de servicio ni de la persistencia de CxC).
 *
 * <h2>Contrato</h2>
 * <ul>
 *   <li>{@link #registrarPorFacturaTimbrada(UUID, UUID, BigDecimal)} — al timbrar
 *       una Factura, registra su Cuenta_Por_Cobrar por el saldo = total (Req 36.1).
 *       Es <strong>idempotente</strong>: si ya existe una CxC para esa Factura, no
 *       hace nada (evita duplicados ante reintentos o doble timbrado defensivo).</li>
 *   <li>{@link #disminuirPorNotaCredito(UUID, BigDecimal)} — al emitir una Nota de
 *       Credito sobre una Factura, disminuye el saldo de su CxC por el monto
 *       (Req 37.1). El tope contra el saldo ya lo aplica el submodulo de notas de
 *       credito; la CxC lo acota defensivamente a {@code [0, saldo]}.</li>
 * </ul>
 *
 * <p>La implementacion ({@code ServicioCuentasPorCobrar}) deriva el {@code tenant_id}
 * del contexto (nunca de la peticion, Req 23.4) y audita las operaciones.</p>
 */
public interface CuentaPorCobrarPort {

    /**
     * Registra la Cuenta_Por_Cobrar de una Factura recien timbrada con saldo igual
     * al total (Req 36.1). Idempotente: si ya existe una CxC para {@code facturaId}
     * en el tenant vigente, no crea otra.
     *
     * @param facturaId Factura timbrada de origen; obligatoria.
     * @param clienteId Cliente al que se le cobra; obligatorio.
     * @param total     total de la Factura (saldo inicial de la CxC); no negativo.
     */
    void registrarPorFacturaTimbrada(UUID facturaId, UUID clienteId, BigDecimal total);

    /**
     * Disminuye el saldo de la CxC de una Factura por el monto de una Nota de
     * Credito emitida (Req 37.1). Si no existe una CxC para {@code facturaId} en el
     * tenant vigente, la operacion no tiene efecto.
     *
     * @param facturaId Factura sobre la que se emitio la Nota de Credito; obligatoria.
     * @param monto     monto de la Nota de Credito a descontar; positivo.
     */
    void disminuirPorNotaCredito(UUID facturaId, BigDecimal monto);
}
