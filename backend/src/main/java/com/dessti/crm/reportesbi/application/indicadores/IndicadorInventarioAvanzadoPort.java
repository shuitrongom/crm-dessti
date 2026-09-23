package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>inventario avanzado</strong> por Almacen
 * (Req 22.1, 48.1). El adaptador concreto (modulo operacion-inventario avanzado) agrega
 * de solo lectura la valuacion de inventario y las existencias por Almacen. Adaptador
 * por defecto: indicadores vacios.
 */
public interface IndicadorInventarioAvanzadoPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.INVENTARIO_AVANZADO;
    }
}
