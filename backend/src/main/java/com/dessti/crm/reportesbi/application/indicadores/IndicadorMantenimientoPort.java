package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>mantenimiento</strong> (Req 22.1, 48.1). El
 * adaptador concreto (modulo mantenimiento) agrega de solo lectura el cumplimiento de
 * SLA de los Tickets de servicio del periodo. Adaptador por defecto: indicadores
 * vacios.
 */
public interface IndicadorMantenimientoPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.MANTENIMIENTO;
    }
}
