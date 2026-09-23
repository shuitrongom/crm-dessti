package com.dessti.crm.platform.audit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades configurables del servicio de auditoria (Req 10).
 *
 * <p>Expone la <strong>retencion configurable</strong> de la bitacora
 * (Req 10.8). En esta tarea (7.1) solo se declara la propiedad; la purga
 * efectiva de registros mas antiguos que el periodo de retencion se implementa
 * en una tarea posterior (debe respetar la inmutabilidad: la purga la ejecuta
 * un proceso administrativo con privilegios especificos, no el rol de
 * aplicacion, dado el trigger append-only y los permisos restringidos).</p>
 *
 * @param retencion periodo de retencion de la bitacora (por ejemplo
 *                  {@code P3650D} para 10 anios). Por defecto 10 anios, un valor
 *                  conservador acorde con la retencion fiscal en Mexico; ajustable
 *                  por configuracion externa.
 */
@ConfigurationProperties(prefix = "crm.auditoria")
public record AuditoriaProperties(Duration retencion) {

    /** Retencion por defecto (10 anios) si no se configura externamente. */
    private static final Duration RETENCION_POR_DEFECTO = Duration.ofDays(3650);

    /** Aplica el valor por defecto cuando no se provee retencion. */
    public AuditoriaProperties {
        if (retencion == null) {
            retencion = RETENCION_POR_DEFECTO;
        }
    }
}
