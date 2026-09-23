package com.dessti.crm.platform.empresas.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo para fijar la moneda de facturacion (ISO 4217) de la renta de una Empresa. */
public record FijarMonedaFacturacionRequest(
        @NotBlank @Size(min = 3, max = 3) String monedaCodigo) {
}