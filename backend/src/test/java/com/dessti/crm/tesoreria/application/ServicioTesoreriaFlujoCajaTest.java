package com.dessti.crm.tesoreria.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.tesoreria.adapter.out.persistence.ConciliacionBancariaRepository;
import com.dessti.crm.tesoreria.adapter.out.persistence.CuentaBancariaRepository;
import com.dessti.crm.tesoreria.adapter.out.persistence.EstadoCuentaBancarioRepository;
import com.dessti.crm.tesoreria.adapter.out.persistence.MovimientoBancarioRepository;
import com.dessti.crm.tesoreria.adapter.out.persistence.TransferenciaBancariaRepository;

/**
 * Pruebas unitarias del calculo de flujo de caja de {@link ServicioTesoreria} (suite
 * Tesoreria). Con dobles de los repositorios (sin base de datos) verifican que el flujo
 * neto = entradas - |salidas|, que las salidas se exponen en valor absoluto, y que el
 * desglose mensual se arma con el formato 'AAAA-MM'.
 */
class ServicioTesoreriaFlujoCajaTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);

    private MovimientoBancarioRepository movimientos;
    private CuentaBancariaRepository cuentas;
    private ServicioTesoreria servicio;

    @BeforeEach
    void setUp() {
        movimientos = mock(MovimientoBancarioRepository.class);
        cuentas = mock(CuentaBancariaRepository.class);
        servicio = new ServicioTesoreria(
                cuentas,
                mock(EstadoCuentaBancarioRepository.class),
                movimientos,
                mock(ConciliacionBancariaRepository.class),
                mock(TransferenciaBancariaRepository.class),
                mock(ImportacionBancariaPort.class),
                mock(PolizaConciliablePort.class),
                mock(AuditoriaPort.class),
                RELOJ,
                mock(ConciliacionBancariaProperties.class));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Flujo neto = entradas - |salidas|; salidas en valor absoluto; meses formateados")
    void componeFlujoCaja() {
        when(movimientos.sumarSaldoBancario(any(), any())).thenReturn(new BigDecimal("500000.00"));
        when(movimientos.sumarEntradas(any(), any())).thenReturn(new BigDecimal("300000.00"));
        when(movimientos.sumarSalidas(any(), any())).thenReturn(new BigDecimal("-180000.00"));
        when(movimientos.contarPendientesConciliacion(any(), any())).thenReturn(4L);
        when(cuentas.countByActivaTrue()).thenReturn(3L);
        when(movimientos.flujoMensual(any(), any())).thenReturn(List.of(
                new Object[] {2026, 3, new BigDecimal("120000.00"), new BigDecimal("-80000.00")},
                new Object[] {2026, 4, new BigDecimal("180000.00"), new BigDecimal("-100000.00")}));

        FlujoCajaDto dto = servicio.flujoCaja(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30));

        assertThat(dto.saldoAcumulado()).isEqualByComparingTo("500000.00");
        assertThat(dto.entradasPeriodo()).isEqualByComparingTo("300000.00");
        assertThat(dto.salidasPeriodo()).isEqualByComparingTo("180000.00");
        assertThat(dto.flujoNetoPeriodo()).isEqualByComparingTo("120000.00");
        assertThat(dto.cuentasActivas()).isEqualTo(3L);
        assertThat(dto.partidasPorConciliar()).isEqualTo(4L);
        assertThat(dto.meses()).hasSize(2);
        assertThat(dto.meses().get(0).periodo()).isEqualTo("2026-03");
        assertThat(dto.meses().get(0).salidas()).isEqualByComparingTo("80000.00");
        assertThat(dto.meses().get(0).neto()).isEqualByComparingTo("40000.00");
        assertThat(dto.meses().get(1).periodo()).isEqualTo("2026-04");
    }

    @Test
    @DisplayName("Sumas nulas se tratan como cero")
    void sumasNulasComoCero() {
        when(movimientos.sumarSaldoBancario(any(), any())).thenReturn(null);
        when(movimientos.sumarEntradas(any(), any())).thenReturn(null);
        when(movimientos.sumarSalidas(any(), any())).thenReturn(null);
        when(movimientos.contarPendientesConciliacion(any(), any())).thenReturn(0L);
        when(cuentas.countByActivaTrue()).thenReturn(0L);
        when(movimientos.flujoMensual(any(), any())).thenReturn(List.of());

        FlujoCajaDto dto = servicio.flujoCaja(null, null);

        assertThat(dto.saldoAcumulado()).isEqualByComparingTo("0.00");
        assertThat(dto.flujoNetoPeriodo()).isEqualByComparingTo("0.00");
        assertThat(dto.meses()).isEmpty();
    }
}
