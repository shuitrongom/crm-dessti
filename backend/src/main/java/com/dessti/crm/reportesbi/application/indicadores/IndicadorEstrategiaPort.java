package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>estrategia</strong> (Req 22.1, 48.1). El
 * adaptador concreto (modulo estrategia) agrega de solo lectura el avance de los
 * Objetivos estrategicos. Adaptador por defecto: indicadores vacios.
 */
public interface IndicadorEstrategiaPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.ESTRATEGIA;
    }
}
