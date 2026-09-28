package com.dessti.crm.facturacion.factura.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida del modulo de facturacion-cfdi para generar automaticamente la
 * <strong>Poliza_Contable de ingreso</strong> de una Factura recien timbrada
 * (Req 38.2), replicando el patron establecido por
 * {@code activosfijos.application.PolizaDepreciacionPort} (poliza de depreciacion).
 *
 * <p>Es la frontera hexagonal que desacopla facturacion de la implementacion
 * concreta de las Polizas_Contables: facturacion depende de esta interfaz (no del
 * submodulo de polizas ni del catalogo contable). La implementacion vive en
 * {@code facturacion/factura/adapter/out/contabilidad} y delega en el
 * {@code PolizaContablePort} del submodulo de Polizas.</p>
 *
 * <h2>Asiento contable (poliza balanceada, Property 16)</h2>
 * <p>El timbrado de una Factura de venta produce una poliza de tipo {@code ingreso}
 * con el asiento estandar:</p>
 * <ul>
 *   <li><strong>Cargo</strong> a Clientes (cuentas por cobrar) por el total.</li>
 *   <li><strong>Abono</strong> a Ingresos por ventas por el subtotal.</li>
 *   <li><strong>Abono</strong> a IVA trasladado por el IVA (si es positivo).</li>
 * </ul>
 * <p>Cuando hay retenciones, se ajusta el asiento para conservar el balance (cargo
 * a la cuenta de retenciones por el monto retenido).</p>
 *
 * <h2>Degradacion gracil (decision de diseno)</h2>
 * <p>La generacion de la poliza <strong>nunca debe revertir un CFDI ya timbrado ante
 * el SAT</strong>. Por ello la implementacion devuelve {@link Optional#empty()} —sin
 * lanzar excepcion— cuando el catalogo contable del tenant no define las cuentas
 * estandar, o cuando el periodo contable de la fecha esta cerrado. En esos casos la
 * Factura queda timbrada y su CxC registrada; solo se omite la poliza (la capa de
 * aplicacion lo audita). Cuando el tenant complete el catalogo y el periodo este
 * abierto, la generacion queda operativa sin cambios de codigo.</p>
 */
public interface PolizaVentaPort {

    /**
     * Genera la Poliza_Contable de ingreso de una Factura timbrada (Req 38.2). No
     * lanza excepcion ante catalogo contable incompleto ni periodo cerrado: en esos
     * casos devuelve {@link Optional#empty()} (degradacion gracil) para no revertir
     * el CFDI ya timbrado.
     *
     * @param fecha       fecha contable de la poliza (fecha de timbrado); obligatoria.
     * @param facturaId   Factura de origen; obligatoria.
     * @param subtotal    subtotal de la Factura (base del ingreso); no negativo.
     * @param iva         IVA trasladado de la Factura; no negativo.
     * @param retenciones retenciones de la Factura; no negativo.
     * @param total       total de la Factura (cargo a clientes); positivo.
     * @return el identificador de la poliza generada, o {@link Optional#empty()} si
     *         se omitio por catalogo incompleto o periodo cerrado.
     */
    Optional<UUID> generarPolizaVenta(LocalDate fecha, UUID facturaId, BigDecimal subtotal,
                                      BigDecimal iva, BigDecimal retenciones, BigDecimal total);
}
