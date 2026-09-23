package com.dessti.crm.vertical.anuncios.pruebadiseno.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la maquina de estados pura de {@link EstadoPruebaDiseno}
 * (Req 15.2, 15.3, 15.4). Verifican las transiciones permitidas
 * ({@code pendiente -> aprobada|rechazada}), los estados finales y el mapeo
 * a/desde la etiqueta persistida.
 */
class EstadoPruebaDisenoTest {

    @Test
    @DisplayName("pendiente puede transitar a aprobada y a rechazada (Req 15.2, 15.3)")
    void pendienteTransiciones() {
        assertThat(EstadoPruebaDiseno.PENDIENTE.puedeTransicionarA(EstadoPruebaDiseno.APROBADA)).isTrue();
        assertThat(EstadoPruebaDiseno.PENDIENTE.puedeTransicionarA(EstadoPruebaDiseno.RECHAZADA)).isTrue();
    }

    @Test
    @DisplayName("aprobada y rechazada son finales, sin transiciones salientes (Req 15.4)")
    void estadosFinales() {
        assertThat(EstadoPruebaDiseno.APROBADA.esFinal()).isTrue();
        assertThat(EstadoPruebaDiseno.RECHAZADA.esFinal()).isTrue();
        assertThat(EstadoPruebaDiseno.PENDIENTE.esFinal()).isFalse();
        assertThat(EstadoPruebaDiseno.APROBADA.puedeTransicionarA(EstadoPruebaDiseno.RECHAZADA)).isFalse();
        assertThat(EstadoPruebaDiseno.RECHAZADA.puedeTransicionarA(EstadoPruebaDiseno.APROBADA)).isFalse();
        assertThat(EstadoPruebaDiseno.APROBADA.puedeTransicionarA(EstadoPruebaDiseno.APROBADA)).isFalse();
    }

    @Test
    @DisplayName("valorBd y desdeValorBd son inversos e insensibles a mayusculas/espacios")
    void mapeoEtiqueta() {
        assertThat(EstadoPruebaDiseno.PENDIENTE.valorBd()).isEqualTo("pendiente");
        assertThat(EstadoPruebaDiseno.APROBADA.valorBd()).isEqualTo("aprobada");
        assertThat(EstadoPruebaDiseno.RECHAZADA.valorBd()).isEqualTo("rechazada");
        assertThat(EstadoPruebaDiseno.desdeValorBd("  APROBADA ")).isEqualTo(EstadoPruebaDiseno.APROBADA);
    }

    @Test
    @DisplayName("desdeValorBd rechaza nulo y etiquetas desconocidas")
    void desdeValorBdInvalido() {
        assertThatThrownBy(() -> EstadoPruebaDiseno.desdeValorBd(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EstadoPruebaDiseno.desdeValorBd("inexistente"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
