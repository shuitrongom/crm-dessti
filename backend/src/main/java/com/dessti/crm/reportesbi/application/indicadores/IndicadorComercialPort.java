package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>comercial</strong> (Req 22.1, 48.1). El
 * adaptador concreto (que aportara el modulo comercial-crm) agrega, de solo lectura, el
 * pipeline de Oportunidades y las Cotizaciones por estado del periodo/Cliente indicado
 * en el {@link FiltroIndicadores}. Mientras no exista, el adaptador por defecto devuelve
 * indicadores vacios (ver {@code ReportesBiConfig}).
 */
public interface IndicadorComercialPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.COMERCIAL;
    }
}
