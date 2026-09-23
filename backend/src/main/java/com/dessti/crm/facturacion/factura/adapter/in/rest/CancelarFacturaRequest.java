package com.dessti.crm.facturacion.factura.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para cancelar una Factura timbrada (Req 35.4). DTO de
 * entrada del contrato REST. El motivo debe ser una clave del catalogo de motivos
 * de cancelacion del SAT (por ejemplo {@code 01}, {@code 02}, {@code 03},
 * {@code 04}); su presencia se exige aqui y su uso lo audita la aplicacion.
 *
 * @param motivoSat clave del motivo de cancelacion del SAT; obligatoria (Req 35.4).
 */
public record CancelarFacturaRequest(@NotBlank @Size(max = 4) String motivoSat) {
}
