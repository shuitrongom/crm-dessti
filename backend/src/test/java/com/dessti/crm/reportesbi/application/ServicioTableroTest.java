package com.dessti.crm.reportesbi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorComercialVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorComprasVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorCxpVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorEstrategiaVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorFinanzasVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorInstalacionVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorInventarioAvanzadoVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorInventarioVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorMantenimientoVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorPresupuestoVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorProduccionVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorRhNominaVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorSocialVacio;
import com.dessti.crm.reportesbi.adapter.out.indicadores.IndicadorTesoreriaVacio;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorAreaPort;

/**
 * Pruebas unitarias de {@link ServicioTablero} (Req 22). Usan los adaptadores por
 * defecto (indicadores en cero) como puertos inyectados y un doble de
 * {@link AuditoriaPort}; no arrancan Spring ni base de datos. Verifican que el Tablero
 * se compone con TODAS las areas del Req 22.1 aunque no haya adaptador concreto (cada
 * area en cero, Req 22.2), que audita la consulta y la exportacion (Req 22.6) y que
 * rechaza un rango de fechas incoherente (422).
 */
class ServicioTableroTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2024-01-15T00:00:00Z"), ZoneOffset.UTC);

    private AuditoriaPort auditoria;
    private ServicioTablero servicio;

    private static List<IndicadorAreaPort> puertosPorDefecto() {
        return List.of(
                new IndicadorComercialVacio(),
                new IndicadorProduccionVacio(),
                new IndicadorInstalacionVacio(),
                new IndicadorMantenimientoVacio(),
                new IndicadorInventarioVacio(),
                new IndicadorInventarioAvanzadoVacio(),
                new IndicadorComprasVacio(),
                new IndicadorFinanzasVacio(),
                new IndicadorRhNominaVacio(),
                new IndicadorTesoreriaVacio(),
                new IndicadorCxpVacio(),
                new IndicadorEstrategiaVacio(),
                new IndicadorPresupuestoVacio(),
                new IndicadorSocialVacio());
    }

    @BeforeEach
    void setUp() {
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioTablero(puertosPorDefecto(), auditoria, RELOJ);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("consultarTablero compone todas las areas del Req 22.1 en cero y audita (Req 22.2, 22.6)")
    void componeTodasLasAreasEnCero() {
        TableroDto tablero = servicio.consultarTablero(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), null, false);

        // Una entrada por cada area del catalogo (Req 22.1), sin metricas (cero).
        assertThat(tablero.areas()).hasSize(AreaIndicador.values().length);
        assertThat(tablero.areas()).allSatisfy(area ->
                assertThat(area.indicadores()).isEmpty());
        assertThat(tablero.generadoEn()).isEqualTo(RELOJ.instant());
        verify(auditoria, times(1)).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("exportar audita la accion de exportacion (Req 22.4, 22.6)")
    void exportarAudita() {
        servicio.consultarTablero(null, null, null, true);
        verify(auditoria, times(1)).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("consultarTablero rechaza un rango de fechas incoherente (422)")
    void rangoIncoherenteRechazado() {
        assertThatThrownBy(() -> servicio.consultarTablero(
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1), null, false))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
