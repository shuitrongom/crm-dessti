package com.dessti.crm.comercial.cotizacion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 3-bis: Guarda de
 * vigencia de la Cotizacion</strong> (Req 6.11).
 *
 * <p>Ejerce la pieza pura de dominio {@link Cotizacion#estaVencida(LocalDate)}, sin
 * base de datos ni Spring. La property comprueba universalmente el bicondicional:
 * una Cotizacion con fecha de vigencia esta vencida <em>si y solo si</em>
 * {@code hoy} es posterior a {@code valido_hasta} (frontera inclusiva: el propio dia
 * de vigencia aun es valido); una Cotizacion sin fecha de vigencia nunca esta
 * vencida.</p>
 */
class GuardaVigenciaCotizacionPropertyTest {

    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ACTOR = "ventas";
    /** Fecha base de referencia; los desfases generados se cuentan respecto a ella. */
    private static final LocalDate BASE = LocalDate.of(2026, 1, 15);

    /** Cotizacion en borrador con una partida y la fecha de vigencia indicada. */
    private static Cotizacion conVigencia(LocalDate validoHasta) {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE,
                List.of(PartidaCotizacion.crear(null, "Base", 1, new BigDecimal("100.00"),
                        BigDecimal.ZERO, TasaIva.EXENTO, ACTOR)),
                ACTOR);
        // fechaEmision muy anterior (mas que el desfase minimo generado, -400 dias)
        // para no violar la invariante valido_hasta >= fecha_emision en ningun caso.
        cotizacion.aplicarDatosDescriptivos(BASE.minusYears(3), validoHasta, null, null, "MXN", ACTOR);
        return cotizacion;
    }

    // Feature: crm-anuncios-luminosos, Property 3-bis: una Cotizacion con vigencia está vencida sii hoy es posterior a valido_hasta (frontera inclusiva).
    @Property(tries = 500)
    void vencidaSiiHoyPosteriorAValidoHasta(
            @ForAll @IntRange(min = -400, max = 400) int desfaseVigencia,
            @ForAll @IntRange(min = -400, max = 400) int desfaseHoy) {

        LocalDate validoHasta = BASE.plusDays(desfaseVigencia);
        LocalDate hoy = BASE.plusDays(desfaseHoy);
        Cotizacion cotizacion = conVigencia(validoHasta);

        boolean esperado = hoy.isAfter(validoHasta);
        assertThat(cotizacion.estaVencida(hoy)).isEqualTo(esperado);
        // Frontera inclusiva: el propio dia de vigencia no esta vencido.
        assertThat(cotizacion.estaVencida(validoHasta)).isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 3-bis: una Cotizacion sin fecha de vigencia nunca está vencida.
    @Property(tries = 200)
    void sinVigenciaNuncaVence(@ForAll @IntRange(min = -400, max = 400) int desfaseHoy) {
        Cotizacion cotizacion = conVigencia(null);
        assertThat(cotizacion.estaVencida(BASE.plusDays(desfaseHoy))).isFalse();
    }

    @Provide
    Arbitrary<Integer> desfases() {
        return Arbitraries.integers().between(-400, 400);
    }
}
