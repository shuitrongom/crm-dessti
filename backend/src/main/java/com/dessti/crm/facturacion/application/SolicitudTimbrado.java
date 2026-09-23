package com.dessti.crm.facturacion.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Solicitud inmutable de Timbrado de un CFDI ante el PAC (Req 35.1). Es un objeto
 * de transporte del {@link PacPort}, libre de dependencias de dominio o
 * persistencia, para preservar la portabilidad del puerto.
 *
 * <p>Incluye lo minimo que un PAC necesita para timbrar una Factura de ingreso:
 * el tenant y la Factura de origen (trazabilidad/auditoria), los datos fiscales
 * del receptor (RFC, nombre, codigo postal, regimen fiscal y uso de CFDI) y los
 * importes ya calculados (subtotal, IVA, retenciones y total). El adaptador real
 * ensambla con ellos el XML del CFDI 4.0; el {@code PacStubAdapter} solo requiere
 * el {@code receptorRfc} para poder simular rechazos deterministas en pruebas.</p>
 *
 * @param tenantId       Empresa emisora (para trazabilidad; nunca se toma de la peticion, Req 23.4).
 * @param facturaId      identificador de la Factura de origen (Req 34.1).
 * @param receptorRfc    RFC del receptor (Req 34.1).
 * @param receptorNombre nombre o razon social del receptor (Req 34.1).
 * @param receptorCp     codigo postal del domicilio fiscal del receptor (Req 34.1).
 * @param receptorRegimenFiscal clave del regimen fiscal del receptor (Req 34.1).
 * @param usoCfdi        clave del uso de CFDI (Req 34.1).
 * @param subtotal       subtotal de la Factura (escala 2, Req 34.2).
 * @param iva            IVA trasladado (16% del subtotal; escala 2, Req 34.2).
 * @param retenciones    retenciones aplicadas (escala 2, Req 34.2).
 * @param total          total = subtotal + IVA - retenciones (escala 2, Req 34.2).
 */
public record SolicitudTimbrado(
        UUID tenantId,
        UUID facturaId,
        String receptorRfc,
        String receptorNombre,
        String receptorCp,
        String receptorRegimenFiscal,
        String usoCfdi,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal retenciones,
        BigDecimal total) {
}
