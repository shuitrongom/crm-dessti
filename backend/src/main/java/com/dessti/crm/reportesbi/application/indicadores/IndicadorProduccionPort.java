package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>produccion</strong> (Req 22.1, 48.1). El
 * adaptador concreto (modulo operacion-produccion) agrega de solo lectura las Ordenes
 * de Fabricacion por estado del periodo. Adaptador por defecto: indicadores vacios.
 */
public interface IndicadorProduccionPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.PRODUCCION;
    }
}
