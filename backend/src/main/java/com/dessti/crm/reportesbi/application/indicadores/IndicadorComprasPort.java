package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>compras</strong> (Req 22.1, 48.1). El
 * adaptador concreto (modulo compras) agrega de solo lectura las Ordenes de compra por
 * estado y las Facturas de proveedor con discrepancia. Adaptador por defecto:
 * indicadores vacios.
 */
public interface IndicadorComprasPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.COMPRAS;
    }
}
