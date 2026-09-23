package com.dessti.crm.platform.giros.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Implementacion <em>placeholder</em> de {@link ConteoEmpresasPorGiroPort} que
 * devuelve siempre {@code 0}: mientras no exista la relacion
 * {@code empresa.giro_id}, ningun Giro se considera en uso (Req 1.5).
 *
 * <p><strong>TODO (tarea 4.4/4.5 - Empresa ligada a un Giro):</strong> sustituir
 * esta implementacion por un adaptador real en {@code platform.empresas},
 * respaldado por {@code EmpresaRepository.countByGiroId(...)} una vez la
 * migracion V51 (tarea 4.1) haya creado la columna {@code empresa.giro_id} y el
 * modelo {@code Empresa} (tarea 4.2) la exponga. Ese adaptador contara las
 * Empresas reales del Giro, habilitando el rechazo 422 al desactivar un Giro en
 * uso.</p>
 *
 * <p>Se registra unicamente si ninguna otra implementacion de
 * {@link ConteoEmpresasPorGiroPort} esta presente en el contexto (mediante
 * {@link ConditionalOnMissingBean}), de modo que la tarea 4.x pueda aportar la
 * suya sin colisionar con este placeholder. Replica exactamente el patron de
 * {@code platform.security.rbac.PlanModulosPermisivoPorDefecto}.</p>
 */
@Configuration
public class ConteoEmpresasPorGiroPorDefecto {

    private static final Logger log = LoggerFactory.getLogger(ConteoEmpresasPorGiroPorDefecto.class);

    /**
     * Bean por defecto que informa que ningun Giro esta en uso mientras la tarea
     * 4.x no aporte la implementacion definitiva respaldada por
     * {@code empresa.giro_id}.
     *
     * @return un {@link ConteoEmpresasPorGiroPort} que siempre devuelve {@code 0}.
     */
    // El nombre del metodo @Bean NO debe coincidir con el nombre del bean de la
    // clase @Configuration (conteoEmpresasPorGiroPorDefecto, decapitalizado): esa
    // colision provoca BeanDefinitionOverrideException al arrancar el contexto. Se
    // usa un nombre distinto, igual que el patron hermano
    // PlanModulosPermisivoPorDefecto.planModulosPermisivo().
    @Bean
    @ConditionalOnMissingBean(ConteoEmpresasPorGiroPort.class)
    public ConteoEmpresasPorGiroPort conteoEmpresasPorGiroPlaceholder() {
        log.warn("Usando ConteoEmpresasPorGiroPort por defecto: ningun Giro se considera en uso (conteo=0). "
                + "Pendiente de la tarea 4.4/4.5 (Empresa ligada a un Giro) para el conteo real "
                + "respaldado por empresa.giro_id (Req 1.5).");
        return (UUID giroId) -> 0L;
    }
}
