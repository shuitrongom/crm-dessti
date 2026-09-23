package com.dessti.crm.tesoreria.adapter.out.importacion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dessti.crm.tesoreria.application.ImportacionBancariaPort;

/**
 * Configuracion de la importacion de estados de cuenta de tesoreria (Req 43.2).
 *
 * <p>Registra el {@link ImportacionBancariaStubAdapter} como implementacion por
 * defecto del {@link ImportacionBancariaPort} <strong>solo si no existe otra
 * implementacion</strong> en el contexto ({@code @ConditionalOnMissingBean}),
 * mediante el patron fiable de metodo {@code @Bean} en una clase
 * {@code @Configuration} (como {@code ReportesBiConfig}/{@code InventarioConfig}).
 * Asi el flujo de importacion y conciliacion opera de extremo a extremo desde ya, y
 * cuando exista el adaptador real de archivo CSV/OFX o de la API del banco, este
 * placeholder se desactiva automaticamente.</p>
 */
@Configuration
public class ImportacionBancariaConfig {

    /**
     * Registra el stub de importacion bancaria si no hay otra implementacion.
     *
     * @return el adaptador stub de importacion de estados de cuenta.
     */
    @Bean
    @ConditionalOnMissingBean(ImportacionBancariaPort.class)
    public ImportacionBancariaPort importacionBancariaPorDefecto() {
        return new ImportacionBancariaStubAdapter();
    }
}
