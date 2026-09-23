package com.dessti.crm.operacion.inventario.avanzado.adapter.out.indicadores;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.ExistenciaAlmacenRepository;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorInventarioAvanzadoPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del
 * {@link IndicadorInventarioAvanzadoPort} (Req 22.1, 48.1). Deriva del propio submodulo de
 * inventario avanzado la valuacion del inventario y las existencias por Almacen, agregando
 * unicamente con consultas {@code SUM}/{@code COUNT} que no modifican dato alguno
 * (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorInventarioAvanzadoVacio}. El aislamiento por tenant (Req 23) lo
 * garantizan el filtro global de Hibernate y la RLS de PostgreSQL activos sobre el
 * repositorio del modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 *
 * <p>La valuacion y las existencias son un estado <em>actual</em> del inventario, no un
 * flujo del periodo; por ello el rango de fechas del filtro no acota estos indicadores.</p>
 */
@Component
public class IndicadorInventarioAvanzadoAdapter implements IndicadorInventarioAvanzadoPort {

    private final ExistenciaAlmacenRepository existenciaAlmacenRepository;

    /**
     * Crea el adaptador con el repositorio de solo lectura de saldos por Almacen.
     *
     * @param existenciaAlmacenRepository repositorio de existencias por Almacen.
     */
    public IndicadorInventarioAvanzadoAdapter(
            ExistenciaAlmacenRepository existenciaAlmacenRepository) {
        this.existenciaAlmacenRepository = existenciaAlmacenRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        BigDecimal valuacionTotal = existenciaAlmacenRepository.sumarValuacionTotal();
        long almacenesConExistencias = existenciaAlmacenRepository.contarAlmacenesConExistencias();

        return new IndicadoresArea(AreaIndicador.INVENTARIO_AVANZADO, List.of(
                ValorIndicador.monetario(
                        "valuacion_inventario_total",
                        "Valuacion total del inventario",
                        valuacionTotal != null ? valuacionTotal : BigDecimal.ZERO),
                ValorIndicador.conteo(
                        "almacenes_con_existencias",
                        "Almacenes con existencias",
                        BigDecimal.valueOf(almacenesConExistencias))));
    }
}
