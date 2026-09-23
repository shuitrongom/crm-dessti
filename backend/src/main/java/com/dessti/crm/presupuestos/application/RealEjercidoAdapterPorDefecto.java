package com.dessti.crm.presupuestos.application;

import java.math.BigDecimal;

import com.dessti.crm.presupuestos.domain.CalculoVariacionPresupuesto;

/**
 * Implementacion <strong>por defecto</strong> (placeholder) del
 * {@link RealEjercidoPort}: devuelve un ejercicio real de <strong>cero</strong> para
 * cualquier area y periodo (Req 62.2, 62.8).
 *
 * <h2>Proposito y sustituibilidad</h2>
 * <p>Permite que el modulo de presupuestos compile y funcione de extremo a extremo
 * (crear/consultar variacion/listar) sin acoplarse aun a la agregacion real de
 * facturacion, compras y nomina. Se registra en {@link PresupuestosConfig} con
 * {@code @ConditionalOnMissingBean(RealEjercidoPort.class)}: en cuanto un modulo de
 * origen aporte un adaptador concreto que agregue el real por area/periodo, este
 * placeholder deja de registrarse automaticamente y la integracion real toma su lugar
 * sin tocar el motor de calculo de variacion.</p>
 *
 * <p>Con real cero, la variacion resultante es simplemente {@code -presupuestado} en
 * importe (y el porcentaje correspondiente), lo que es coherente y determinista; el
 * calculo puro de variacion (Property 36) es correcto sea cual sea el origen del
 * dato. Los importes se normalizan a la escala monetaria estandar (2, HALF_UP).</p>
 */
public class RealEjercidoAdapterPorDefecto implements RealEjercidoPort {

    /** Cero monetario normalizado a escala 2. */
    private static final BigDecimal CERO = CalculoVariacionPresupuesto.normalizar(BigDecimal.ZERO);

    @Override
    public BigDecimal realIngresos(String area, String periodo) {
        return CERO;
    }

    @Override
    public BigDecimal realEgresos(String area, String periodo) {
        return CERO;
    }
}
