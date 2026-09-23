package com.dessti.crm.compras.factura.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para registrar una Factura_Proveedor (Req 33.1, 33.2). DTO
 * de entrada del contrato REST, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.compras.factura.application.RegistrarFacturaProveedorCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4); el
 * Proveedor se deriva de la Orden_Compra.
 *
 * <p>La validacion de campo (Bean Validation -&gt; 400) cubre presencia y rango; la
 * existencia de la Orden_Compra (404) la verifica la capa de aplicacion.</p>
 *
 * @param ordenCompraId  Orden_Compra existente asociada; obligatorio (Req 33.1).
 * @param folioProveedor folio de factura del Proveedor; obligatorio, 1..100
 *                       (Req 33.2).
 * @param monto          monto de la factura; obligatorio, en [0, 9,999,999,999.99],
 *                       escala 2 (Req 33.2).
 */
public record RegistrarFacturaProveedorRequest(
        @NotNull UUID ordenCompraId,
        @NotBlank @Size(max = 100) String folioProveedor,
        @NotNull
        @DecimalMin(value = "0.00")
        @DecimalMax(value = "9999999999.99")
        @Digits(integer = 10, fraction = 2)
        BigDecimal monto) {
}
