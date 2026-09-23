package com.dessti.crm.social.analitica.domain;

import com.dessti.crm.social.domain.CanalSocial;

/**
 * Resultado inmutable de la agregacion de <strong>solo lectura</strong> de la
 * analitica social para un {@link CanalSocial} y un periodo (Req 66.1). Cada
 * instancia resume las metricas de un unico canal:
 *
 * <ul>
 *   <li><strong>alcance:</strong> numero de Conversaciones distintas del canal
 *       (proxy de remitentes alcanzados).</li>
 *   <li><strong>interacciones:</strong> numero total de Mensajes_Social del canal
 *       (entrantes + salientes). Invariante:
 *       {@code interacciones == mensajesRecibidos + mensajesEnviados}.</li>
 *   <li><strong>mensajesRecibidos:</strong> Mensajes_Social entrantes.</li>
 *   <li><strong>mensajesEnviados:</strong> Mensajes_Social salientes.</li>
 *   <li><strong>tiempoRespuestaPromedioSegundos:</strong> promedio, en segundos,
 *       del tiempo hasta la primera respuesta saliente por Conversacion con
 *       respuesta; {@code 0} si ninguna Conversacion del canal tuvo respuesta.</li>
 *   <li><strong>conversiones:</strong> Conversaciones del canal marcadas como
 *       lead/conversion (Req 66.1).</li>
 * </ul>
 *
 * <p>Todos los conteos son no negativos. El record no expone mutadores: es un valor
 * de dominio puro, apto para la verificacion por propiedades (Property 40).</p>
 *
 * @param canal                           Canal_Social al que corresponden las metricas.
 * @param alcance                         Conversaciones distintas del canal (&gt;= 0).
 * @param interacciones                   total de Mensajes_Social del canal (&gt;= 0).
 * @param mensajesRecibidos               Mensajes_Social entrantes (&gt;= 0).
 * @param mensajesEnviados                Mensajes_Social salientes (&gt;= 0).
 * @param tiempoRespuestaPromedioSegundos promedio del tiempo de primera respuesta,
 *                                        en segundos (&gt;= 0).
 * @param conversiones                    Conversaciones con lead/conversion (&gt;= 0).
 */
public record MetricasSociales(
        CanalSocial canal,
        long alcance,
        long interacciones,
        long mensajesRecibidos,
        long mensajesEnviados,
        long tiempoRespuestaPromedioSegundos,
        long conversiones) {

    /**
     * Valida las invariantes de dominio: el canal es obligatorio y todos los
     * conteos son no negativos y coherentes ({@code interacciones ==
     * mensajesRecibidos + mensajesEnviados}).
     *
     * @throws IllegalArgumentException si el canal es nulo, algun conteo es negativo
     *         o la suma de recibidos y enviados no coincide con las interacciones.
     */
    public MetricasSociales {
        if (canal == null) {
            throw new IllegalArgumentException("Las metricas sociales deben indicar el Canal_Social.");
        }
        exigirNoNegativo(alcance, "alcance");
        exigirNoNegativo(interacciones, "interacciones");
        exigirNoNegativo(mensajesRecibidos, "mensajesRecibidos");
        exigirNoNegativo(mensajesEnviados, "mensajesEnviados");
        exigirNoNegativo(tiempoRespuestaPromedioSegundos, "tiempoRespuestaPromedioSegundos");
        exigirNoNegativo(conversiones, "conversiones");
        if (interacciones != mensajesRecibidos + mensajesEnviados) {
            throw new IllegalArgumentException(
                    "Las interacciones deben ser la suma de mensajes recibidos y enviados.");
        }
    }

    private static void exigirNoNegativo(long valor, String campo) {
        if (valor < 0) {
            throw new IllegalArgumentException("La metrica '" + campo + "' no puede ser negativa.");
        }
    }
}
