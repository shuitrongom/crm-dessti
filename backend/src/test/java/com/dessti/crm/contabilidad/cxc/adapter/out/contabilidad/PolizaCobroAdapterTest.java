package com.dessti.crm.contabilidad.cxc.adapter.out.contabilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.CuentaContableRepository;
import com.dessti.crm.contabilidad.polizas.application.PolizaContablePort;
import com.dessti.crm.contabilidad.polizas.application.RenglonPolizaCommand;
import com.dessti.crm.contabilidad.polizas.domain.CuentaContable;
import com.dessti.crm.contabilidad.polizas.domain.NaturalezaCuenta;
import com.dessti.crm.contabilidad.polizas.domain.TipoCuentaContable;
import com.dessti.crm.contabilidad.polizas.domain.TipoPoliza;
import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas del {@link PolizaCobroAdapter}: el asiento de cobro balancea (cargo a
 * bancos == abono a clientes) y el adaptador degrada con {@link Optional#empty()}
 * cuando el catalogo esta incompleto o el periodo esta cerrado (nunca revierte el
 * pago ya registrado).
 */
class PolizaCobroAdapterTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 15);
    private static final UUID PAGO = UUID.randomUUID();

    private CuentaContableRepository cuentaRepo;
    private PolizaContablePort polizaPort;
    private PolizaCobroAdapter adapter;

    @BeforeEach
    void setUp() {
        cuentaRepo = mock(CuentaContableRepository.class);
        polizaPort = mock(PolizaContablePort.class);
        adapter = new PolizaCobroAdapter(cuentaRepo, polizaPort);
        when(polizaPort.generarPolizaDeEvento(any(), any(), any(), any(), any(), anyList()))
                .thenReturn(UUID.randomUUID());
    }

    private CuentaContable cuenta(String codigo, TipoCuentaContable tipo, NaturalezaCuenta nat) {
        return CuentaContable.crear(codigo, "Cuenta " + codigo, tipo, nat, "actor");
    }

    private void sembrarCuentas() {
        when(cuentaRepo.findByCodigo("102-01")).thenReturn(Optional.of(
                cuenta("102-01", TipoCuentaContable.ACTIVO, NaturalezaCuenta.DEUDORA)));
        when(cuentaRepo.findByCodigo("105-01")).thenReturn(Optional.of(
                cuenta("105-01", TipoCuentaContable.ACTIVO, NaturalezaCuenta.DEUDORA)));
    }

    @Test
    @DisplayName("El asiento de cobro balancea: cargo a bancos == abono a clientes == monto")
    @SuppressWarnings("unchecked")
    void asientoBalancea() {
        sembrarCuentas();
        Optional<UUID> resultado = adapter.generarPolizaCobro(FECHA, PAGO, new BigDecimal("2500.00"));

        assertThat(resultado).isPresent();
        ArgumentCaptor<List<RenglonPolizaCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(polizaPort).generarPolizaDeEvento(
                eq(FECHA), eq(TipoPoliza.INGRESO), any(), eq("pago_cliente"), eq(PAGO),
                captor.capture());
        List<RenglonPolizaCommand> renglones = captor.getValue();
        BigDecimal cargos = renglones.stream()
                .map(r -> r.cargo() == null ? BigDecimal.ZERO : r.cargo())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal abonos = renglones.stream()
                .map(r -> r.abono() == null ? BigDecimal.ZERO : r.abono())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(cargos).isEqualByComparingTo("2500.00");
        assertThat(abonos).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("Catalogo incompleto (falta Bancos): se omite la poliza (empty)")
    void catalogoIncompletoOmite() {
        when(cuentaRepo.findByCodigo("102-01")).thenReturn(Optional.empty());
        when(cuentaRepo.findByCodigo("105-01")).thenReturn(Optional.of(
                cuenta("105-01", TipoCuentaContable.ACTIVO, NaturalezaCuenta.DEUDORA)));

        Optional<UUID> resultado = adapter.generarPolizaCobro(FECHA, PAGO, new BigDecimal("2500.00"));

        assertThat(resultado).isEmpty();
        verify(polizaPort, never()).generarPolizaDeEvento(any(), any(), any(), any(), any(), anyList());
    }

    @Test
    @DisplayName("Periodo cerrado (el puerto lanza ReglaNegocio): se degrada a empty")
    void periodoCerradoDegrada() {
        sembrarCuentas();
        when(polizaPort.generarPolizaDeEvento(any(), any(), any(), any(), any(), anyList()))
                .thenThrow(new ReglaNegocioException("El periodo 2026-03 esta cerrado."));

        Optional<UUID> resultado = adapter.generarPolizaCobro(FECHA, PAGO, new BigDecimal("2500.00"));

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Monto no positivo: se omite la poliza (empty)")
    void montoNoPositivoOmite() {
        Optional<UUID> resultado = adapter.generarPolizaCobro(FECHA, PAGO, BigDecimal.ZERO);
        assertThat(resultado).isEmpty();
        verify(polizaPort, never()).generarPolizaDeEvento(any(), any(), any(), any(), any(), anyList());
    }
}
