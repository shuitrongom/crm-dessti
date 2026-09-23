package com.dessti.crm.social.analitica.domain;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.DireccionMensaje;

/**
 * Proyeccion de <strong>solo lectura</strong> de una fila fuente de la analitica
 * social: un Mensaje_Social (entrante o saliente) de una Conversacion, con el
 * minimo de campos necesarios para agregar las metricas por Canal_Social y periodo
 * (Req 66.1). Es un record inmutable, sin dependencias de Spring ni de JPA, para
 * que la agregacion sea una funcion pura y directamente verificable (Property 40).
 *
 * <p><strong>Aislamiento por tenant (Req 66.6, 23):</strong> cada fila lleva su
 * {@code tenantId}, de modo que la funcion pura {@link CalculoMetricasSociales}
 * puede filtrar por Empresa y comprobar que ninguna fila de otro tenant contribuye
 * a las metricas del tenant objetivo.</p>
 *
 * <h2>Semantica de campos temporales</h2>
 * <ul>
 *   <li>En una fila <strong>entrante</strong> ({@link DireccionMensaje#ENTRANTE}),
 *       {@link #instante()} es {@code recibidoEn} (UTC).</li>
 *   <li>En una fila <strong>saliente</strong> ({@link DireccionMensaje#SALIENTE}),
 *       {@link #instante()} es {@code enviadoEn} (UTC).</li>
 * </ul>
 * <p>El tiempo de respuesta se deriva por Conversacion como el intervalo entre el
 * primer entrante y la primera respuesta saliente posterior a ese entrante (ver
 * {@link CalculoMetricasSociales}).</p>
 *
 * @param tenantId       Empresa (tenant) a la que pertenece la fila; obligatorio (Req 66.6).
 * @param canal          Canal_Social de la Conversacion; obligatorio (Req 66.1).
 * @param conversacionId Conversacion a la que pertenece la fila; obligatorio (base del alcance).
 * @param direccion      sentido del Mensaje_Social (entrante/saliente); obligatorio.
 * @param instante       marca temporal UTC del mensaje (recepcion si entrante, envio si saliente).
 * @param esLead         {@code true} si la Conversacion genero un lead/conversion (Req 66.1).
 */
public record FilaMetricaSocial(
        UUID tenantId,
        CanalSocial canal,
        UUID conversacionId,
        DireccionMensaje direccion,
        Instant instante,
        boolean esLead) {

    /**
     * Valida las invariantes minimas de la fila.
     *
     * @throws IllegalArgumentException si el tenant, el canal, la Conversacion o la
     *         direccion son nulos.
     */
    public FilaMetricaSocial {
        if (tenantId == null) {
            throw new IllegalArgumentException("La fila de analitica social debe indicar el tenant_id.");
        }
        if (canal == null) {
            throw new IllegalArgumentException("La fila de analitica social debe indicar el Canal_Social.");
        }
        if (conversacionId == null) {
            throw new IllegalArgumentException("La fila de analitica social debe indicar la Conversacion.");
        }
        if (direccion == null) {
            throw new IllegalArgumentException("La fila de analitica social debe indicar la direccion.");
        }
    }

    /**
     * Indica si la fila corresponde a un Mensaje_Social entrante.
     *
     * @return {@code true} si {@link #direccion()} es {@link DireccionMensaje#ENTRANTE}.
     */
    public boolean esEntrante() {
        return direccion == DireccionMensaje.ENTRANTE;
    }

    /**
     * Indica si la fila corresponde a un Mensaje_Social saliente.
     *
     * @return {@code true} si {@link #direccion()} es {@link DireccionMensaje#SALIENTE}.
     */
    public boolean esSaliente() {
        return direccion == DireccionMensaje.SALIENTE;
    }
}
