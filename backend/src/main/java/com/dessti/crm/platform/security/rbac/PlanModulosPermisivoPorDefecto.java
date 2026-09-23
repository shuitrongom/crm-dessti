package com.dessti.crm.platform.security.rbac;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Implementacion <em>placeholder</em> de {@link PlanModulosPort} que habilita
 * todos los modulos por defecto.
 *
 * <p><strong>TODO (tarea 14.2 - Planes y Suscripciones):</strong> sustituir esta
 * implementacion por una respaldada por la Suscripcion vigente de la Empresa,
 * que consulte el conjunto {@code modulos_habilitados} del Plan y devuelva
 * {@code false} para los modulos no incluidos, derivando en 403 (Req 25.4).</p>
 *
 * <p>Se registra unicamente si ninguna otra implementacion de
 * {@link PlanModulosPort} esta presente en el contexto (mediante
 * {@link ConditionalOnMissingBean}), de modo que la tarea 14.2 pueda aportar la
 * suya sin colisionar con este placeholder.</p>
 */
@Configuration
public class PlanModulosPermisivoPorDefecto {

    private static final Logger log = LoggerFactory.getLogger(PlanModulosPermisivoPorDefecto.class);

    /**
     * Bean por defecto que concede acceso a cualquier modulo mientras la tarea
     * 14.2 no aporte la implementacion definitiva.
     *
     * @return un {@link PlanModulosPort} permisivo.
     */
    @Bean
    @ConditionalOnMissingBean(PlanModulosPort.class)
    public PlanModulosPort planModulosPermisivo() {
        log.warn("Usando PlanModulosPort permisivo por defecto: todos los modulos habilitados. "
                + "Pendiente de la tarea 14.2 (Planes y Suscripciones) para el gating real por Plan (Req 25.4).");
        return (UUID tenantId, String modulo) -> true;
    }
}
