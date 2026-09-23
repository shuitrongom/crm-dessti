package com.dessti.crm.comercial.cotizacion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias del agregado {@link Cotizacion} y de {@link PartidaCotizacion}
 * (Req 6). Cubren el calculo half-up de subtotales y total (Req 6.3, 6.5;
 * Property 2), el rechazo de cantidad/precio fuera de rango (Req 6.4; Property 3),
 * las cotas de 1..500 partidas (Req 6.1, 6.2), la maquina de estados (Req 6.6,
 * 6.7) y la guarda de >=1 partida para enviar. No arrancan Spring ni BD.
 */
class CotizacionTest {

    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ACTOR = "ventas";

    private static PartidaCotizacion partida(int cantidad, String precio) {
        return PartidaCotizacion.crear(null, "Anuncio luminoso", cantidad, new BigDecimal(precio), ACTOR);
    }

    @Test
    @DisplayName("crear fija estado 'borrador' y calcula subtotales y total half-up (Req 6.1, 6.3, 6.5)")
    void crearCalculaTotalesHalfUp() {
        // 3 * 10.125 -> 30.375 -> half-up 30.38 ; 2 * 5.005 -> 10.01 -> 10.01
        List<PartidaCotizacion> partidas = List.of(
                partida(3, "10.125"),
                partida(2, "5.005"));
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE, partidas, ACTOR);

        assertThat(cotizacion.getEstado()).isEqualTo(EstadoCotizacion.BORRADOR);
        // precio_unitario se normaliza half-up a escala 2 ANTES de multiplicar:
        //   10.125 -> 10.13 ; 3 * 10.13 = 30.39
        //    5.005 ->  5.01 ; 2 *  5.01 = 10.02
        assertThat(cotizacion.getPartidas().get(0).getPrecioUnitario()).isEqualByComparingTo("10.13");
        assertThat(cotizacion.getPartidas().get(0).getSubtotal()).isEqualByComparingTo("30.39");
        assertThat(cotizacion.getPartidas().get(1).getPrecioUnitario()).isEqualByComparingTo("5.01");
        assertThat(cotizacion.getPartidas().get(1).getSubtotal()).isEqualByComparingTo("10.02");
        // total = round(sum subtotales, 2) = 40.41
        assertThat(cotizacion.getTotal()).isEqualByComparingTo("40.41");
        assertThat(cotizacion.getSubtotal()).isEqualByComparingTo("40.41");
        assertThat(cotizacion.getTotal().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("total == suma de subtotales de las partidas (Property 2)")
    void totalIgualSumaDeSubtotales() {
        List<PartidaCotizacion> partidas = List.of(
                partida(7, "123.45"),
                partida(1, "0.01"),
                partida(999, "1000.00"));
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE, partidas, ACTOR);

        BigDecimal suma = cotizacion.getPartidas().stream()
                .map(PartidaCotizacion::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(cotizacion.getTotal()).isEqualByComparingTo(suma);
    }

    @Test
    @DisplayName("crear sin partidas se rechaza (Req 6.2 -> 422)")
    void crearSinPartidasRechaza() {
        assertThatThrownBy(() -> Cotizacion.crear(CLIENTE, List.of(), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear con mas de 500 partidas se rechaza (Req 6.2 -> 422)")
    void crearMasDe500PartidasRechaza() {
        List<PartidaCotizacion> muchas = new ArrayList<>();
        IntStream.rangeClosed(1, 501).forEach(i -> muchas.add(partida(1, "1.00")));
        assertThatThrownBy(() -> Cotizacion.crear(CLIENTE, muchas, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear con exactamente 500 partidas se acepta (limite Req 6.1)")
    void crearCon500PartidasSeAcepta() {
        List<PartidaCotizacion> partidas = new ArrayList<>();
        IntStream.rangeClosed(1, 500).forEach(i -> partidas.add(partida(1, "1.00")));
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE, partidas, ACTOR);
        assertThat(cotizacion.getPartidas()).hasSize(500);
        assertThat(cotizacion.getTotal()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("partida con cantidad fuera de [1, 999999] se rechaza y no calcula subtotal (Req 6.4; Property 3)")
    void partidaCantidadFueraDeRango() {
        assertThatThrownBy(() -> partida(0, "10.00")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> partida(1_000_000, "10.00")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("partida con precio fuera de [0.01, 999999999.99] se rechaza (Req 6.4; Property 3)")
    void partidaPrecioFueraDeRango() {
        assertThatThrownBy(() -> partida(1, "0.00")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> partida(1, "1000000000.00")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("cambiarEstado sigue borrador -> enviada -> aprobada (Req 6.6)")
    void flujoAprobacionValido() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE, List.of(partida(1, "100.00")), ACTOR);
        cotizacion.cambiarEstado(EstadoCotizacion.ENVIADA, ACTOR);
        assertThat(cotizacion.getEstado()).isEqualTo(EstadoCotizacion.ENVIADA);
        cotizacion.cambiarEstado(EstadoCotizacion.APROBADA, ACTOR);
        assertThat(cotizacion.getEstado()).isEqualTo(EstadoCotizacion.APROBADA);
    }

    @Test
    @DisplayName("cambiarEstado borrador -> enviada -> rechazada es valido (Req 6.6)")
    void flujoRechazoValido() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE, List.of(partida(1, "100.00")), ACTOR);
        cotizacion.cambiarEstado(EstadoCotizacion.ENVIADA, ACTOR);
        cotizacion.cambiarEstado(EstadoCotizacion.RECHAZADA, ACTOR);
        assertThat(cotizacion.getEstado()).isEqualTo(EstadoCotizacion.RECHAZADA);
    }

    @Test
    @DisplayName("cambiarEstado con transicion invalida lanza 409 y conserva el estado (Req 6.7)")
    void transicionInvalida409() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE, List.of(partida(1, "100.00")), ACTOR);
        assertThatThrownBy(() -> cotizacion.cambiarEstado(EstadoCotizacion.APROBADA, ACTOR))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(cotizacion.getEstado()).isEqualTo(EstadoCotizacion.BORRADOR);
    }

    @Test
    @DisplayName("transicion desde un estado final se rechaza con 409 (Req 6.7)")
    void transicionDesdeFinalRechaza() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE, List.of(partida(1, "100.00")), ACTOR);
        cotizacion.cambiarEstado(EstadoCotizacion.ENVIADA, ACTOR);
        cotizacion.cambiarEstado(EstadoCotizacion.APROBADA, ACTOR);
        assertThatThrownBy(() -> cotizacion.cambiarEstado(EstadoCotizacion.RECHAZADA, ACTOR))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(cotizacion.getEstado()).isEqualTo(EstadoCotizacion.APROBADA);
    }

    @Test
    @DisplayName("un cascaron de conversion sin partidas no puede enviarse (guarda borrador -> enviada)")
    void cascaronSinPartidasNoSeEnvia() {
        UUID oportunidad = UUID.randomUUID();
        Cotizacion cascaron = Cotizacion.crearCascaronConversion(oportunidad, CLIENTE, ACTOR);
        assertThat(cascaron.getEstado()).isEqualTo(EstadoCotizacion.BORRADOR);
        assertThat(cascaron.getPartidas()).isEmpty();
        assertThat(cascaron.getOportunidadId()).isEqualTo(oportunidad);
        assertThat(cascaron.getTotal()).isEqualByComparingTo("0.00");

        assertThatThrownBy(() -> cascaron.cambiarEstado(EstadoCotizacion.ENVIADA, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(cascaron.getEstado()).isEqualTo(EstadoCotizacion.BORRADOR);
    }

    @Test
    @DisplayName("agregarPartida recalcula el total y solo se permite en 'borrador'")
    void agregarPartidaRecalculaYRestringe() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE, List.of(partida(1, "100.00")), ACTOR);
        cotizacion.agregarPartida(partida(2, "50.00"), ACTOR);
        assertThat(cotizacion.getPartidas()).hasSize(2);
        assertThat(cotizacion.getTotal()).isEqualByComparingTo("200.00");

        cotizacion.cambiarEstado(EstadoCotizacion.ENVIADA, ACTOR);
        assertThatThrownBy(() -> cotizacion.agregarPartida(partida(1, "10.00"), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
