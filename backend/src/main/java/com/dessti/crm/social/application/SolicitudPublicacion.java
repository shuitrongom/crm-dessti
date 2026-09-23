package com.dessti.crm.social.application;

import com.dessti.crm.social.domain.CanalSocial;

/**
 * Solicitud inmutable de publicacion de una Publicacion_Social hacia el proveedor a
 * traves del {@link PublicacionSocialPort} (Req 65.5). Es un record de frontera
 * hexagonal, sin tipos de dominio de persistencia ni de framework, para mantener el
 * puerto estable y portable (mismo patron que {@link SolicitudEnvioSocial}).
 *
 * <p>Las validaciones de negocio (contenido no vacio, fecha no pasada, estado
 * {@code programada}) las aplica la capa de aplicacion <strong>antes</strong> de
 * construir esta solicitud; el adaptador solo publica.</p>
 *
 * @param canal           Canal_Social por el que se publica; obligatorio.
 * @param credencialesRef referencia al secreto de la cuenta (NUNCA el valor,
 *                        Req 11); el adaptador resuelve el token desde el vault.
 * @param contenido       contenido de la publicacion; obligatorio.
 */
public record SolicitudPublicacion(
        CanalSocial canal,
        String credencialesRef,
        String contenido) {
}
