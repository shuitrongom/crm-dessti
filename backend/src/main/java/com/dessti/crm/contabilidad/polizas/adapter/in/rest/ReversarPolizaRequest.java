package com.dessti.crm.contabilidad.polizas.adapter.in.rest;

import java.time.LocalDate;

/**
 * Cuerpo (opcional) de la peticion para reversar una Poliza_Contable (Req 38.5). DTO
 * de entrada del contrato REST. Si {@code fecha} es {@code null}, el reverso toma la
 * fecha de la poliza original.
 *
 * @param fecha fecha contable del reverso; opcional.
 */
public record ReversarPolizaRequest(LocalDate fecha) {
}
