package com.dessti.crm.facturacion.factura.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dessti.crm.contabilidad.cxc.application.CuentaPorCobrarPort;
import com.dessti.crm.facturacion.application.PacPort;
import com.dessti.crm.facturacion.application.ResultadoCancelacion;
import com.dessti.crm.facturacion.application.ResultadoTimbrado;
import com.dessti.crm.facturacion.factura.adapter.out.persistence.FacturaRepository;
import com.dessti.crm.facturacion.factura.domain.DatosFiscalesReceptor;
import com.dessti.crm.facturacion.factura.domain.Factura;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias (Mockito) de {@link ServicioFacturas} centradas en
 * {@code timbrar} y la regla fiscal de vencimiento (Req 36.1, 36.5): la fecha
 * de
 * vencimiento de la CxC se deriva de la fecha de emision (timbrado) mas los
 * dias de
 * credito del Cliente (AC-5), contado/PUE si 0 (AC-6), y se degrada a contado
 * con
 * auditoria cuando el Cliente no es accesible, concluyendo el timbrado con
 * exito
 * (AC-7). No arranca Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServicioFacturas.timbrar: vencimiento fiscal (Req 36.1, 36.5)")
class ServicioFacturasTest {

        private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
        private static final UUID COTIZACION = UUID.fromString("33333333-3333-3333-3333-333333333333");
        private static final UUID FOLIO_FISCAL = UUID.fromString("44444444-4444-4444-4444-444444444444");
        // Emision (timbrado) conocida: 2025-01-10 (UTC).
        private static final Instant FECHA_TIMBRADO = Instant.parse("2025-01-10T12:00:00Z");
        private static final LocalDate EMISION = LocalDate.of(2025, 1, 10);

        @Mock
        private FacturaRepository facturaRepository;
        @Mock
        private CotizacionAprobadaPort cotizacionAprobada;
        @Mock
        private OrdenFabricacionParaFacturaPort ordenFabricacionParaFactura;
        @Mock
        private PacPort pac;
        @Mock
        private AuditoriaPort auditoria;
        @Mock
        private CuentaPorCobrarPort cuentaPorCobrar;
        @Mock
        private ClienteCreditoPort clienteCredito;
        @Mock
        private PolizaVentaPort polizaVenta;

        private ServicioFacturas servicio;

        @BeforeEach
        void setUp() {
                servicio = new ServicioFacturas(
                                facturaRepository, cotizacionAprobada, ordenFabricacionParaFactura,
                                pac, auditoria, cuentaPorCobrar, clienteCredito, polizaVenta);
                TenantContext.set(TENANT);
        }

        @AfterEach
        void tearDown() {
                TenantContext.clear();
        }

        private Factura borrador() {
                DatosFiscalesReceptor datos = DatosFiscalesReceptor.validar(
                                "XAXX010101000", "Cliente Demo", "06000", "601", "G03");
                return Factura.emitirDesdeCotizacion(
                                COTIZACION, CLIENTE, datos, new BigDecimal("1000.00"), BigDecimal.ZERO, "sistema");
        }

        private void prepararTimbradoExitoso(Factura factura) {
                when(facturaRepository.findById(eq(factura.getId()))).thenReturn(Optional.of(factura));
                when(pac.timbrar(any())).thenReturn(
                                ResultadoTimbrado.exitoso(FOLIO_FISCAL, "SELLO-SAT", FECHA_TIMBRADO));
                when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> inv.getArgument(0));
                when(polizaVenta.generarPolizaVenta(any(), any(), any(), any(), any(), any()))
                                .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("dias=30, emision 2025-01-10 -> vencimiento 2025-02-09 (AC-5)")
        void vencimientoConCredito() {
                Factura factura = borrador();
                prepararTimbradoExitoso(factura);
                when(clienteCredito.diasCreditoDe(CLIENTE)).thenReturn(Optional.of(30));

                servicio.timbrar(factura.getId());

                ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
                verify(cuentaPorCobrar).registrarPorFacturaTimbrada(
                                eq(factura.getId()), eq(CLIENTE), any(BigDecimal.class), captor.capture());
                assertThat(captor.getValue()).isEqualTo(LocalDate.of(2025, 2, 9));
        }

        @Test
        @DisplayName("dias=0 -> vencimiento = emision (contado/PUE) (AC-6)")
        void vencimientoContado() {
                Factura factura = borrador();
                prepararTimbradoExitoso(factura);
                when(clienteCredito.diasCreditoDe(CLIENTE)).thenReturn(Optional.of(0));

                servicio.timbrar(factura.getId());

                ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
                verify(cuentaPorCobrar).registrarPorFacturaTimbrada(
                                eq(factura.getId()), eq(CLIENTE), any(BigDecimal.class), captor.capture());
                assertThat(captor.getValue()).isEqualTo(EMISION);
        }

        @Test
        @DisplayName("cliente no accesible -> vencimiento = emision, audita fallback y timbra con exito (AC-7)")
        void vencimientoFallbackContado() {
                Factura factura = borrador();
                prepararTimbradoExitoso(factura);
                when(clienteCredito.diasCreditoDe(CLIENTE)).thenReturn(Optional.empty());

                FacturaDto dto = servicio.timbrar(factura.getId());

                assertThat(dto.estado()).isEqualTo("timbrada");
                ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
                verify(cuentaPorCobrar).registrarPorFacturaTimbrada(
                                eq(factura.getId()), eq(CLIENTE), any(BigDecimal.class), captor.capture());
                assertThat(captor.getValue()).isEqualTo(EMISION);
                // Se audita el fallback a contado por cliente no encontrado.
                verify(auditoria).registrar(argThat(evento -> evento != null && evento.detalle() != null
                                && evento.detalle().contains("se asume contado")));
        }

        // ------------------------------------------------------------------
        // cancelar: cascada a CxC (Req 35.5) — FEAT-003
        // ------------------------------------------------------------------

        @Test
        @DisplayName("cancelar invoca cancelarPorFactura tras confirmar; la degradacion no rompe el CFDI (AC-11)")
        void cancelarPropagaACxC() {
                Factura factura = borrador();
                // Dejar la factura en 'timbrada' para poder cancelarla.
                factura.timbrar(FOLIO_FISCAL, "SELLO-SAT", FECHA_TIMBRADO, "sistema");
                when(facturaRepository.findById(eq(factura.getId()))).thenReturn(Optional.of(factura));
                when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> inv.getArgument(0));
                when(pac.cancelar(any())).thenReturn(ResultadoCancelacion.aceptada("ACUSE-1"));
                // Stub que degrada sin lanzar (comportamiento por defecto del mock: no-op).

                FacturaDto dto = servicio.cancelar(factura.getId(), "02");

                assertThat(dto.estado()).isEqualTo("cancelada");
                verify(cuentaPorCobrar).cancelarPorFactura(eq(factura.getId()));
        }
}
