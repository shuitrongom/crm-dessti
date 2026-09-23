package com.dessti.crm.reportesbi.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorComercialVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorComprasVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorCxpVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorEstrategiaVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorFinanzasVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorInstalacionVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorInventarioAvanzadoVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorInventarioVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorMantenimientoVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorPresupuestoVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorProduccionVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorRhNominaVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorSocialVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorTesoreriaVacio;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorComercialPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorComprasPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorCxpPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorEstrategiaPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorFinanzasPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorInstalacionPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorInventarioAvanzadoPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorInventarioPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorMantenimientoPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorPresupuestoPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorProduccionPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorRhNominaPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorSocialPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorTesoreriaPort;

/**
 * Configuracion del modulo reportes-bi (Req 22, 48).
 *
 * <p>Registra un adaptador <strong>por defecto</strong> por cada
 * {@code IndicadorAreaPort} de las areas del Req 22.1, <strong>solo si no existe otra
 * implementacion</strong> en el contexto ({@code @ConditionalOnMissingBean}). Asi el
 * Tablero (Req 22) y el analisis consolidado (Req 48) se componen y renderizan de
 * extremo a extremo desde ya —con las areas presentes pero en cero mientras no haya
 * datos ni adaptador concreto—, y cuando un modulo de area aporte su adaptador real de
 * solo lectura, el placeholder correspondiente se desactiva automaticamente. Sigue el
 * mismo patron que {@code InventarioConfig} para {@code NotificadorStockPort}.</p>
 *
 * <p><strong>Independencia (bloque 44):</strong> ninguno de estos beans importa ni
 * referencia los servicios/entidades de otros modulos (en particular NO toca
 * {@code com.dessti.crm.social}); todos devuelven indicadores vacios. El grafo de
 * dependencias del agregador queda limpio y el modulo compila de forma aislada.</p>
 */
@Configuration
public class ReportesBiConfig {

    /**
     * Indicadores comerciales por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area comercial.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorComercialPort.class)
    public IndicadorComercialPort indicadorComercialPorDefecto() {
        return new IndicadorComercialVacio();
    }

    /**
     * Indicadores de produccion por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area produccion.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorProduccionPort.class)
    public IndicadorProduccionPort indicadorProduccionPorDefecto() {
        return new IndicadorProduccionVacio();
    }

    /**
     * Indicadores de instalacion por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area instalacion.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorInstalacionPort.class)
    public IndicadorInstalacionPort indicadorInstalacionPorDefecto() {
        return new IndicadorInstalacionVacio();
    }

    /**
     * Indicadores de mantenimiento por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area mantenimiento.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorMantenimientoPort.class)
    public IndicadorMantenimientoPort indicadorMantenimientoPorDefecto() {
        return new IndicadorMantenimientoVacio();
    }

    /**
     * Indicadores de inventario por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area inventario.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorInventarioPort.class)
    public IndicadorInventarioPort indicadorInventarioPorDefecto() {
        return new IndicadorInventarioVacio();
    }

    /**
     * Indicadores de inventario avanzado por defecto (vacios) si no hay adaptador
     * concreto.
     *
     * @return el adaptador por defecto del area inventario avanzado.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorInventarioAvanzadoPort.class)
    public IndicadorInventarioAvanzadoPort indicadorInventarioAvanzadoPorDefecto() {
        return new IndicadorInventarioAvanzadoVacio();
    }

    /**
     * Indicadores de compras por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area compras.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorComprasPort.class)
    public IndicadorComprasPort indicadorComprasPorDefecto() {
        return new IndicadorComprasVacio();
    }

    /**
     * Indicadores de finanzas por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area finanzas.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorFinanzasPort.class)
    public IndicadorFinanzasPort indicadorFinanzasPorDefecto() {
        return new IndicadorFinanzasVacio();
    }

    /**
     * Indicadores de RH/nomina por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area RH/nomina.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorRhNominaPort.class)
    public IndicadorRhNominaPort indicadorRhNominaPorDefecto() {
        return new IndicadorRhNominaVacio();
    }

    /**
     * Indicadores de tesoreria por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area tesoreria.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorTesoreriaPort.class)
    public IndicadorTesoreriaPort indicadorTesoreriaPorDefecto() {
        return new IndicadorTesoreriaVacio();
    }

    /**
     * Indicadores de CxP por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area CxP.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorCxpPort.class)
    public IndicadorCxpPort indicadorCxpPorDefecto() {
        return new IndicadorCxpVacio();
    }

    /**
     * Indicadores de estrategia por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area estrategia.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorEstrategiaPort.class)
    public IndicadorEstrategiaPort indicadorEstrategiaPorDefecto() {
        return new IndicadorEstrategiaVacio();
    }

    /**
     * Indicadores de presupuesto por defecto (vacios) si no hay adaptador concreto.
     *
     * @return el adaptador por defecto del area presupuesto.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorPresupuestoPort.class)
    public IndicadorPresupuestoPort indicadorPresupuestoPorDefecto() {
        return new IndicadorPresupuestoVacio();
    }

    /**
     * Indicadores de redes sociales por defecto (vacios) si no hay adaptador concreto.
     * NO acopla con el modulo social (bloque 44, independencia).
     *
     * @return el adaptador por defecto del area redes sociales.
     */
    @Bean
    @ConditionalOnMissingBean(IndicadorSocialPort.class)
    public IndicadorSocialPort indicadorSocialPorDefecto() {
        return new IndicadorSocialVacio();
    }
}
