package com.dessti.crm.platform.empresas;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del puerto de existencia de datos de vertical por Giro (Req 3.1,
 * 3.2).
 *
 * <p>Registra {@link DatosVerticalNingunoPorDefecto} como implementacion por
 * defecto de {@link DatosVerticalPort} <strong>solo si no existe otra
 * implementacion</strong> en el contexto ({@code @ConditionalOnMissingBean}),
 * mediante el patron fiable de metodo {@code @Bean} en una clase
 * {@code @Configuration} (como {@code ImportacionBancariaConfig}/
 * {@code ReportesBiConfig}). Asi {@code ServicioEmpresas.cambiarGiro} opera de
 * extremo a extremo desde ya y, cuando un Modulo_Vertical (bloque 8/12) aporte
 * su deteccion real de datos —o un adaptador agregador la componga—, este
 * placeholder se desactiva automaticamente y el cambio de Giro se vuelve
 * estricto (Req 3.2).</p>
 *
 * <p><strong>Sin colision de nombres de bean:</strong> el nombre del bean lo fija
 * el nombre del metodo {@code @Bean} ({@code datosVerticalPorDefecto}), distinto
 * del nombre de esta clase {@code @Configuration} ({@code DatosVerticalConfig},
 * bean {@code datosVerticalConfig}) y del nombre de la clase de implementacion
 * ({@code DatosVerticalNingunoPorDefecto}). No se anota la implementacion con
 * {@code @Component}, de modo que el unico bean {@link DatosVerticalPort} por
 * defecto es el que aqui se define.</p>
 */
@Configuration
public class DatosVerticalConfig {

    /**
     * Registra el placeholder de datos de vertical si no hay otra implementacion.
     *
     * @return el placeholder que declara que ninguna Empresa tiene datos de
     *         vertical (Req 3.1).
     */
    @Bean
    @ConditionalOnMissingBean(DatosVerticalPort.class)
    public DatosVerticalPort datosVerticalPorDefecto() {
        return new DatosVerticalNingunoPorDefecto();
    }
}
