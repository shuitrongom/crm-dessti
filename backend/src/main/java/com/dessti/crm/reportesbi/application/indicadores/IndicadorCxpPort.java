package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>cuentas por pagar (CxP)</strong> (Req 22.1,
 * 48.1). El adaptador concreto (modulo contabilidad-cxp) agrega de solo lectura las CxP
 * vencidas del periodo. Adaptador por defecto: indicadores vacios.
 */
public interface IndicadorCxpPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.CXP;
    }
}
