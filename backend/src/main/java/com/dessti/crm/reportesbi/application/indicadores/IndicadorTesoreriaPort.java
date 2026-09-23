package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>tesoreria</strong> (Req 22.1, 48.1). El
 * adaptador concreto (modulo tesoreria) agrega de solo lectura los saldos bancarios y
 * las partidas en conciliacion pendientes. Adaptador por defecto: indicadores vacios.
 */
public interface IndicadorTesoreriaPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.TESORERIA;
    }
}
