package com.dessti.crm.social.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.social.domain.CuentaCanalSocial;

/**
 * DTO de salida de una {@link CuentaCanalSocial} (Req 12.2, 64.1), distinto de la
 * entidad de persistencia. <strong>No expone las credenciales</strong>: solo su
 * referencia {@code credencialesRef} (Req 11). El controlador REST lo serializa;
 * nunca se expone la entidad JPA.
 *
 * @param id                   identificador de la Cuenta_Canal_Social.
 * @param canal                etiqueta del Canal_Social.
 * @param identificadorExterno identificador en el canal (numero WA / page id / ig id).
 * @param nombre               nombre descriptivo.
 * @param credencialesRef      referencia al secreto (NUNCA el valor, Req 11).
 * @param activa               {@code true} si la cuenta esta operativa.
 * @param version              version para concurrencia optimista (Req 49).
 * @param createdAt            instante de alta (UTC).
 * @param updatedAt            instante de la ultima modificacion (UTC).
 */
public record CuentaCanalSocialDto(
        UUID id,
        String canal,
        String identificadorExterno,
        String nombre,
        String credencialesRef,
        boolean activa,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link CuentaCanalSocial} a su DTO de salida.
     *
     * @param cuenta entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static CuentaCanalSocialDto de(CuentaCanalSocial cuenta) {
        return new CuentaCanalSocialDto(
                cuenta.getId(),
                cuenta.getCanal().valorBd(),
                cuenta.getIdentificadorExterno(),
                cuenta.getNombre(),
                cuenta.getCredencialesRef(),
                cuenta.isActiva(),
                cuenta.getVersion(),
                cuenta.getCreatedAt(),
                cuenta.getUpdatedAt());
    }
}
