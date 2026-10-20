package com.dessti.crm.contabilidad.cxc.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dessti.crm.contabilidad.cxc.adapter.out.persistence.AplicacionPagoRepository;
import com.dessti.crm.contabilidad.cxc.adapter.out.persistence.CuentaPorCobrarRepository;
import com.dessti.crm.contabilidad.cxc.adapter.out.persistence.PagoClienteRepository;
import com.dessti.crm.contabilidad.cxc.domain.CuentaPorCobrar;
import com.dessti.crm.contabilidad.cxc.domain.EstadoCuentaPorCobrar;
import com.dessti.crm.facturacion.application.PacPort;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias (Mockito) de {@link ServicioCuentasPorCobrar} centradas en
 * {@code registrarPorFacturaTimbrada} (Req 36.1, 36.5): crea la CxC con la
 * {@code fechaVencimiento} recibida y audita (AC-5/6); respeta la idempotencia de
 * {@code existsByFacturaId} (AC-8). No arranca Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServicioCuentasPorCobrar.registrarPorFacturaTimbrada (Req 36.1, 36.5)")
class ServicioCuentasPorCobrarTest {

    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID FACTURA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private CuentaPorCobrarRepository cuentaPorCobrarRepository;
    @Mock private PagoClienteRepository pagoClienteRepository;
    @Mock private AplicacionPagoRepository aplicacionPagoRepository;
    @Mock private PacPort pac;
    @Mock private AuditoriaPort auditoria;
    @Mock private PolizaCobroPort polizaCobro;

    private final Clock clock = Clock.fixed(Instant.parse("2025-01-10T12:00:00Z"), ZoneOffset.UTC);

    private ServicioCuentasPorCobrar servicio;

    @BeforeEach
    void setUp() {
        servicio = new ServicioCuentasPorCobrar(
                cuentaPorCobrarRepository, pagoClienteRepository, aplicacionPagoRepository,
                pac, auditoria, clock, polizaCobro);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("crea la CxC con la fechaVencimiento recibida y audita (AC-5/6)")
    void registraConVencimiento() {
        LocalDate vencimiento = LocalDate.of(2025, 2, 9);
        when(cuentaPorCobrarRepository.existsByFacturaId(FACTURA)).thenReturn(false);
        when(cuentaPorCobrarRepository.save(any(CuentaPorCobrar.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        servicio.registrarPorFacturaTimbrada(FACTURA, CLIENTE, new BigDecimal("1160.00"), vencimiento);

        ArgumentCaptor<CuentaPorCobrar> captor = ArgumentCaptor.forClass(CuentaPorCobrar.class);
        verify(cuentaPorCobrarRepository).save(captor.capture());
        CuentaPorCobrar guardada = captor.getValue();
        assertThat(guardada.getFechaVencimiento()).isEqualTo(vencimiento);
        assertThat(guardada.getFacturaId()).isEqualTo(FACTURA);
        assertThat(guardada.getClienteId()).isEqualTo(CLIENTE);
        assertThat(guardada.getSaldo()).isEqualByComparingTo(new BigDecimal("1160.00"));
        assertThat(guardada.getEstado()).isEqualTo(EstadoCuentaPorCobrar.PENDIENTE);
        verify(auditoria).registrar(any());
    }

    @Test
    @DisplayName("idempotencia: si ya existe CxC no guarda ni audita el registro (AC-8)")
    void registroIdempotente() {
        when(cuentaPorCobrarRepository.existsByFacturaId(FACTURA)).thenReturn(true);

        servicio.registrarPorFacturaTimbrada(
                FACTURA, CLIENTE, new BigDecimal("1160.00"), LocalDate.of(2025, 2, 9));

        verify(cuentaPorCobrarRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
        verify(cuentaPorCobrarRepository).existsByFacturaId(eq(FACTURA));
    }
}
