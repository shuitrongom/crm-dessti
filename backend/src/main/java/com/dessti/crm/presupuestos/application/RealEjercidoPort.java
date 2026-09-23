package com.dessti.crm.presupuestos.application;

import java.math.BigDecimal;

/**
 * Puerto de salida de <strong>SOLO LECTURA</strong> que expone el ejercicio REAL
 * (ingresos y egresos efectivamente incurridos) por {@code area} y {@code periodo},
 * derivado de las operaciones del CRM: facturacion, compras y nomina (Req 62.2,
 * 62.8).
 *
 * <h2>Diseno hexagonal y desacoplamiento (Req 62.2, 62.8)</h2>
 * <p>El modulo de presupuestos DECLARA y CONSUME esta interfaz para calcular la
 * variacion frente a lo estimado como una <strong>agregacion de solo lectura</strong>
 * que NO modifica ningun origen de datos. De este modo el motor de presupuesto no se
 * acopla a la persistencia de facturacion/compras/nomina: son esos modulos quienes,
 * en su momento, pueden aportar un adaptador concreto que implemente este puerto
 * (agregando Facturas, Ordenes de Compra y Nomina del area y periodo) sin tocar el
 * calculo de variacion.</p>
 *
 * <p>Mientras no exista un adaptador concreto, {@link RealEjercidoAdapterPorDefecto}
 * lo implementa devolviendo cero (placeholder documentado), registrado con
 * {@code @ConditionalOnMissingBean} en {@link PresupuestosConfig}, de modo que la
 * consulta de variacion funcione de extremo a extremo desde ya y la variacion
 * calculada (Property 36) sea correcta independientemente del origen del dato.</p>
 *
 * <p>Todas las consultas quedan acotadas al tenant vigente por el filtro global de
 * Hibernate y la RLS de los modulos de origen (Req 23).</p>
 */
public interface RealEjercidoPort {

    /**
     * Ingresos reales agregados (solo lectura) del {@code area} y {@code periodo}
     * indicados (Req 62.8). Un area/periodo sin operaciones devuelve cero.
     *
     * @param area    area funcional a consultar; obligatoria.
     * @param periodo periodo 'AAAA-MM' o codigo equivalente; obligatorio.
     * @return el importe real de ingresos (no negativo), nunca {@code null}.
     */
    BigDecimal realIngresos(String area, String periodo);

    /**
     * Egresos reales agregados (solo lectura) del {@code area} y {@code periodo}
     * indicados (Req 62.8). Un area/periodo sin operaciones devuelve cero.
     *
     * @param area    area funcional a consultar; obligatoria.
     * @param periodo periodo 'AAAA-MM' o codigo equivalente; obligatorio.
     * @return el importe real de egresos (no negativo), nunca {@code null}.
     */
    BigDecimal realEgresos(String area, String periodo);
}
