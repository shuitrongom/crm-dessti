package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>presupuesto</strong> (Req 22.1, 48.1). El
 * adaptador concreto (modulo presupuestos) agrega de solo lectura la variacion
 * presupuestal del periodo. Adaptador por defecto: indicadores vacios.
 */
public interface IndicadorPresupuestoPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.PRESUPUESTO;
    }
}
