package com.dessti.crm.activosfijos.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.dessti.crm.activosfijos.domain.MetodoDepreciacion;

/**
 * Comando de aplicacion para dar de alta un Activo_Fijo (Req 44.1). DTO interno de
 * la capa de aplicacion, distinto del request REST y de la entidad JPA (Req 12.2).
 * El controlador lo construye a partir del request antes de invocar el servicio.
 *
 * <p>El {@code tenant_id} y el actor NO se transportan aqui: se derivan del
 * contexto autenticado (Req 23.4).</p>
 *
 * @param nombre           nombre del bien; obligatorio (1..200).
 * @param costo            costo de adquisicion; obligatorio y &gt; 0.
 * @param fechaAdquisicion fecha de adquisicion; obligatoria.
 * @param vidaUtilMeses    vida util en meses; obligatoria y &gt; 0.
 * @param metodo           metodo de depreciacion; obligatorio.
 * @param valorResidual    valor residual; {@code null} se asume 0; en {@code [0, costo]}.
 */
public record CrearActivoFijoCommand(
        String nombre,
        BigDecimal costo,
        LocalDate fechaAdquisicion,
        Integer vidaUtilMeses,
        MetodoDepreciacion metodo,
        BigDecimal valorResidual) {
}
