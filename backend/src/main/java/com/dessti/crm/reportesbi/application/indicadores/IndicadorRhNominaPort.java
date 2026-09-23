package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>RH/nomina</strong> (Req 22.1, 48.1). El
 * adaptador concreto (modulo rh-nomina) agrega de solo lectura el costo de nomina del
 * periodo. Adaptador por defecto: indicadores vacios.
 */
public interface IndicadorRhNominaPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.RH_NOMINA;
    }
}
