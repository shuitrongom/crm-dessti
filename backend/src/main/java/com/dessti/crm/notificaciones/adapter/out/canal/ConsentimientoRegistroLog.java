package com.dessti.crm.notificaciones.adapter.out.canal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dessti.crm.notificaciones.application.ConsentimientoPort;
import com.dessti.crm.notificaciones.domain.CanalNotificacion;

/**
 * Adaptador por defecto del {@link ConsentimientoPort} con politica <strong>segura
 * por defecto</strong> (Req 46.7): sin una fuente de consentimiento real, considera
 * que NO existe Opt_In vigente para marketing en Canal_Social, de modo que tales
 * Notificaciones se <em>omitan</em> en lugar de enviarse indebidamente.
 *
 * <h2>Sustituibilidad e independencia de modulos</h2>
 * <p>Se registra con {@code @ConditionalOnMissingBean} en
 * {@code NotificacionesConfig}. El modulo social (u otro registro de
 * consentimientos) podra aportar un bean que consulte el Opt_In real, desactivando
 * este adaptador. No importa ninguna clase del modulo social.</p>
 */
public class ConsentimientoRegistroLog implements ConsentimientoPort {

    private static final Logger log = LoggerFactory.getLogger(ConsentimientoRegistroLog.class);

    @Override
    public boolean tieneOptInVigente(CanalNotificacion canal, String destinatario) {
        // Politica segura por defecto (Req 46.7): sin fuente real de Opt_In, se
        // asume ausencia de consentimiento para marketing social -> se omitira.
        log.debug("[CONSENTIMIENTO] sin fuente real de Opt_In; canal={} -> se asume sin Opt_In "
                        + "(politica segura por defecto)",
                canal.valorBd());
        return false;
    }
}
