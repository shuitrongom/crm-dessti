package com.dessti.crm.platform.respaldo.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparador del respaldo <strong>periodico</strong> con frecuencia configurable
 * (Req 50.1). Ejecuta {@link ServicioRespaldo#ejecutarRespaldo()} segun la
 * expresion cron {@code crm.respaldo.cron} (por defecto diario a las 02:00).
 *
 * <p><strong>Seguridad en pruebas:</strong> el bean solo existe cuando
 * {@code crm.respaldo.habilitado=true} ({@link ConditionalOnProperty}). Por
 * defecto la capacidad esta desactivada, de modo que en pruebas y en un arranque
 * sin configurar NO se disparen respaldos ni se invoquen procesos externos.</p>
 *
 * <p>Errores: si el respaldo programado falla, {@link ServicioRespaldo} ya
 * registra la bitacora {@code FALLIDO} y audita; aqui se captura para que el
 * planificador no deje de reprogramar futuras ejecuciones.</p>
 */
@Component
@ConditionalOnProperty(name = "crm.respaldo.habilitado", havingValue = "true")
public class ProgramadorRespaldos {

    private static final Logger log = LoggerFactory.getLogger(ProgramadorRespaldos.class);

    private final ServicioRespaldo servicioRespaldo;

    public ProgramadorRespaldos(ServicioRespaldo servicioRespaldo) {
        this.servicioRespaldo = servicioRespaldo;
    }

    /**
     * Respaldo periodico programado (Req 50.1). La expresion cron se resuelve
     * desde {@code crm.respaldo.cron}.
     */
    @Scheduled(cron = "${crm.respaldo.cron}")
    public void respaldoProgramado() {
        log.info("Iniciando respaldo periodico programado (Req 50.1).");
        try {
            servicioRespaldo.ejecutarRespaldo();
        } catch (RuntimeException e) {
            // El fallo ya quedo registrado y auditado por el servicio; aqui solo
            // se evita propagarlo para no detener la reprogramacion.
            log.error("El respaldo periodico programado fallo (Req 50): {}", e.getMessage());
        }
    }
}
