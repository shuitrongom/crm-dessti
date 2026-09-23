package com.dessti.crm.comercial.adapter.out.indicadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dessti.crm.comercial.cotizacion.adapter.out.persistence.CotizacionRepository;
import com.dessti.crm.comercial.cotizacion.domain.EstadoCotizacion;
import com.dessti.crm.comercial.oportunidad.adapter.out.persistence.OportunidadRepository;
import com.dessti.crm.comercial.oportunidad.domain.EtapaOportunidad;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Pruebas unitarias deterministas de {@link IndicadorComercialAdapter} (Req 22.1) con
 * dobles Mockito de los repositorios: verifican que las agregaciones por etapa/estado se
 * mapean a las claves y unidades correctas del pipeline y de las Cotizaciones, sin
 * arrancar Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IndicadorComercialAdapter - mapeo de agregaciones a ValorIndicador (Req 22.1)")
class IndicadorComercialAdapterTest {

    @Mock
    private OportunidadRepository oportunidadRepository;

    @Mock
    private CotizacionRepository cotizacionRepository;

    @Test
    @DisplayName("mapea pipeline por etapa, valor del pipeline y cotizaciones por estado")
    void mapeaIndicadoresComerciales() {
        when(oportunidadRepository.contarPorEtapa(any(), any(), any())).thenReturn(List.of(
                new Object[] {EtapaOportunidad.NUEVO, 3L},
                new Object[] {EtapaOportunidad.CALIFICADO, 2L},
                new Object[] {EtapaOportunidad.GANADO, 4L},
                new Object[] {EtapaOportunidad.PERDIDO, 1L}));
        when(oportunidadRepository.sumarValorPipelineAbierto(any(), any(), any()))
                .thenReturn(new BigDecimal("15000.00"));
        when(cotizacionRepository.contarPorEstado(any(), any(), any())).thenReturn(List.of(
                new Object[] {EstadoCotizacion.APROBADA, 5L},
                new Object[] {EstadoCotizacion.ENVIADA, 2L}));

        IndicadorComercialAdapter adapter =
                new IndicadorComercialAdapter(oportunidadRepository, cotizacionRepository);

        IndicadoresArea resultado = adapter.agregar(
                FiltroIndicadores.deTablero(null, null, null));

        assertThat(resultado.area()).isEqualTo(AreaIndicador.COMERCIAL);
        Map<String, ValorIndicador> porClave = indexar(resultado);

        assertThat(porClave.get("oportunidades_pipeline_abierto").valor())
                .isEqualByComparingTo("5"); // 3 + 2 (nuevo + calificado)
        assertThat(porClave.get("oportunidades_ganadas").valor()).isEqualByComparingTo("4");
        assertThat(porClave.get("oportunidades_perdidas").valor()).isEqualByComparingTo("1");

        ValorIndicador valorPipeline = porClave.get("valor_pipeline_abierto");
        assertThat(valorPipeline.valor()).isEqualByComparingTo("15000.00");
        assertThat(valorPipeline.unidad()).isEqualTo("MXN");

        assertThat(porClave.get("cotizaciones_aprobadas").valor()).isEqualByComparingTo("5");
        assertThat(porClave.get("cotizaciones_enviadas").valor()).isEqualByComparingTo("2");
        assertThat(porClave.get("cotizaciones_borrador").valor()).isEqualByComparingTo("0");
        assertThat(porClave.get("cotizaciones_aprobadas").unidad()).isEqualTo("conteo");
    }

    @Test
    @DisplayName("sin datos devuelve indicadores en cero, no vacios")
    void sinDatosDevuelveCeros() {
        when(oportunidadRepository.contarPorEtapa(any(), any(), any())).thenReturn(List.of());
        when(oportunidadRepository.sumarValorPipelineAbierto(any(), any(), any())).thenReturn(null);
        when(cotizacionRepository.contarPorEstado(any(), any(), any())).thenReturn(List.of());

        IndicadorComercialAdapter adapter =
                new IndicadorComercialAdapter(oportunidadRepository, cotizacionRepository);

        IndicadoresArea resultado = adapter.agregar(
                FiltroIndicadores.deTablero(null, null, UUID.randomUUID()));

        assertThat(resultado.indicadores()).isNotEmpty();
        assertThat(indexar(resultado).get("valor_pipeline_abierto").valor())
                .isEqualByComparingTo("0");
    }

    private static Map<String, ValorIndicador> indexar(IndicadoresArea area) {
        return area.indicadores().stream()
                .collect(Collectors.toMap(ValorIndicador::clave, v -> v));
    }
}
