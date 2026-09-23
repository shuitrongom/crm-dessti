package com.dessti.crm.facturacion.application;

import java.util.UUID;

/**
 * Solicitud inmutable de cancelacion de un CFDI timbrado ante el PAC (Req 35.4).
 * Objeto de transporte del {@link PacPort}, libre de dependencias de dominio o
 * persistencia.
 *
 * @param folioFiscal Folio_Fiscal (UUID del SAT) del CFDI a cancelar; obligatorio (Req 35.4).
 * @param motivoSat   clave del motivo de cancelacion conforme al catalogo del SAT
 *                    (por ejemplo {@code 01}, {@code 02}, {@code 03}, {@code 04});
 *                    obligatorio (Req 35.4).
 */
public record SolicitudCancelacion(UUID folioFiscal, String motivoSat) {
}
