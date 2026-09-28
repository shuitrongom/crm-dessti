package com.dessti.crm.activosfijos.adapter.out.indicadores;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.activosfijos.adapter.out.persistence.ActivoFijoRepository;
import com.dessti.crm.activosfijos.domain.EstadoActivoFijo;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorActivoFijoPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorActivoFijoPort}
 * (Req 22.1, 48.1, 44). Deriva del propio modulo de activos fijos el <strong>valor neto en
 * libros</strong> (costo - depreciacion acumulada), el costo total, la depreciacion
 * acumulada de los Activos_Fijos vigentes y el numero de activos dados de baja, agregando
 * unicamente con consultas {@code SUM}/{@code COUNT} que no modifican dato alguno
 * (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorActivoFijoVacio}. El aislamiento por tenant (Req 23) lo
 * garantizan el filtro global de Hibernate y la RLS de PostgreSQL activos sobre el
 * repositorio del modulo; el {@code tenant_id} nunca viaja en el filtro. Los indicadores
 * de activos fijos reflejan la posicion patrimonial acumulada (no se acotan por el rango
 * del filtro del Tablero, coherente con el {@code IndicadorPresupuestoAdapter}).</p>
 */
@Component
public class IndicadorActivoFijoAdapter implements IndicadorActivoFijoPort {

    private final ActivoFijoRepository activoFijoRepository;

    /**
     * @param activoFijoRepository repositorio de solo lectura de Activos_Fijos.
     */
    public IndicadorActivoFijoAdapter(ActivoFijoRepository activoFijoRepository) {
        this.activoFijoRepository = activoFijoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        BigDecimal costo = valorOCero(activoFijoRepository.sumarCostoActivos());
        BigDecimal depreciacion = valorOCero(activoFijoRepository.sumarDepreciacionAcumulada());
        BigDecimal valorNeto = costo.subtract(depreciacion);
        long vigentes = activoFijoRepository.countByEstado(EstadoActivoFijo.ACTIVO);
        long bajas = activoFijoRepository.countByEstado(EstadoActivoFijo.BAJA);

        return new IndicadoresArea(AreaIndicador.ACTIVO_FIJO, List.of(
                ValorIndicador.monetario(
                        "activos_valor_neto_libros",
                        "Valor neto en libros",
                        valorNeto),
                ValorIndicador.monetario(
                        "activos_costo_total",
                        "Costo total de activos",
                        costo),
                ValorIndicador.monetario(
                        "activos_depreciacion_acumulada",
                        "Depreciacion acumulada",
                        depreciacion),
                ValorIndicador.conteo(
                        "activos_vigentes",
                        "Activos fijos vigentes",
                        BigDecimal.valueOf(vigentes)),
                ValorIndicador.conteo(
                        "activos_baja",
                        "Activos dados de baja",
                        BigDecimal.valueOf(bajas))));
    }

    private static BigDecimal valorOCero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
