package com.dessti.crm.presupuestos.adapter.out.indicadores;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.presupuestos.adapter.out.persistence.PresupuestoRepository;
import com.dessti.crm.presupuestos.application.RealEjercidoPort;
import com.dessti.crm.presupuestos.domain.CalculoVariacionPresupuesto;
import com.dessti.crm.presupuestos.domain.Presupuesto;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorPresupuestoPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorPresupuestoPort}
 * (Req 22.1, 48.1). Deriva del propio modulo de presupuestos la variacion presupuestal
 * consolidada: agrega los montos estimados de todos los Presupuestos del tenant y obtiene
 * el ejercicio real via el {@link RealEjercidoPort} (agregacion de solo lectura), sin
 * modificar ningun origen (Req 22.2, 48.2, 62.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorPresupuestoVacio}. El aislamiento por tenant (Req 23) lo
 * garantizan el filtro global de Hibernate y la RLS de PostgreSQL activos sobre el
 * repositorio del modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 *
 * <p>El periodo del Presupuesto es un codigo 'AAAA-MM' que no corresponde al rango de
 * fechas del filtro del Tablero; por ello se consolidan todos los Presupuestos del tenant.
 * La variacion se calcula con {@link CalculoVariacionPresupuesto#normalizar(BigDecimal)}
 * para conservar la escala monetaria del sistema.</p>
 */
@Component
public class IndicadorPresupuestoAdapter implements IndicadorPresupuestoPort {

    private final PresupuestoRepository presupuestoRepository;
    private final RealEjercidoPort realEjercido;

    /**
     * Crea el adaptador con el repositorio de solo lectura de Presupuestos y el puerto
     * del ejercicio real.
     *
     * @param presupuestoRepository repositorio de Presupuestos.
     * @param realEjercido          puerto de solo lectura del ejercicio real por area/periodo.
     */
    public IndicadorPresupuestoAdapter(PresupuestoRepository presupuestoRepository,
                                       RealEjercidoPort realEjercido) {
        this.presupuestoRepository = presupuestoRepository;
        this.realEjercido = realEjercido;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        List<Presupuesto> presupuestos = presupuestoRepository.buscarParaIndicadores(null);

        BigDecimal ingresosEstimados = BigDecimal.ZERO;
        BigDecimal egresosEstimados = BigDecimal.ZERO;
        BigDecimal ingresosReales = BigDecimal.ZERO;
        BigDecimal egresosReales = BigDecimal.ZERO;

        for (Presupuesto presupuesto : presupuestos) {
            ingresosEstimados = ingresosEstimados.add(presupuesto.getIngresosEstimados());
            egresosEstimados = egresosEstimados.add(presupuesto.getEgresosEstimados());
            ingresosReales = ingresosReales.add(
                    normalizarReal(realEjercido.realIngresos(presupuesto.getArea(), presupuesto.getPeriodo())));
            egresosReales = egresosReales.add(
                    normalizarReal(realEjercido.realEgresos(presupuesto.getArea(), presupuesto.getPeriodo())));
        }

        BigDecimal variacionIngresos = CalculoVariacionPresupuesto.normalizar(
                ingresosReales.subtract(ingresosEstimados));
        BigDecimal variacionEgresos = CalculoVariacionPresupuesto.normalizar(
                egresosReales.subtract(egresosEstimados));

        return new IndicadoresArea(AreaIndicador.PRESUPUESTO, List.of(
                ValorIndicador.monetario(
                        "presupuesto_ingresos_estimados",
                        "Ingresos presupuestados",
                        CalculoVariacionPresupuesto.normalizar(ingresosEstimados)),
                ValorIndicador.monetario(
                        "presupuesto_ingresos_reales",
                        "Ingresos reales",
                        CalculoVariacionPresupuesto.normalizar(ingresosReales)),
                ValorIndicador.monetario(
                        "presupuesto_variacion_ingresos",
                        "Variacion de ingresos (real - estimado)",
                        variacionIngresos),
                ValorIndicador.monetario(
                        "presupuesto_egresos_estimados",
                        "Egresos presupuestados",
                        CalculoVariacionPresupuesto.normalizar(egresosEstimados)),
                ValorIndicador.monetario(
                        "presupuesto_egresos_reales",
                        "Egresos reales",
                        CalculoVariacionPresupuesto.normalizar(egresosReales)),
                ValorIndicador.monetario(
                        "presupuesto_variacion_egresos",
                        "Variacion de egresos (real - estimado)",
                        variacionEgresos)));
    }

    private static BigDecimal normalizarReal(BigDecimal real) {
        return CalculoVariacionPresupuesto.normalizar(real != null ? real : BigDecimal.ZERO);
    }
}
