package com.dessti.crm.platform.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementacion minima (placeholder) del {@link NotificadorAlertasPort} que se
 * limita a registrar la alerta disparada en el log de la aplicacion (Req 10.11).
 *
 * <p>Existe para que la deteccion de patrones funcione de extremo a extremo sin
 * acoplar el modulo de auditoria a un proveedor de notificaciones concreto. Se
 * registra como bean por defecto en {@link AuditoriaConfig} solo si
 * <strong>no hay otra implementacion</strong> del puerto
 * ({@code @ConditionalOnMissingBean}), de modo que cuando la Tarea 43 aporte el
 * adaptador real de notificaciones (correo/WhatsApp), este placeholder se
 * desactive automaticamente.</p>
 *
 * <p>No registra secretos: {@link AlertaDisparada} solo transporta metadatos del
 * patron.</p>
 *
 * <p><strong>TODO (Tarea 43):</strong> sustituir por la integracion real con el
 * {@code NotificacionPort} del modulo de notificaciones.</p>
 */
public class NotificadorAlertasRegistroLog implements NotificadorAlertasPort {

    private static final Logger log = LoggerFactory.getLogger(NotificadorAlertasRegistroLog.class);

    @Override
    public void notificar(AlertaDisparada alerta) {
        // Placeholder: la entrega real la implementa la Tarea 43 (notificaciones).
        log.warn("[ALERTA_AUDITORIA] patron={} tenant={} conteo={} umbral={} destinatarios={} detectadaUtc={}"
                        + " (notificacion placeholder; integrar con Tarea 43)",
                alerta.patron(),
                alerta.tenantId(),
                alerta.conteoObservado(),
                alerta.umbral(),
                alerta.destinatarios(),
                alerta.detectadaUtc());
    }
}
