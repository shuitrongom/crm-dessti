package com.dessti.crm.comercial.adapter.out.indicadores;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.comercial.cotizacion.adapter.out.persistence.CotizacionRepository;
import com.dessti.crm.comercial.cotizacion.domain.EstadoCotizacion;
import com.dessti.crm.comercial.oportunidad.adapter.out.persistence.OportunidadRepository;
import com.dessti.crm.comercial.oportunidad.domain.EtapaOportunidad;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorComercialPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.RangoPeriodo;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorComercialPort}
 * (Req 22.1, 48.1). Deriva del propio modulo comercial-crm el pipeline de Oportunidades
 * y las Cotizaciones por estado del periodo/Cliente indicado en el {@link FiltroIndicadores},
 * agregando unicamente con consultas {@code COUNT}/{@code SUM} que no modifican dato alguno
 * (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorComercialVacio} ({@code @ConditionalOnMissingBean} en
 * {@code ReportesBiConfig}). El aislamiento por tenant (Req 23) lo garantizan el filtro
 * global de Hibernate y la RLS de PostgreSQL activos sobre los repositorios del modulo; el
 * {@code tenant_id} nunca viaja en el filtro.</p>
 */
@Component
public class IndicadorComercialAdapter implements IndicadorComercialPort {

    private final OportunidadRepository oportunidadRepository;
    private final CotizacionRepository cotizacionRepository;

    /**
     * Crea el adaptador con los repositorios de solo lectura del modulo comercial.
     *
     * @param oportunidadRepository repositorio de Oportunidades del pipeline.
     * @param cotizacionRepository  repositorio de Cotizaciones.
     */
    public IndicadorComercialAdapter(OportunidadRepository oportunidadRepository,
                                     CotizacionRepository cotizacionRepository) {
        this.oportunidadRepository = oportunidadRepository;
        this.cotizacionRepository = cotizacionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        // Cotas no nulas: un rango nulo se traduce a las cotas centinela [EPOCH, 9999)
        // preservando la semantica "sin limite" y evitando el fallo de inferencia de
        // tipo de PostgreSQL sobre columnas timestamp (patron ":param IS NULL OR ...").
        Instant desde = RangoPeriodo.desdeInclusivoOMinimo(filtro.desde());
        Instant hasta = RangoPeriodo.hastaExclusivoOMaximo(filtro.hasta());

        List<ValorIndicador> indicadores = new ArrayList<>();

        // Pipeline de Oportunidades por etapa (Req 22.1).
        long[] conteoEtapa = new long[EtapaOportunidad.values().length];
        for (Object[] fila : oportunidadRepository.contarPorEtapa(filtro.clienteId(), desde, hasta)) {
            EtapaOportunidad etapa = (EtapaOportunidad) fila[0];
            conteoEtapa[etapa.ordinal()] = ((Number) fila[1]).longValue();
        }
        indicadores.add(ValorIndicador.conteo(
                "oportunidades_pipeline_abierto",
                "Oportunidades en pipeline abierto",
                BigDecimal.valueOf(conteoEtapa[EtapaOportunidad.NUEVO.ordinal()]
                        + conteoEtapa[EtapaOportunidad.CALIFICADO.ordinal()]
                        + conteoEtapa[EtapaOportunidad.PROPUESTA.ordinal()]
                        + conteoEtapa[EtapaOportunidad.NEGOCIACION.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "oportunidades_ganadas",
                "Oportunidades ganadas",
                BigDecimal.valueOf(conteoEtapa[EtapaOportunidad.GANADO.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "oportunidades_perdidas",
                "Oportunidades perdidas",
                BigDecimal.valueOf(conteoEtapa[EtapaOportunidad.PERDIDO.ordinal()])));

        BigDecimal valorPipeline = oportunidadRepository.sumarValorPipelineAbierto(
                filtro.clienteId(), desde, hasta);
        indicadores.add(ValorIndicador.monetario(
                "valor_pipeline_abierto",
                "Valor estimado del pipeline abierto",
                valorPipeline != null ? valorPipeline : BigDecimal.ZERO));

        // Cotizaciones por estado (Req 22.1).
        long[] conteoEstado = new long[EstadoCotizacion.values().length];
        for (Object[] fila : cotizacionRepository.contarPorEstado(filtro.clienteId(), desde, hasta)) {
            EstadoCotizacion estado = (EstadoCotizacion) fila[0];
            conteoEstado[estado.ordinal()] = ((Number) fila[1]).longValue();
        }
        indicadores.add(ValorIndicador.conteo(
                "cotizaciones_borrador",
                "Cotizaciones en borrador",
                BigDecimal.valueOf(conteoEstado[EstadoCotizacion.BORRADOR.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "cotizaciones_enviadas",
                "Cotizaciones enviadas",
                BigDecimal.valueOf(conteoEstado[EstadoCotizacion.ENVIADA.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "cotizaciones_aprobadas",
                "Cotizaciones aprobadas",
                BigDecimal.valueOf(conteoEstado[EstadoCotizacion.APROBADA.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "cotizaciones_rechazadas",
                "Cotizaciones rechazadas",
                BigDecimal.valueOf(conteoEstado[EstadoCotizacion.RECHAZADA.ordinal()])));

        return new IndicadoresArea(AreaIndicador.COMERCIAL, indicadores);
    }
}
