package com.dessti.crm.reportesbi.adapter.out.indicadores;

import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorTesoreriaPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;

/**
 * Adaptador por defecto del {@link IndicadorTesoreriaPort} que devuelve indicadores
 * vacios (Req 22.1, 22.2). Se registra solo si no existe otra implementacion
 * ({@code @ConditionalOnMissingBean}). Solo lectura, sin acceso a datos de origen.
 */
public class IndicadorTesoreriaVacio implements IndicadorTesoreriaPort {

    @Override
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        return IndicadoresArea.vacio(AreaIndicador.TESORERIA);
    }
}
