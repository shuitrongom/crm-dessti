package com.dessti.crm.social.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Guarda <strong>pura</strong> de la Ventana_Servicio (Req 64.6, 64.7; Property 37).
 *
 * <p>La Ventana_Servicio es el intervalo de 24 horas (configurable) contado desde
 * el ultimo Mensaje_Social <em>entrante</em> del Cliente durante el cual la Empresa
 * puede responder con <strong>texto libre</strong>. Fuera de ese intervalo se
 * requiere una {@link PlantillaMensaje} aprobada (o un mensaje etiquetado) y se
 * rechaza el texto libre (Req 64.7).</p>
 *
 * <h2>Funcion pura (Property 37)</h2>
 * <p>Todas las operaciones son funciones puras {@code (ultimoEntranteUtc, ahora,
 * ventanaHoras) -> boolean}: deterministas, sin efectos secundarios y sin
 * dependencias de framework, lo que las hace trivialmente verificables por pruebas
 * de propiedad. La guarda no consulta la base de datos ni el reloj: recibe
 * {@code ahora} explicitamente.</p>
 *
 * <h2>Regla de borde: sin entrantes</h2>
 * <p>Cuando {@code ultimoEntranteUtc} es {@code null} (la Conversacion aun no ha
 * recibido ningun entrante; por ejemplo, un contacto iniciado por la Empresa) se
 * considera <strong>fuera</strong> de la ventana, por lo que se exige una
 * Plantilla_Mensaje (coherente con las reglas de Meta para iniciar conversacion).</p>
 *
 * <h2>Borde de igualdad</h2>
 * <p>Se considera dentro de la ventana mientras el tiempo transcurrido desde el
 * ultimo entrante sea <em>menor o igual</em> a la duracion de la ventana
 * ({@code ahora - ultimoEntranteUtc <= ventana}). Un {@code ahora} anterior al
 * ultimo entrante (transcurrido negativo) tambien se considera dentro (no expira
 * "hacia atras").</p>
 */
public final class GuardaVentanaServicio {

    /** Duracion por defecto de la Ventana_Servicio: 24 horas (Req 64.6). */
    public static final int VENTANA_HORAS_DEFECTO = 24;

    private GuardaVentanaServicio() {
        // Utilidad estatica pura: no instanciable.
    }

    /**
     * Funcion pura: indica si {@code ahora} esta dentro de la Ventana_Servicio
     * medida desde {@code ultimoEntranteUtc} con una amplitud de
     * {@code ventanaHoras} horas (Req 64.6; Property 37).
     *
     * @param ultimoEntranteUtc instante del ultimo Mensaje_Social entrante (UTC);
     *                          {@code null} si no hay entrantes (fuera de la ventana).
     * @param ahora             instante de referencia (UTC); obligatorio.
     * @param ventanaHoras      amplitud de la ventana en horas; debe ser &gt; 0.
     * @return {@code true} si {@code ahora - ultimoEntranteUtc <= ventanaHoras} (y
     *         {@code ultimoEntranteUtc} no es {@code null}).
     * @throws IllegalArgumentException si {@code ahora} es {@code null} o
     *         {@code ventanaHoras <= 0}.
     */
    public static boolean dentroDeVentana(Instant ultimoEntranteUtc, Instant ahora, int ventanaHoras) {
        if (ahora == null) {
            throw new IllegalArgumentException("El instante 'ahora' es obligatorio");
        }
        if (ventanaHoras <= 0) {
            throw new IllegalArgumentException("La ventana de servicio en horas debe ser mayor que cero");
        }
        if (ultimoEntranteUtc == null) {
            // Sin ningun entrante previo: fuera de la ventana (se exige plantilla).
            return false;
        }
        Duration transcurrido = Duration.between(ultimoEntranteUtc, ahora);
        // Un 'ahora' anterior al entrante (transcurrido negativo) sigue dentro.
        if (transcurrido.isNegative()) {
            return true;
        }
        return transcurrido.compareTo(Duration.ofHours(ventanaHoras)) <= 0;
    }

    /**
     * Funcion pura: indica si el envio de un Mensaje_Social del tipo indicado esta
     * permitido segun la Ventana_Servicio (Req 64.6, 64.7; Property 37).
     *
     * <ul>
     *   <li>Un mensaje de tipo {@link TipoMensaje#PLANTILLA} o
     *       {@link TipoMensaje#INTERACTIVO} siempre esta permitido (no es texto
     *       libre): dentro de la ventana como respuesta normal y fuera de ella como
     *       la unica via admitida (Req 64.7).</li>
     *   <li>Un mensaje de tipo {@link TipoMensaje#TEXTO} (texto libre) solo esta
     *       permitido <strong>dentro</strong> de la ventana (Req 64.6); fuera de
     *       ella se rechaza y se exige una Plantilla_Mensaje (Req 64.7).</li>
     * </ul>
     *
     * @param tipo              tipo del Mensaje_Social a enviar; obligatorio.
     * @param ultimoEntranteUtc instante del ultimo entrante (UTC); {@code null} si
     *                          no hay entrantes.
     * @param ahora             instante de referencia (UTC); obligatorio.
     * @param ventanaHoras      amplitud de la ventana en horas; &gt; 0.
     * @return {@code true} si el envio esta permitido por la Ventana_Servicio.
     * @throws IllegalArgumentException si {@code tipo}/{@code ahora} son nulos o
     *         {@code ventanaHoras <= 0}.
     */
    public static boolean permiteEnvio(TipoMensaje tipo, Instant ultimoEntranteUtc,
                                       Instant ahora, int ventanaHoras) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo del Mensaje_Social es obligatorio");
        }
        if (!tipo.esTextoLibre()) {
            // Plantilla o interactivo: permitido dentro y fuera de la ventana.
            return true;
        }
        // Texto libre: solo dentro de la ventana (Req 64.6/64.7).
        return dentroDeVentana(ultimoEntranteUtc, ahora, ventanaHoras);
    }
}
