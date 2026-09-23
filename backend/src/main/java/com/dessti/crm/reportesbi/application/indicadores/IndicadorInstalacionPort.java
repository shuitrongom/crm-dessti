package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>instalacion</strong> (Req 22.1, 48.1). El
 * adaptador concreto (modulo operacion-instalacion) agrega de solo lectura el
 * cumplimiento de fechas programadas de las Ordenes de trabajo de instalacion.
 * Adaptador por defecto: indicadores vacios.
 */
public interface IndicadorInstalacionPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.INSTALACION;
    }
}
