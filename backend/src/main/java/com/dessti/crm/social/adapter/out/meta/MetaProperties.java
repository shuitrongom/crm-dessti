package com.dessti.crm.social.adapter.out.meta;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de integracion con la mensajeria de <strong>Meta</strong> (WhatsApp
 * Business Cloud API y Graph API para Messenger/Instagram) y de la politica de
 * reintentos (Req 64.13, 11). Sigue el patron de {@code PacProperties}.
 *
 * <p><strong>Gestion de secretos (Req 11):</strong> {@link #appSecret} (usado para
 * validar la firma HMAC de los webhooks, X-Hub-Signature-256), {@link #verifyToken}
 * (handshake de verificacion del webhook) y {@link #tokenAcceso} (token de acceso
 * por defecto) provienen <em>fuera del codigo fuente</em> y se resuelven desde
 * variables de entorno / almacen externo ({@code META_APP_SECRET},
 * {@code META_VERIFY_TOKEN}, {@code META_ACCESS_TOKEN}). No se define ningun valor
 * por defecto sensible. Sus valores <strong>nunca</strong> se escriben en logs; por
 * ello {@link #toString()} los enmascara. El token de acceso concreto de cada
 * cuenta se resuelve por su {@code credencialesRef}; el stub no necesita
 * credenciales.</p>
 *
 * <h2>Ventana de servicio y reintentos</h2>
 * <ul>
 *   <li>{@link #ventanaServicioHoras}: amplitud de la Ventana_Servicio en horas
 *       (Req 64.6); por defecto 24.</li>
 *   <li>{@link #maxIntentos}: numero maximo de intentos de envio ante fallo
 *       (Req 64.13); por defecto 3 (1 inicial + 2 reintentos).</li>
 *   <li>{@link #esperaReintentoMillis}: espera base entre reintentos en
 *       milisegundos; por defecto 200.</li>
 * </ul>
 *
 * @param appSecret             secreto de la app de Meta para validar la firma del
 *                              webhook (origen: {@code META_APP_SECRET}, Req 11).
 * @param verifyToken           token de verificacion del webhook (handshake)
 *                              (origen: {@code META_VERIFY_TOKEN}, Req 11).
 * @param tokenAcceso           token de acceso por defecto (origen:
 *                              {@code META_ACCESS_TOKEN}, Req 11); opcional.
 * @param ventanaServicioHoras  amplitud de la Ventana_Servicio en horas (Req 64.6).
 * @param maxIntentos           numero maximo de intentos de envio (Req 64.13).
 * @param esperaReintentoMillis espera base entre reintentos en ms (Req 64.13).
 */
@ConfigurationProperties(prefix = "crm.social")
public record MetaProperties(
        String appSecret,
        String verifyToken,
        String tokenAcceso,
        Integer ventanaServicioHoras,
        Integer maxIntentos,
        Long esperaReintentoMillis) {

    /** Amplitud por defecto de la Ventana_Servicio: 24 horas (Req 64.6). */
    public static final int VENTANA_SERVICIO_HORAS_DEFECTO = 24;

    /** Numero de intentos por defecto (1 inicial + 2 reintentos) (Req 64.13). */
    public static final int MAX_INTENTOS_DEFECTO = 3;

    /** Espera base por defecto entre reintentos, en milisegundos. */
    public static final long ESPERA_REINTENTO_MILLIS_DEFECTO = 200L;

    /**
     * Amplitud efectiva de la Ventana_Servicio en horas, aplicando el valor por
     * defecto (24) cuando no se configura o es no positivo (Req 64.6).
     *
     * @return la amplitud de la ventana en horas (&gt; 0).
     */
    public int ventanaServicioHorasEfectiva() {
        return (ventanaServicioHoras == null || ventanaServicioHoras <= 0)
                ? VENTANA_SERVICIO_HORAS_DEFECTO
                : ventanaServicioHoras;
    }

    /**
     * Numero efectivo de intentos de envio, aplicando el valor por defecto (3)
     * cuando no se configura o es menor que 1 (Req 64.13).
     *
     * @return el numero maximo de intentos (&ge; 1).
     */
    public int maxIntentosEfectivo() {
        return (maxIntentos == null || maxIntentos < 1) ? MAX_INTENTOS_DEFECTO : maxIntentos;
    }

    /**
     * Espera efectiva entre reintentos en milisegundos, aplicando el valor por
     * defecto (200) cuando no se configura o es negativa.
     *
     * @return la espera base entre reintentos en ms (&ge; 0).
     */
    public long esperaReintentoMillisEfectiva() {
        return (esperaReintentoMillis == null || esperaReintentoMillis < 0)
                ? ESPERA_REINTENTO_MILLIS_DEFECTO
                : esperaReintentoMillis;
    }

    /**
     * Representacion segura que enmascara los secretos para evitar su filtracion en
     * logs (Req 11.3). Los parametros no sensibles se muestran para diagnostico.
     *
     * @return descripcion sin exponer secretos.
     */
    @Override
    public String toString() {
        return "MetaProperties{"
                + "appSecret=" + enmascarar(appSecret)
                + ", verifyToken=" + enmascarar(verifyToken)
                + ", tokenAcceso=" + enmascarar(tokenAcceso)
                + ", ventanaServicioHoras=" + ventanaServicioHorasEfectiva()
                + ", maxIntentos=" + maxIntentosEfectivo()
                + ", esperaReintentoMillis=" + esperaReintentoMillisEfectiva()
                + '}';
    }

    /**
     * Enmascara un valor sensible: nunca revela el contenido, solo si fue provisto.
     *
     * @param valor valor sensible (puede ser nulo o vacio).
     * @return {@code "<ausente>"} si no hay valor, {@code "****"} en caso contrario.
     */
    private static String enmascarar(String valor) {
        return (valor == null || valor.isBlank()) ? "<ausente>" : "****";
    }
}
