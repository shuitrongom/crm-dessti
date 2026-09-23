package com.dessti.crm.comercial.cotizacion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la maquina de estados <strong>pura</strong> de la
 * Cotizacion (Req 6.6, 6.7). Ejercen la funcion
 * {@link EstadoCotizacion#puedeTransicionarA(EstadoCotizacion)} de forma
 * exhaustiva sobre todos los pares de estados, verificando que se acepta
 * exactamente el conjunto de transiciones definidas y se rechaza cualquier otra,
 * incluidas todas las que parten de un estado final.
 */
class EstadoCotizacionTest {

    /** Transiciones permitidas exactas del Req 6.6. */
    private static Set<EstadoCotizacion> destinosPermitidos(EstadoCotizacion actual) {
        return switch (actual) {
            case BORRADOR -> EnumSet.of(EstadoCotizacion.ENVIADA);
            case ENVIADA -> EnumSet.of(EstadoCotizacion.APROBADA, EstadoCotizacion.RECHAZADA);
            case APROBADA, RECHAZADA -> EnumSet.noneOf(EstadoCotizacion.class);
        };
    }

    @Test
    @DisplayName("La tabla acepta exactamente las transiciones del Req 6.6 y rechaza el resto")
    void aceptaExactamenteLasTransicionesDefinidas() {
        for (EstadoCotizacion actual : EstadoCotizacion.values()) {
            Set<EstadoCotizacion> permitidas = destinosPermitidos(actual);
            for (EstadoCotizacion destino : EstadoCotizacion.values()) {
                boolean esperado = permitidas.contains(destino);
                assertThat(actual.puedeTransicionarA(destino))
                        .as("%s -> %s", actual.valorBd(), destino.valorBd())
                        .isEqualTo(esperado);
            }
        }
    }

    @Test
    @DisplayName("El flujo valido borrador -> enviada -> {aprobada|rechazada} se acepta (Req 6.6)")
    void flujoValido() {
        assertThat(EstadoCotizacion.BORRADOR.puedeTransicionarA(EstadoCotizacion.ENVIADA)).isTrue();
        assertThat(EstadoCotizacion.ENVIADA.puedeTransicionarA(EstadoCotizacion.APROBADA)).isTrue();
        assertThat(EstadoCotizacion.ENVIADA.puedeTransicionarA(EstadoCotizacion.RECHAZADA)).isTrue();
    }

    @Test
    @DisplayName("Los estados finales 'aprobada' y 'rechazada' no admiten transiciones (Req 6.7)")
    void estadosFinalesSinTransiciones() {
        assertThat(EstadoCotizacion.APROBADA.esFinal()).isTrue();
        assertThat(EstadoCotizacion.RECHAZADA.esFinal()).isTrue();
        for (EstadoCotizacion destino : EstadoCotizacion.values()) {
            assertThat(EstadoCotizacion.APROBADA.puedeTransicionarA(destino))
                    .as("aprobada -> %s", destino.valorBd()).isFalse();
            assertThat(EstadoCotizacion.RECHAZADA.puedeTransicionarA(destino))
                    .as("rechazada -> %s", destino.valorBd()).isFalse();
        }
    }

    @Test
    @DisplayName("Transiciones que saltan pasos o retroceden son invalidas (Req 6.7)")
    void transicionesInvalidasComunes() {
        assertThat(EstadoCotizacion.BORRADOR.puedeTransicionarA(EstadoCotizacion.APROBADA)).isFalse();
        assertThat(EstadoCotizacion.BORRADOR.puedeTransicionarA(EstadoCotizacion.RECHAZADA)).isFalse();
        assertThat(EstadoCotizacion.ENVIADA.puedeTransicionarA(EstadoCotizacion.BORRADOR)).isFalse();
        assertThat(EstadoCotizacion.BORRADOR.puedeTransicionarA(EstadoCotizacion.BORRADOR)).isFalse();
    }

    @Test
    @DisplayName("El valor de BD y su inversa son consistentes")
    void valorBdConsistente() {
        for (EstadoCotizacion estado : EstadoCotizacion.values()) {
            assertThat(EstadoCotizacion.desdeValorBd(estado.valorBd())).isEqualTo(estado);
        }
        assertThat(EstadoCotizacion.desdeValorBd("  BORRADOR  ")).isEqualTo(EstadoCotizacion.BORRADOR);
    }
}
