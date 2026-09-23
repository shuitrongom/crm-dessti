package com.dessti.crm.social.analitica.application;

import com.dessti.crm.social.analitica.domain.MetricasSociales;

/**
 * DTO de salida de las <strong>metricas sociales de un Canal_Social</strong>
 * (Req 66.1): la proyeccion de {@link MetricasSociales} a la frontera REST. Es una
 * agregacion de solo lectura que no modifica los datos de origen (Req 66.1).
 *
 * @param canal                           etiqueta del Canal_Social ({@code whatsapp},
 *                                        {@code messenger}, {@code instagram}).
 * @param alcance                         Conversaciones distintas del canal.
 * @param interacciones                   total de Mensajes_Social del canal.
 * @param mensajesRecibidos               Mensajes_Social entrantes.
 * @param mensajesEnviados                Mensajes_Social salientes.
 * @param tiempoRespuestaPromedioSegundos promedio del tiempo de primera respuesta (segundos).
 * @param conversiones                    Conversaciones con lead/conversion.
 */
public record MetricasSocialesDto(
        String canal,
        long alcance,
        long interacciones,
        long mensajesRecibidos,
        long mensajesEnviados,
        long tiempoRespuestaPromedioSegundos,
        long conversiones) {

    /**
     * Proyecta el valor de dominio {@link MetricasSociales} a su DTO de salida.
     *
     * @param metricas metricas de dominio de un canal; obligatorio.
     * @return el DTO de metricas del canal.
     */
    public static MetricasSocialesDto de(MetricasSociales metricas) {
        return new MetricasSocialesDto(
                metricas.canal().valorBd(),
                metricas.alcance(),
                metricas.interacciones(),
                metricas.mensajesRecibidos(),
                metricas.mensajesEnviados(),
                metricas.tiempoRespuestaPromedioSegundos(),
                metricas.conversiones());
    }
}
