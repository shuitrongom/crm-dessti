package com.dessti.crm.operacion.produccion.adapter.out.indicadores;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorProduccionPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.RangoPeriodo;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.OrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.domain.EstadoOrdenFabricacion;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorProduccionPort}
 * (Req 22.1, 48.1). Deriva del propio submodulo de Ordenes de Fabricacion del vertical de
 * anuncios las Ordenes por estado del periodo indicado en el {@link FiltroIndicadores},
 * agregando unicamente con consultas {@code COUNT} que no modifican dato alguno (Req 22.2,
 * 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorProduccionVacio}. El aislamiento por tenant (Req 23) lo
 * garantizan el filtro global de Hibernate y la RLS de PostgreSQL activos sobre el
 * repositorio del modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 */
@Component
public class IndicadorProduccionAdapter implements IndicadorProduccionPort {

    private final OrdenFabricacionRepository ordenFabricacionRepository;

    /**
     * Crea el adaptador con el repositorio de solo lectura de Ordenes de Fabricacion.
     *
     * @param ordenFabricacionRepository repositorio de Ordenes de Fabricacion.
     */
    public IndicadorProduccionAdapter(OrdenFabricacionRepository ordenFabricacionRepository) {
        this.ordenFabricacionRepository = ordenFabricacionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        // Cotas no nulas [EPOCH, 9999): preservan la semantica "sin limite" y evitan el
        // fallo de inferencia de tipo de PostgreSQL sobre columnas timestamp.
        Instant desde = RangoPeriodo.desdeInclusivoOMinimo(filtro.desde());
        Instant hasta = RangoPeriodo.hastaExclusivoOMaximo(filtro.hasta());

        long[] conteo = new long[EstadoOrdenFabricacion.values().length];
        for (Object[] fila : ordenFabricacionRepository.contarPorEstado(desde, hasta)) {
            EstadoOrdenFabricacion estado = (EstadoOrdenFabricacion) fila[0];
            conteo[estado.ordinal()] = ((Number) fila[1]).longValue();
        }

        List<ValorIndicador> indicadores = new ArrayList<>();
        indicadores.add(ValorIndicador.conteo(
                "ordenes_fabricacion_pendientes",
                "Ordenes de fabricacion pendientes",
                BigDecimal.valueOf(conteo[EstadoOrdenFabricacion.PENDIENTE.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "ordenes_fabricacion_en_produccion",
                "Ordenes de fabricacion en produccion",
                BigDecimal.valueOf(conteo[EstadoOrdenFabricacion.EN_PRODUCCION.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "ordenes_fabricacion_terminadas",
                "Ordenes de fabricacion terminadas",
                BigDecimal.valueOf(conteo[EstadoOrdenFabricacion.TERMINADA.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "ordenes_fabricacion_canceladas",
                "Ordenes de fabricacion canceladas",
                BigDecimal.valueOf(conteo[EstadoOrdenFabricacion.CANCELADA.ordinal()])));

        return new IndicadoresArea(AreaIndicador.PRODUCCION, indicadores);
    }
}
