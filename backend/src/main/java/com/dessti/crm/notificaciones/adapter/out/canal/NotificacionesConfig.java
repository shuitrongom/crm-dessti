package com.dessti.crm.notificaciones.adapter.out.canal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dessti.crm.notificaciones.application.ConsentimientoPort;
import com.dessti.crm.notificaciones.application.NotificadorCorreoPort;
import com.dessti.crm.notificaciones.application.NotificadorSocialPort;
import com.dessti.crm.notificaciones.application.NotificadorWhatsappPort;
import com.dessti.crm.notificaciones.application.ReintentosProperties;

/**
 * Configuracion del modulo notificaciones (Req 46, 11). Registra:
 *
 * <ul>
 *   <li>{@link ReintentosProperties} como bean de propiedades de la politica de
 *       reintentos configurable ({@code crm.notificaciones.reintentos.*}, Req 46.3),
 *       siguiendo el patron de {@code PacConfig}.</li>
 *   <li>Los adaptadores por defecto de los puertos de salida de canal
 *       ({@link NotificadorCorreoPort}, {@link NotificadorWhatsappPort},
 *       {@link NotificadorSocialPort}) y del {@link ConsentimientoPort}, cada uno con
 *       {@code @ConditionalOnMissingBean} para que el adaptador real (o el bean del
 *       modulo social) los reemplace automaticamente sin tocar la aplicacion
 *       (Req 46.2, 46.6, 46.7).</li>
 * </ul>
 *
 * <p><strong>Independencia de modulos:</strong> esta configuracion y sus adaptadores
 * por defecto NO referencian el modulo social; el {@link NotificadorSocialPort} es la
 * frontera que dicho modulo podra implementar mas adelante.</p>
 */
@Configuration
@EnableConfigurationProperties(ReintentosProperties.class)
public class NotificacionesConfig {

    /**
     * Adaptador de correo por defecto (registro en log) si no hay otro
     * {@link NotificadorCorreoPort}.
     *
     * @return el adaptador de correo por defecto.
     */
    @Bean
    @ConditionalOnMissingBean(NotificadorCorreoPort.class)
    public NotificadorCorreoPort notificadorCorreoPort() {
        return new NotificadorCorreoRegistroLog();
    }

    /**
     * Adaptador de WhatsApp por defecto (registro en log) si no hay otro
     * {@link NotificadorWhatsappPort}.
     *
     * @return el adaptador de WhatsApp por defecto.
     */
    @Bean
    @ConditionalOnMissingBean(NotificadorWhatsappPort.class)
    public NotificadorWhatsappPort notificadorWhatsappPort() {
        return new NotificadorWhatsappRegistroLog();
    }

    /**
     * Adaptador social por defecto (registro en log) si no hay otro
     * {@link NotificadorSocialPort}. El modulo social podra aportar el bean real
     * (Req 46.6).
     *
     * @return el adaptador social por defecto.
     */
    @Bean
    @ConditionalOnMissingBean(NotificadorSocialPort.class)
    public NotificadorSocialPort notificadorSocialPort() {
        return new NotificadorSocialRegistroLog();
    }

    /**
     * Adaptador de consentimiento por defecto (politica segura: sin Opt_In) si no
     * hay otro {@link ConsentimientoPort} (Req 46.7).
     *
     * @return el adaptador de consentimiento por defecto.
     */
    @Bean
    @ConditionalOnMissingBean(ConsentimientoPort.class)
    public ConsentimientoPort consentimientoPort() {
        return new ConsentimientoRegistroLog();
    }
}
