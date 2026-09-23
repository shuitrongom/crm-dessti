package com.dessti.crm.social.application;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida del resultado de registrar un consentimiento (Opt_In/Opt_Out)
 * (Req 64.9), distinto de la entidad de persistencia.
 *
 * @param id            identificador del registro de consentimiento.
 * @param canal         etiqueta del Canal_Social.
 * @param sujetoExterno identificador del sujeto en el canal (remitente).
 * @param estado        etiqueta del estado (opt_in/opt_out).
 * @param registradoEn  marca temporal UTC del registro (Req 64.9).
 */
public record ConsentimientoRegistradoDto(
        UUID id,
        String canal,
        String sujetoExterno,
        String estado,
        Instant registradoEn) {
}
