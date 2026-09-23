package com.dessti.crm.social.application;

import com.dessti.crm.social.domain.CanalSocial;

/**
 * Puerto de salida de <strong>captura de leads</strong> (Req 64.4): cuando un
 * Mensaje_Social entrante proviene de un remitente que NO coincide con ningun
 * Cliente/Contacto existente, el modulo {@code social} solicita crear/enlazar un
 * Contacto (y, opcionalmente, una Oportunidad) a partir del remitente, integrando
 * la captura de prospectos con el pipeline comercial (Req 14).
 *
 * <h2>Costura de desacoplamiento (independencia de modulos)</h2>
 * <p>El modulo {@code social} <strong>no</strong> importa los servicios del modulo
 * {@code comercial.cliente} para evitar acoplamiento y colisiones de construccion
 * en paralelo. En su lugar define este puerto, cuya implementacion por defecto
 * ({@code CaptacionLeadStubAdapter}, registrada con
 * {@code @ConditionalOnMissingBean}) genera un identificador de Contacto sin crear
 * la entidad comercial. El modulo comercial podra aportar mas adelante un adaptador
 * real que cree el Contacto/Oportunidad efectivos, sin tocar el modulo social.</p>
 */
public interface CaptacionLeadPort {

    /**
     * Crea o enlaza un lead (Contacto y, opcionalmente, Oportunidad) a partir de un
     * remitente entrante sin coincidencia en el CRM (Req 64.4, 14).
     *
     * @param solicitud datos del remitente entrante; obligatorio.
     * @return el resultado con los identificadores del Contacto (y Oportunidad) para
     *         vincular la Conversacion.
     */
    ResultadoCaptacionLead capturar(SolicitudCaptacionLead solicitud);

    /**
     * Solicitud inmutable de captura de lead a partir de un remitente entrante
     * (Req 64.4).
     *
     * @param canal            Canal_Social por el que llego el remitente; obligatorio.
     * @param remitenteExterno identificador del remitente en el canal; obligatorio.
     * @param nombreMostrado   nombre visible del remitente si el evento lo trae;
     *                         opcional ({@code null}).
     */
    record SolicitudCaptacionLead(CanalSocial canal, String remitenteExterno, String nombreMostrado) {
    }

    /**
     * Resultado inmutable de la captura de lead (Req 64.4).
     *
     * @param contactoId   identificador del Contacto creado o enlazado; puede ser
     *                     {@code null} si el adaptador no genero ninguno.
     * @param oportunidadId identificador de la Oportunidad creada; opcional
     *                     ({@code null}).
     */
    record ResultadoCaptacionLead(java.util.UUID contactoId, java.util.UUID oportunidadId) {
    }
}
