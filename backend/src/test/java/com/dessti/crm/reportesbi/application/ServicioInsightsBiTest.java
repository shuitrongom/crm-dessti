package com.dessti.crm.reportesbi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.reportesbi.application.ia.GeneradorInsightsPort;
import com.dessti.crm.reportesbi.application.ia.ResultadoInsights;
import com.dessti.crm.reportesbi.application.ia.SolicitudInsights;

/**
 * Pruebas unitarias de {@link ServicioInsightsBi} (suite BI+IA). Con dobles del
 * servicio consolidado, el puerto de IA y la auditoria (sin Spring ni base de datos)
 * verifican que aplana los indicadores preservando su area, delega en el puerto de IA,
 * mapea la procedencia al DTO y audita la accion {@code insights}.
 */
class ServicioInsightsBiTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2024-03-01T00:00:00Z"), ZoneOffset.UTC);

    private ServicioInteligenciaNegocio consolidadoServicio;
    private GeneradorInsightsPort generador;
    private AuditoriaPort auditoria;
    private ServicioInsightsBi servicio;

    @BeforeEach
    void setUp() {
        consolidadoServicio = mock(ServicioInteligenciaNegocio.class);
        generador = mock(GeneradorInsightsPort.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioInsightsBi(consolidadoServicio, generador, auditoria, RELOJ);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static InteligenciaNegocioDto consolidadoDe() {
        IndicadorDto cotiz = new IndicadorDto("cotiz", "Cotizaciones",
                new BigDecimal("12"), "conteo", new BigDecimal("8"), new BigDecimal("4"));
        IndicadorDto fact = new IndicadorDto("fact", "Facturacion",
                new BigDecimal("50000"), "MXN", null, null);
        return new InteligenciaNegocioDto(
                RELOJ.instant(), LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), null, null,
                List.of(
                        new IndicadoresAreaDto("COMERCIAL", List.of(cotiz)),
                        new IndicadoresAreaDto("FINANZAS", List.of(fact))));
    }

    @Test
    @DisplayName("Aplana los indicadores preservando su area y delega en el puerto de IA")
    void aplanaYDelega() {
        when(consolidadoServicio.consolidado(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(consolidadoDe());
        when(generador.generar(any(SolicitudInsights.class)))
                .thenReturn(ResultadoInsights.deIa("Resumen IA", List.of("Hallazgo 1")));

        InsightsDto dto = servicio.generar(
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), null, null);

        ArgumentCaptor<SolicitudInsights> captor = ArgumentCaptor.forClass(SolicitudInsights.class);
        verify(generador).generar(captor.capture());
        SolicitudInsights solicitud = captor.getValue();
        assertThat(solicitud.indicadores()).hasSize(2);
        assertThat(solicitud.indicadores()).extracting("area")
                .containsExactly("COMERCIAL", "FINANZAS");
        assertThat(solicitud.indicadores().get(0).clave()).isEqualTo("cotiz");

        assertThat(dto.resumenEjecutivo()).isEqualTo("Resumen IA");
        assertThat(dto.hallazgos()).containsExactly("Hallazgo 1");
        assertThat(dto.generadoPorIa()).isTrue();
        assertThat(dto.generadoEn()).isEqualTo(RELOJ.instant());
    }

    @Test
    @DisplayName("Mapea generadoPorIa=false del heuristico y audita la accion insights")
    void mapeaHeuristicoYAudita() {
        when(consolidadoServicio.consolidado(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(consolidadoDe());
        when(generador.generar(any(SolicitudInsights.class)))
                .thenReturn(ResultadoInsights.deHeuristico("Resumen heuristico", List.of()));

        InsightsDto dto = servicio.generar(null, null, null, null);

        assertThat(dto.generadoPorIa()).isFalse();
        assertThat(dto.resumenEjecutivo()).isEqualTo("Resumen heuristico");

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("insights");
        assertThat(evento.getValue().recurso()).isEqualTo("inteligencia_negocio");
    }

    @Test
    @DisplayName("Pide el consolidado sin exportar (exportar=false)")
    void pideConsolidadoSinExportar() {
        when(consolidadoServicio.consolidado(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(consolidadoDe());
        when(generador.generar(any(SolicitudInsights.class)))
                .thenReturn(ResultadoInsights.deHeuristico("R", List.of()));

        servicio.generar(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), "COMERCIAL", "cliente");

        verify(consolidadoServicio).consolidado(
                eq(LocalDate.of(2024, 2, 1)), eq(LocalDate.of(2024, 2, 29)),
                eq("COMERCIAL"), eq("cliente"), eq(false));
    }
}
