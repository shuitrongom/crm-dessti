package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>inventario</strong> de Materiales (Req 22.1,
 * 48.1). El adaptador concreto (modulo operacion-inventario) agrega de solo lectura los
 * Materiales bajo stock minimo, las existencias por Almacen y la valuacion. Adaptador
 * por defecto: indicadores vacios.
 */
public interface IndicadorInventarioPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.INVENTARIO;
    }
}
