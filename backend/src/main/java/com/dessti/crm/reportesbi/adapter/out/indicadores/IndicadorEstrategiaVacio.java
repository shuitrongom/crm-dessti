package com.dessti.crm.reportesbi.adapter.out.indicadores;

import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorEstrategiaPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;

/**
 * Adaptador por defecto del {@link IndicadorEstrategiaPort} que devuelve indicadores
 * vacios (Req 22.1, 22.2). Se registra solo si no existe otra implementacion
 * ({@code @ConditionalOnMissingBean}). Solo lectura, sin acceso a datos de origen.
 */
public class IndicadorEstrategiaVacio implements IndicadorEstrategiaPort {

    @Override
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        return IndicadoresArea.vacio(AreaIndicador.ESTRATEGIA);
    }
}
