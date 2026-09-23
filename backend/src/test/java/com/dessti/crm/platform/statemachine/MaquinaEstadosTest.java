package com.dessti.crm.platform.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del helper generico y puro {@link MaquinaEstados} (design.md,
 * seccion <em>State Machines</em>; semilla de la tarea 46.1). Verifican la
 * aceptacion/rechazo de transiciones, el tratamiento de estados finales y la
 * validacion de argumentos, con un enum de estados de prueba.
 */
class MaquinaEstadosTest {

    /** Estados de una maquina ficticia para las pruebas del helper. */
    private enum Estado { A, B, C, FIN }

    private static final MaquinaEstados<Estado> MAQUINA =
            MaquinaEstados.<Estado>builder(Estado.class)
                    .permitir(Estado.A, Estado.B, Estado.FIN)
                    .permitir(Estado.B, Estado.C, Estado.FIN)
                    .permitir(Estado.C, Estado.FIN)
                    .construir();

    @Test
    @DisplayName("Acepta exactamente las transiciones declaradas")
    void aceptaTransicionesDeclaradas() {
        assertThat(MAQUINA.puedeTransicionar(Estado.A, Estado.B)).isTrue();
        assertThat(MAQUINA.puedeTransicionar(Estado.A, Estado.FIN)).isTrue();
        assertThat(MAQUINA.puedeTransicionar(Estado.B, Estado.C)).isTrue();
        assertThat(MAQUINA.puedeTransicionar(Estado.A, Estado.C)).isFalse();
        assertThat(MAQUINA.puedeTransicionar(Estado.C, Estado.A)).isFalse();
    }

    @Test
    @DisplayName("Un estado sin salidas es final y no admite transiciones")
    void estadoFinalSinSalidas() {
        assertThat(MAQUINA.esFinal(Estado.FIN)).isTrue();
        assertThat(MAQUINA.esFinal(Estado.A)).isFalse();
        for (Estado destino : Estado.values()) {
            assertThat(MAQUINA.puedeTransicionar(Estado.FIN, destino)).isFalse();
        }
    }

    @Test
    @DisplayName("transicionesDesde devuelve un conjunto inmutable")
    void transicionesInmutables() {
        assertThat(MAQUINA.transicionesDesde(Estado.A)).containsExactlyInAnyOrder(Estado.B, Estado.FIN);
        assertThatThrownBy(() -> MAQUINA.transicionesDesde(Estado.A).add(Estado.C))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Los argumentos nulos y builder invalido se rechazan")
    void validaArgumentos() {
        assertThatThrownBy(() -> MAQUINA.puedeTransicionar(null, Estado.A))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MaquinaEstados.builder(Estado.class).permitir(Estado.A))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
