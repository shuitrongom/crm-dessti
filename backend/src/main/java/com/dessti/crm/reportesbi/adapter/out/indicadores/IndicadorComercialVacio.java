package com.dessti.crm.reportesbi.adapter.out.indicadores;

import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorComercialPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;

/**
 * Adaptador por defecto del {@link IndicadorComercialPort} que devuelve indicadores
 * vacios (Req 22.1, 22.2). Se registra en {@code ReportesBiConfig} solo si no existe
 * otra implementacion ({@code @ConditionalOnMissingBean}), de modo que el adaptador
 * concreto del modulo comercial lo reemplace automaticamente cuando exista. Es de solo
 * lectura y no accede a ningun dato de origen.
 */
public class IndicadorComercialVacio implements IndicadorComercialPort {

    @Override
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        return IndicadoresArea.vacio(AreaIndicador.COMERCIAL);
    }
}
