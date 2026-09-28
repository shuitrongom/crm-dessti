package com.dessti.crm.contabilidad.polizas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.CuentaContableRepository;
import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.PolizaContableRepository;
import com.dessti.crm.contabilidad.polizas.domain.PolizaContable;
import com.dessti.crm.contabilidad.polizas.domain.TipoPoliza;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas del candado contable en {@link ServicioContabilidad}: el registro y el
 * reverso de Polizas_Contables se rechazan (422) cuando el periodo de la fecha esta
 * cerrado, y se permiten cuando esta abierto. Como el registro es el unico punto de
 * entrada de polizas, este candado cubre tambien la generacion automatica por
 * {@code generarPolizaDeEvento}.
 */
class CandadoPeriodoContabilidadTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CUENTA_A = UUID.randomUUID();
    private static final UUID CUENTA_B = UUID.randomUUID();
    private static final LocalDate FECHA = LocalDate.of(2026, 3, 15);

    private CuentaContableRepository cuentaRepo;
    private PolizaContableRepository polizaRepo;
    private AuditoriaPort auditoria;
    private PeriodoContablePort periodoPort;
    private ServicioContabilidad servicio;

    @BeforeEach
    void setUp() {
        cuentaRepo = mock(CuentaContableRepository.class);
        polizaRepo = mock(PolizaContableRepository.class);
        auditoria = mock(AuditoriaPort.class);
        periodoPort = mock(PeriodoContablePort.class);
        servicio = new ServicioContabilidad(cuentaRepo, polizaRepo, auditoria, periodoPort);
        TenantContext.set(TENANT);
        when(polizaRepo.save(any(PolizaContable.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private RegistrarPolizaCommand comandoBalanceado() {
        return new RegistrarPolizaCommand(
                FECHA, TipoPoliza.DIARIO, "Asiento de prueba", "manual", null,
                List.of(
                        new RenglonPolizaCommand(CUENTA_A, new BigDecimal("100.00"), null),
                        new RenglonPolizaCommand(CUENTA_B, null, new BigDecimal("100.00"))));
    }

    @Test
    @DisplayName("registrarPoliza con periodo cerrado se rechaza (422) y no persiste nada")
    void registrarEnPeriodoCerradoRechaza() {
        when(periodoPort.estaCerrado(2026, 3)).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrarPoliza(comandoBalanceado()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("2026-03")
                .hasMessageContaining("cerrado");

        verify(polizaRepo, never()).save(any(PolizaContable.class));
    }

    @Test
    @DisplayName("registrarPoliza con periodo abierto persiste normalmente")
    void registrarEnPeriodoAbiertoPersiste() {
        when(periodoPort.estaCerrado(2026, 3)).thenReturn(false);

        PolizaContableDto dto = servicio.registrarPoliza(comandoBalanceado());

        assertThat(dto).isNotNull();
        assertThat(dto.totalCargos()).isEqualByComparingTo("100.00");
        verify(polizaRepo).save(any(PolizaContable.class));
        verify(periodoPort).estaCerrado(2026, 3);
    }

    @Test
    @DisplayName("reversarPoliza cuya fecha de reverso cae en periodo cerrado se rechaza (422)")
    void reversarEnPeriodoCerradoRechaza() {
        // Poliza original en un periodo abierto, ya persistida.
        PolizaContable original = PolizaContable.crear(
                LocalDate.of(2026, 2, 10), TipoPoliza.DIARIO, "Original", "manual", null,
                List.of(
                        com.dessti.crm.contabilidad.polizas.domain.MovimientoPoliza.cargo(
                                CUENTA_A, new BigDecimal("50.00"), "actor"),
                        com.dessti.crm.contabilidad.polizas.domain.MovimientoPoliza.abono(
                                CUENTA_B, new BigDecimal("50.00"), "actor")),
                "actor");
        UUID polizaId = original.getId();
        when(polizaRepo.findById(polizaId)).thenReturn(java.util.Optional.of(original));
        // El reverso se dirige a marzo, que esta cerrado.
        when(periodoPort.estaCerrado(2026, 3)).thenReturn(true);

        assertThatThrownBy(() -> servicio.reversarPoliza(polizaId, LocalDate.of(2026, 3, 1)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("2026-03");

        verify(polizaRepo, never()).save(any(PolizaContable.class));
    }
}
