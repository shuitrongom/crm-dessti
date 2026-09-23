package com.dessti.crm.social.adapter.out.lead;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dessti.crm.social.application.CaptacionLeadPort;

/**
 * Adaptador por defecto de {@link CaptacionLeadPort} (Req 64.4): implementacion
 * <strong>desacoplada</strong> que genera un identificador de Contacto sin crear la
 * entidad comercial, para que el modulo {@code social} compile y opere de forma
 * independiente del modulo {@code comercial.cliente} (ver la costura documentada en
 * {@link CaptacionLeadPort}).
 *
 * <h2>Sustituibilidad</h2>
 * <p>Se registra como bean {@link CaptacionLeadPort} mediante el metodo
 * {@code @Bean} {@code @ConditionalOnMissingBean} de {@link CaptacionLeadConfig}:
 * cuando el modulo comercial aporte un adaptador real que cree el
 * Contacto/Oportunidad efectivos, este adaptador por defecto deja de registrarse sin
 * tocar el modulo social.</p>
 *
 * <p>No escribe datos sensibles en logs (Req 11): solo el canal y un resumen no
 * identificatorio del remitente.</p>
 */
public class CaptacionLeadStubAdapter implements CaptacionLeadPort {

    private static final Logger log = LoggerFactory.getLogger(CaptacionLeadStubAdapter.class);

    @Override
    public ResultadoCaptacionLead capturar(SolicitudCaptacionLead solicitud) {
        if (solicitud == null) {
            return new ResultadoCaptacionLead(null, null);
        }
        // Genera un id de Contacto para vincular la Conversacion; el adaptador real
        // (modulo comercial) creara la entidad efectiva. Sin Oportunidad por defecto.
        UUID contactoId = UUID.randomUUID();
        log.debug("Captura de lead (stub) canal={} contacto={}", solicitud.canal(), contactoId);
        return new ResultadoCaptacionLead(contactoId, null);
    }
}
