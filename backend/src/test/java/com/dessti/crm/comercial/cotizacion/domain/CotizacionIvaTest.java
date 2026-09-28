package com.dessti.crm.comercial.cotizacion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas del desglose fiscal CFDI del agregado {@link Cotizacion} (V80): IVA por
 * partida (tasas mixtas), descuento por partida y global, y retenciones ISR/IVA.
 * Verifica el orden de calculo: subtotal = Σ base neta; IVA = Σ IVA por partida;
 * total = subtotal - descuento global + IVA - retenciones. Aritmetica escala 2
 * half-up. No arranca Spring ni BD.
 */
class CotizacionIvaTest {

    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ACTOR = "ventas";

    private static PartidaCotizacion partida(int cantidad, String precio, String descuento, TasaIva tasa) {
        return PartidaCotizacion.crear(null, "Anuncio luminoso", cantidad, new BigDecimal(precio),
                new BigDecimal(descuento), tasa, ACTOR);
    }

    @Test
    @DisplayName("IVA 16% por defecto: total = base + IVA")
    void ivaDieciseisPorDefecto() {
        // 2 * 100.00 = 200.00 base; IVA 16% = 32.00; total = 232.00
        Cotizacion c = Cotizacion.crear(CLIENTE,
                List.of(partida(2, "100.00", "0.00", TasaIva.DIECISEIS)), ACTOR);
        assertThat(c.getSubtotal()).isEqualByComparingTo("200.00");
        assertThat(c.getIva()).isEqualByComparingTo("32.00");
        assertThat(c.getTotal()).isEqualByComparingTo("232.00");
    }

    @Test
    @DisplayName("descuento por partida reduce la base y el IVA")
    void descuentoPorPartida() {
        // bruto 200.00 - descuento 50.00 = base 150.00; IVA 16% = 24.00; total 174.00
        Cotizacion c = Cotizacion.crear(CLIENTE,
                List.of(partida(2, "100.00", "50.00", TasaIva.DIECISEIS)), ACTOR);
        assertThat(c.getSubtotal()).isEqualByComparingTo("150.00");
        assertThat(c.getIva()).isEqualByComparingTo("24.00");
        assertThat(c.getTotal()).isEqualByComparingTo("174.00");
    }

    @Test
    @DisplayName("tasas mixtas: el IVA se calcula por partida sobre su base")
    void tasasMixtas() {
        // Partida A: 1 * 100.00, IVA 16% -> base 100.00, IVA 16.00
        // Partida B: 1 * 100.00, exento  -> base 100.00, IVA 0.00
        Cotizacion c = Cotizacion.crear(CLIENTE, List.of(
                partida(1, "100.00", "0.00", TasaIva.DIECISEIS),
                partida(1, "100.00", "0.00", TasaIva.EXENTO)), ACTOR);
        assertThat(c.getSubtotal()).isEqualByComparingTo("200.00");
        assertThat(c.getIva()).isEqualByComparingTo("16.00");
        assertThat(c.getTotal()).isEqualByComparingTo("216.00");
    }

    @Test
    @DisplayName("descuento global y retenciones ajustan el total (orden CFDI)")
    void descuentoGlobalYRetenciones() {
        // base 1000.00; IVA 16% = 160.00
        Cotizacion c = Cotizacion.crear(CLIENTE,
                List.of(partida(10, "100.00", "0.00", TasaIva.DIECISEIS)), ACTOR);
        // descuento global 100.00 -> base gravable 900.00
        // ret ISR 100.00, ret IVA 106.67
        c.aplicarAjustesFiscales(new BigDecimal("100.00"), new BigDecimal("100.00"),
                new BigDecimal("106.67"), ACTOR);
        // total = 900.00 + 160.00 - 100.00 - 106.67 = 853.33
        assertThat(c.getSubtotal()).isEqualByComparingTo("1000.00");
        assertThat(c.getIva()).isEqualByComparingTo("160.00");
        assertThat(c.getDescuentoGlobal()).isEqualByComparingTo("100.00");
        assertThat(c.getRetencionIsr()).isEqualByComparingTo("100.00");
        assertThat(c.getRetencionIva()).isEqualByComparingTo("106.67");
        assertThat(c.getTotal()).isEqualByComparingTo("853.33");
    }

    @Test
    @DisplayName("el descuento de partida no puede exceder su importe bruto (422)")
    void descuentoPartidaExcesivoRechaza() {
        assertThatThrownBy(() -> partida(1, "100.00", "150.00", TasaIva.DIECISEIS))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("las retenciones no pueden dejar el total negativo (se acota a 0)")
    void totalNoNegativo() {
        Cotizacion c = Cotizacion.crear(CLIENTE,
                List.of(partida(1, "100.00", "0.00", TasaIva.EXENTO)), ACTOR);
        // base 100.00, IVA 0; retencion ISR 500.00 -> total teorico -400 -> acotado a 0
        c.aplicarAjustesFiscales(BigDecimal.ZERO, new BigDecimal("500.00"), BigDecimal.ZERO, ACTOR);
        assertThat(c.getTotal()).isEqualByComparingTo("0.00");
    }
}
