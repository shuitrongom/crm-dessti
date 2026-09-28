package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>activos fijos</strong> (Req 22.1, 48.1, 44). El
 * adaptador concreto (modulo activosfijos) agrega de solo lectura el valor neto en libros,
 * el costo total y la depreciacion acumulada de los Activos_Fijos vigentes, y el numero de
 * activos de baja. Adaptador por defecto: indicadores vacios.
 */
public interface IndicadorActivoFijoPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.ACTIVO_FIJO;
    }
}
