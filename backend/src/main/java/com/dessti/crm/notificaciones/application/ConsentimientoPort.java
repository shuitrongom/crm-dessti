package com.dessti.crm.notificaciones.application;

import com.dessti.crm.notificaciones.domain.CanalNotificacion;

/**
 * Puerto de <strong>salida</strong> para consultar si un destinatario tiene un
 * <em>Opt_In vigente</em> para recibir Notificaciones de marketing por un
 * Canal_Social (Req 46.7). Frontera hexagonal que desacopla la decision de consentimiento
 * de su fuente concreta (el modulo social u otro registro de consentimientos).
 *
 * <h2>Independencia de modulos</h2>
 * <p>Este puerto pertenece exclusivamente al modulo notificaciones y NO referencia
 * ninguna clase del modulo social. El adaptador por defecto
 * {@link ConsentimientoRegistroLog} aplica una politica <strong>segura por
 * defecto</strong>: sin una fuente de consentimiento real, considera que NO hay
 * Opt_In vigente para marketing social, de modo que tales Notificaciones se
 * <em>omiten</em> (Req 46.7) en lugar de enviarse indebidamente. El modulo social
 * podra registrar un bean que consulte el Opt_In real, desactivando el adaptador por
 * defecto ({@code @ConditionalOnMissingBean}).</p>
 */
public interface ConsentimientoPort {

    /**
     * Indica si el destinatario tiene un Opt_In vigente para recibir Notificaciones
     * de marketing por el Canal_Social indicado (Req 46.7).
     *
     * @param canal        Canal_Social a evaluar (WhatsApp/Messenger/Instagram).
     * @param destinatario identificador del destinatario en ese canal.
     * @return {@code true} si existe Opt_In vigente; {@code false} en caso contrario.
     */
    boolean tieneOptInVigente(CanalNotificacion canal, String destinatario);
}
