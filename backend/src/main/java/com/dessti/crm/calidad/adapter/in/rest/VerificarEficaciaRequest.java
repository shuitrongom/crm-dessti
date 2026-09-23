package com.dessti.crm.calidad.adapter.in.rest;

/**
 * Cuerpo de la peticion para registrar la verificacion de la eficacia de una
 * Accion_Correctiva, requisito previo del cierre (Req 70.2; Property 43).
 *
 * @param evidencia evidencia de la verificacion; opcional.
 */
public record VerificarEficaciaRequest(String evidencia) {
}
