package com.dessti.crm.contabilidad.cxc.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida del submodulo Cuentas_Por_Cobrar para generar automaticamente la
 * <strong>Poliza_Contable de ingreso por cobro</strong> de un Pago_Cliente
 * registrado (Req 38.2), replicando el patron de
 * {@code activosfijos.application.PolizaDepreciacionPort}.
 *
 * <h2>Asiento contable (poliza balanceada, Property 16)</h2>
 * <ul>
 *   <li><strong>Cargo</strong> a Bancos por el monto cobrado.</li>
 *   <li><strong>Abono</strong> a Clientes (cuentas por cobrar) por el monto cobrado.</li>
 * </ul>
 *
 * <h2>Degradacion gracil</h2>
 * <p>La implementacion devuelve {@link Optional#empty()} —sin lanzar excepcion—
 * cuando el catalogo contable del tenant no define las cuentas estandar o el periodo
 * contable de la fecha esta cerrado, de modo que el registro del pago (y el timbrado
 * del complemento) nunca se revierte por un asunto contable interno. La capa de
 * aplicacion audita la omision.</p>
 */
public interface PolizaCobroPort {

    /**
     * Genera la Poliza_Contable de cobro de un Pago_Cliente (Req 38.2). No lanza
     * excepcion ante catalogo incompleto ni periodo cerrado: en esos casos devuelve
     * {@link Optional#empty()} (degradacion gracil).
     *
     * @param fecha   fecha contable de la poliza (fecha del pago); obligatoria.
     * @param pagoId  Pago_Cliente de origen; obligatorio.
     * @param monto   monto cobrado (cargo a bancos, abono a clientes); positivo.
     * @return el identificador de la poliza generada, o {@link Optional#empty()} si
     *         se omitio.
     */
    Optional<UUID> generarPolizaCobro(LocalDate fecha, UUID pagoId, BigDecimal monto);
}
