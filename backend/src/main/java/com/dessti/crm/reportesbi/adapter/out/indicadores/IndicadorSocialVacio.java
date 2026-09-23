package com.dessti.crm.reportesbi.adapter.out.indicadores;

import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorSocialPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;

/**
 * Adaptador por defecto del {@link IndicadorSocialPort} que devuelve indicadores vacios
 * (Req 22.1, 22.2). Se registra solo si no existe otra implementacion
 * ({@code @ConditionalOnMissingBean}).
 *
 * <p><strong>Independencia (bloque 44):</strong> este placeholder NO importa ni
 * referencia el paquete {@code com.dessti.crm.social}; el adaptador concreto que lee
 * mensajes/campanas lo aportara ese modulo por separado, reemplazando este bean
 * automaticamente. Es de solo lectura y no accede a dato alguno.</p>
 */
public class IndicadorSocialVacio implements IndicadorSocialPort {

    @Override
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        return IndicadoresArea.vacio(AreaIndicador.REDES_SOCIALES);
    }
}
