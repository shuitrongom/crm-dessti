package com.dessti.crm.activosfijos.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida (frontera hexagonal) que desacopla al {@code ServicioActivosFijos}
 * de la contabilidad al generar la Poliza_Contable de una depreciacion de periodo
 * (Req 44.3, 38.2). La implementacion arma una poliza <strong>balanceada</strong>
 * (cargo al gasto por depreciacion, abono a la depreciacion acumulada) y la delega
 * al submodulo de Polizas Contables.
 *
 * <h2>Generacion condicionada (decision documentada)</h2>
 * <p>La generacion depende de que el catalogo contable del tenant tenga definidas
 * las cuentas estandar de depreciacion (ver la implementacion). Si no lo estan, la
 * poliza NO se genera y el metodo devuelve {@link Optional#empty()}, de modo que la
 * depreciacion del periodo aun se registre (con {@code poliza_contable_id} nulo) y
 * la capa de aplicacion lo audite. Esto evita acoplar el alta de Activos_Fijos a la
 * configuracion contable y mantiene el flujo operativo.</p>
 */
public interface PolizaDepreciacionPort {

    /**
     * Genera y persiste una Poliza_Contable balanceada para la depreciacion del
     * periodo de un Activo_Fijo (Req 44.3, 38.2), si el catalogo contable define las
     * cuentas estandar de depreciacion.
     *
     * @param fecha        fecha contable de la poliza; obligatoria.
     * @param activoFijoId Activo_Fijo depreciado (origen del evento contable).
     * @param periodo      periodo mensual {@code 'AAAA-MM'} depreciado (para el concepto).
     * @param monto        monto de la depreciacion del periodo; debe ser &gt; 0.
     * @param actor        identificador de quien genera (auditoria).
     * @return el identificador de la Poliza_Contable generada, o
     *         {@link Optional#empty()} si no pudo generarse (catalogo incompleto).
     */
    Optional<UUID> generarPolizaDepreciacion(LocalDate fecha, UUID activoFijoId, String periodo,
                                             BigDecimal monto, String actor);
}
