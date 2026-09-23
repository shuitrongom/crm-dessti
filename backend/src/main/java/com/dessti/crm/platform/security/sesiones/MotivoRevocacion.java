package com.dessti.crm.platform.security.sesiones;

/**
 * Motivo por el cual un Token_Refresco (Sesion) fue revocado (Req 68).
 *
 * <p>Se materializa en la columna {@code motivo_revocacion} de la tabla
 * {@code sesion_refresco} (migracion {@code V6}). Distingue los cuatro
 * disparadores de revocacion previstos por el Req 68:</p>
 * <ul>
 *   <li>{@link #LOGOUT}: el propio Usuario cerro sesion (Req 68.1).</li>
 *   <li>{@link #ADMINISTRADOR}: un Administrador con Permiso revoco las
 *       sesiones de la cuenta (Req 68.2).</li>
 *   <li>{@link #DESACTIVACION}: la cuenta fue desactivada (Req 68.2, 4.2).</li>
 *   <li>{@link #CAMBIO_PASSWORD}: se detecto un evento de seguridad relevante,
 *       como el cambio de contrasena (Req 68.4).</li>
 * </ul>
 */
public enum MotivoRevocacion {

    /** Revocacion por cierre de sesion del propio Usuario (Req 68.1). */
    LOGOUT,

    /** Revocacion por un Administrador con Permiso (Req 68.2). */
    ADMINISTRADOR,

    /** Revocacion por desactivacion de la cuenta (Req 68.2, 4.2). */
    DESACTIVACION,

    /** Revocacion por cambio de contrasena u otro evento de seguridad (Req 68.4). */
    CAMBIO_PASSWORD
}
