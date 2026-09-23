package com.dessti.crm.social.domain;

/**
 * Guarda <strong>pura</strong> de Opt_In para la mensajeria de marketing (Req 64.8,
 * 64.9, 46.7; Property 38).
 *
 * <p>Un Mensaje_Social <em>de marketing</em> solo puede enviarse a un Cliente o
 * Contacto que cuente con un Opt_In <strong>vigente</strong> para el Canal_Social
 * correspondiente; en ausencia de consentimiento vigente el envio se rechaza
 * (Req 64.8). Los mensajes que <em>no</em> son de marketing (por ejemplo, la
 * atencion/servicio dentro de la Ventana_Servicio) no estan sujetos a esta guarda.</p>
 *
 * <h2>Funcion pura (Property 38)</h2>
 * <p>La decision es una funcion pura {@code (esMarketing, tieneOptInVigente) ->
 * boolean}: determinista, sin efectos secundarios y sin dependencias de framework.
 * La resolucion de si existe un Opt_In vigente (consulta del ultimo
 * {@link ConsentimientoCanal} por canal y sujeto) la realiza la capa de aplicacion
 * y se pasa aqui como booleano.</p>
 */
public final class GuardaOptIn {

    private GuardaOptIn() {
        // Utilidad estatica pura: no instanciable.
    }

    /**
     * Funcion pura: indica si esta permitido enviar un Mensaje_Social segun la
     * guarda de Opt_In de marketing (Req 64.8; Property 38).
     *
     * <ul>
     *   <li>Si el mensaje <strong>no</strong> es de marketing, el envio esta
     *       permitido con independencia del consentimiento.</li>
     *   <li>Si el mensaje <strong>es</strong> de marketing, el envio esta permitido
     *       <em>si y solo si</em> existe un Opt_In vigente para el canal.</li>
     * </ul>
     *
     * @param esMarketing        {@code true} si el Mensaje_Social es de marketing.
     * @param tieneOptInVigente  {@code true} si el destinatario tiene un Opt_In
     *                           vigente para el Canal_Social.
     * @return {@code true} si el envio esta permitido por la guarda de Opt_In.
     */
    public static boolean puedeEnviarMarketing(boolean esMarketing, boolean tieneOptInVigente) {
        if (!esMarketing) {
            // Los mensajes de servicio/atencion no requieren Opt_In (Req 64.8).
            return true;
        }
        // Marketing: requiere Opt_In vigente (Req 64.8).
        return tieneOptInVigente;
    }
}
