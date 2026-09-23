package com.dessti.crm.platform.web;

/**
 * Detalle de un error de validacion asociado a un campo concreto de la
 * peticion. Se incluye en el arreglo {@code errors} de la respuesta Problem
 * Details ante fallos de validacion (Req 8.2).
 *
 * @param field   nombre del campo invalido
 * @param message mensaje de negocio en espanol que explica el error
 */
public record ErrorCampo(String field, String message) {
}
