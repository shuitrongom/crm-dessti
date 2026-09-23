package com.dessti.crm.presupuestos.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del modulo de Presupuestos (Req 62, Bloque 38).
 *
 * <p>Habilita el enlace tipado de {@link PresupuestosProperties} (umbral de desviacion
 * configurable, Req 62.3), siguiendo el mismo patron que el resto de la plataforma
 * (por ejemplo {@code ConciliacionConfig} y {@code AuditoriaConfig}). El
 * {@code ServicioPresupuestos} recibe las propiedades por inyeccion.</p>
 *
 * <p>Ademas registra un {@link RealEjercidoPort} <strong>por defecto</strong> que
 * devuelve importes reales en cero (placeholder documentado, Req 62.2/62.8) solo si
 * ningun otro bean del puerto esta presente ({@code @ConditionalOnMissingBean}). Asi
 * la consulta de variacion funciona de extremo a extremo desde ya, y cuando los
 * modulos de facturacion/compras/nomina aporten un adaptador de agregacion real, este
 * placeholder cede su lugar automaticamente sin tocar el motor de presupuesto.</p>
 */
@Configuration
@EnableConfigurationProperties(PresupuestosProperties.class)
public class PresupuestosConfig {

    /**
     * Registra el {@link RealEjercidoAdapterPorDefecto} como implementacion por
     * defecto del {@link RealEjercidoPort} solo si no existe otra implementacion en el
     * contexto ({@code @ConditionalOnMissingBean}).
     *
     * @return el puerto de real ejercido por defecto (importes en cero).
     */
    @Bean
    @ConditionalOnMissingBean(RealEjercidoPort.class)
    public RealEjercidoPort realEjercidoPorDefecto() {
        return new RealEjercidoAdapterPorDefecto();
    }
}
