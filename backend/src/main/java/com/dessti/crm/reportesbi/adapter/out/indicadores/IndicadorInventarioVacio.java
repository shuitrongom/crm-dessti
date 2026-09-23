package com.dessti.crm.reportesbi.adapter.out.indicadores;

import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorInventarioPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;

/**
 * Adaptador por defecto del {@link IndicadorInventarioPort} que devuelve indicadores
 * vacios (Req 22.1, 22.2). Se registra solo si no existe otra implementacion
 * ({@code @ConditionalOnMissingBean}). Solo lectura, sin acceso a datos de origen.
 */
public class IndicadorInventarioVacio implements IndicadorInventarioPort {

    @Override
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        return IndicadoresArea.vacio(AreaIndicador.INVENTARIO);
    }
}
