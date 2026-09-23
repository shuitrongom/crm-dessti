package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>finanzas/facturacion</strong> (Req 22.1,
 * 48.1). El adaptador concreto (modulos facturacion/contabilidad) agrega de solo
 * lectura la facturacion del periodo, las CxC vencidas y el IVA del periodo. Adaptador
 * por defecto: indicadores vacios.
 */
public interface IndicadorFinanzasPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.FINANZAS;
    }
}
