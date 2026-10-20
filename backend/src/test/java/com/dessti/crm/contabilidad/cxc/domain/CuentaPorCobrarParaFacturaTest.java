package com.dessti.crm.contabilidad.cxc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la fabrica {@link CuentaPorCobrar#paraFactura}: la sobrecarga de 5
 * argumentos fija la {@code fechaVencimiento} conservando saldo = total y estado
 * {@code PENDIENTE} (Req 36.1, 36.5); la de 4 argumentos deja la fecha de
 * vencimiento nula (delega en la de 5 con {@code null}). Dominio puro, sin Spring.
 */
@DisplayName("CuentaPorCobrar.paraFactura (Req 36.1, 36.5)")
class CuentaPorCobrarParaFacturaTest {

    private static final UUID FACTURA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ACTOR = "sistema";

    @Test
    @DisplayName("la fabrica de 5 args fija fechaVencimiento y mantiene saldo=total/PENDIENTE")
    void paraFacturaConVencimiento() {
        LocalDate vencimiento = LocalDate.of(2025, 2, 9);

        CuentaPorCobrar cxc = CuentaPorCobrar.paraFactura(
                FACTURA, CLIENTE, new BigDecimal("1160.00"), vencimiento, ACTOR);

        assertThat(cxc.getFechaVencimiento()).isEqualTo(vencimiento);
        assertThat(cxc.getSaldo()).isEqualByComparingTo(new BigDecimal("1160.00"));
        assertThat(cxc.getTotal()).isEqualByComparingTo(new BigDecimal("1160.00"));
        assertThat(cxc.getEstado()).isEqualTo(EstadoCuentaPorCobrar.PENDIENTE);
        assertThat(cxc.getFacturaId()).isEqualTo(FACTURA);
        assertThat(cxc.getClienteId()).isEqualTo(CLIENTE);
    }

    @Test
    @DisplayName("la fabrica de 4 args deja fechaVencimiento null")
    void paraFacturaSinVencimiento() {
        CuentaPorCobrar cxc = CuentaPorCobrar.paraFactura(
                FACTURA, CLIENTE, new BigDecimal("500.00"), ACTOR);

        assertThat(cxc.getFechaVencimiento()).isNull();
        assertThat(cxc.getSaldo()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(cxc.getEstado()).isEqualTo(EstadoCuentaPorCobrar.PENDIENTE);
    }
}
