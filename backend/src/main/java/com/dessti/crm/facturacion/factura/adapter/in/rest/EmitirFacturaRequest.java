package com.dessti.crm.facturacion.factura.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para emitir una Factura (CFDI 4.0) (Req 34.1, 34.2). DTO
 * de entrada del contrato REST, distinto del comando de aplicacion
 * {@link com.dessti.crm.facturacion.factura.application.EmitirFacturaCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * <p>Exactamente uno de {@code cotizacionId} u {@code ordenFabricacionId} debe
 * indicarse como origen (Req 34.1); la capa de aplicacion lo valida (422). El
 * detalle de formato de RFC/CP/regimen/uso lo valida el dominio
 * ({@code DatosFiscalesReceptor}); aqui se exige unicamente su presencia.</p>
 *
 * @param cotizacionId         Cotizacion aprobada de origen; opcional.
 * @param ordenFabricacionId   Orden_Fabricacion de origen; opcional.
 * @param receptorRfc          RFC del receptor; obligatorio (Req 34.1).
 * @param receptorNombre       nombre o razon social del receptor; obligatorio (Req 34.1).
 * @param receptorCp           codigo postal del receptor; obligatorio (Req 34.1).
 * @param receptorRegimenFiscal clave del regimen fiscal del receptor; obligatoria (Req 34.1).
 * @param usoCfdi              clave del uso de CFDI; obligatoria (Req 34.1).
 * @param tasaRetencion        tasa de retencion en {@code [0, 1]}; opcional (Req 34.2).
 */
public record EmitirFacturaRequest(
        UUID cotizacionId,
        UUID ordenFabricacionId,
        @NotBlank String receptorRfc,
        @NotBlank String receptorNombre,
        @NotBlank String receptorCp,
        @NotBlank String receptorRegimenFiscal,
        @NotBlank String usoCfdi,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal tasaRetencion) {
}
