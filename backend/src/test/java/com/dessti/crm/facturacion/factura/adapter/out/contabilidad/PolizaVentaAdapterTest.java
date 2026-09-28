package com.dessti.crm.facturacion.factura.adapter.out.contabilidad;

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
 * Pruebas del {@link PolizaVentaAdapter}: el asiento de ingreso generado balancea
 * (Property 16: suma de cargos == suma de abonos) y el adaptador degrada con
 * {@link Optional#empty()} cuando el catalogo contable esta incompleto o el periodo
 * esta cerrado (nunca revierte el CFDI ya timbrado).
 */
class PolizaVentaAdapterTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 15);
    private static final UUID FACTURA = UUID.randomUUID();

    private CuentaContableRepository cuentaRepo;
    private PolizaContablePort polizaPort;
    private PolizaVentaAdapter adapter;

    @BeforeEach
    void setUp() {
        cuentaRepo = mock(CuentaContableRepository.class);
        polizaPort = mock(PolizaContablePort.class);
        adapter = new PolizaVentaAdapter(cuentaRepo, polizaPort);
        when(polizaPort.generarPolizaDeEvento(any(), any(), any(), any(), any(), anyList()))
                .thenReturn(UUID.randomUUID());
    }

    private CuentaContable cuenta(String codigo, TipoCuentaContable tipo, NaturalezaCuenta nat) {
        return CuentaContable.crear(codigo, "Cuenta " + codigo, tipo, nat, "actor");
    }

    private void sembrarCuentasBasicas() {
        when(cuentaRepo.findByCodigo("105-01")).thenReturn(Optional.of(
                cuenta("105-01", TipoCuentaContable.ACTIVO, NaturalezaCuenta.DEUDORA)));
        when(cuentaRepo.findByCodigo("401-01")).thenReturn(Optional.of(
                cuenta("401-01", TipoCuentaContable.INGRESO, NaturalezaCuenta.ACREEDORA)));
        when(cuentaRepo.findByCodigo("208-01")).thenReturn(Optional.of(
                cuenta("208-01", TipoCuentaContable.PASIVO, NaturalezaCuenta.ACREEDORA)));
        when(cuentaRepo.findByCodigo("216-01")).thenReturn(Optional.of(
                cuenta("216-01", TipoCuentaContable.PASIVO, NaturalezaCuenta.DEUDORA)));
    }

    @SuppressWarnings("unchecked")
    private List<RenglonPolizaCommand> capturarRenglones() {
        ArgumentCaptor<List<RenglonPolizaCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(polizaPort).generarPolizaDeEvento(
                eq(FECHA), eq(TipoPoliza.INGRESO), any(), eq("factura_timbrada"), eq(FACTURA),
                captor.capture());
        return captor.getValue();
    }

    private static BigDecimal sumaCargos(List<RenglonPolizaCommand> renglones) {
        return renglones.stream()
                .map(r -> r.cargo() == null ? BigDecimal.ZERO : r.cargo())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumaAbonos(List<RenglonPolizaCommand> renglones) {
        return renglones.stream()
                .map(r -> r.abono() == null ? BigDecimal.ZERO : r.abono())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("Sin retenciones: el asiento balancea (cargo clientes = abono ingresos + abono IVA)")
    void asientoSinRetencionesBalancea() {
        sembrarCuentasBasicas();
        // subtotal 1000, IVA 160, retenciones 0, total 1160.
        Optional<UUID> resultado = adapter.generarPolizaVenta(
                FECHA, FACTURA, new BigDecimal("1000.00"), new BigDecimal("160.00"),
                BigDecimal.ZERO, new BigDecimal("1160.00"));

        assertThat(resultado).isPresent();
        List<RenglonPolizaCommand> renglones = capturarRenglones();
        assertThat(sumaCargos(renglones)).isEqualByComparingTo("1160.00");
        assertThat(sumaAbonos(renglones)).isEqualByComparingTo("1160.00");
    }

    @Test
    @DisplayName("Con retenciones: el asiento balancea (total + retenciones = subtotal + IVA)")
    void asientoConRetencionesBalancea() {
        sembrarCuentasBasicas();
        // subtotal 1000, IVA 160, retenciones 100, total 1060.
        Optional<UUID> resultado = adapter.generarPolizaVenta(
                FECHA, FACTURA, new BigDecimal("1000.00"), new BigDecimal("160.00"),
                new BigDecimal("100.00"), new BigDecimal("1060.00"));

        assertThat(resultado).isPresent();
        List<RenglonPolizaCommand> renglones = capturarRenglones();
        // Cargos: clientes 1060 + retenciones 100 = 1160; abonos: ingresos 1000 + IVA 160 = 1160.
        assertThat(sumaCargos(renglones)).isEqualByComparingTo("1160.00");
        assertThat(sumaAbonos(renglones)).isEqualByComparingTo("1160.00");
    }

    @Test
    @DisplayName("Catalogo incompleto (falta Clientes): se omite la poliza (empty) sin llamar al puerto")
    void catalogoIncompletoOmitePoliza() {
        // Solo ingresos disponible; falta 105-01 Clientes.
        when(cuentaRepo.findByCodigo("105-01")).thenReturn(Optional.empty());
        when(cuentaRepo.findByCodigo("401-01")).thenReturn(Optional.of(
                cuenta("401-01", TipoCuentaContable.INGRESO, NaturalezaCuenta.ACREEDORA)));

        Optional<UUID> resultado = adapter.generarPolizaVenta(
                FECHA, FACTURA, new BigDecimal("1000.00"), new BigDecimal("160.00"),
                BigDecimal.ZERO, new BigDecimal("1160.00"));

        assertThat(resultado).isEmpty();
        verify(polizaPort, never()).generarPolizaDeEvento(any(), any(), any(), any(), any(), anyList());
    }

    @Test
    @DisplayName("Periodo cerrado (el puerto lanza ReglaNegocio): se degrada a empty sin propagar")
    void periodoCerradoDegrada() {
        sembrarCuentasBasicas();
        when(polizaPort.generarPolizaDeEvento(any(), any(), any(), any(), any(), anyList()))
                .thenThrow(new ReglaNegocioException("El periodo 2026-03 esta cerrado."));

        Optional<UUID> resultado = adapter.generarPolizaVenta(
                FECHA, FACTURA, new BigDecimal("1000.00"), new BigDecimal("160.00"),
                BigDecimal.ZERO, new BigDecimal("1160.00"));

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Total no positivo: se omite la poliza (empty)")
    void totalNoPositivoOmite() {
        Optional<UUID> resultado = adapter.generarPolizaVenta(
                FECHA, FACTURA, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(resultado).isEmpty();
        verify(polizaPort, never()).generarPolizaDeEvento(any(), any(), any(), any(), any(), anyList());
    }
}
