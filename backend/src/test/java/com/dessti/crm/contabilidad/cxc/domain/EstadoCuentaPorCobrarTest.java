package com.dessti.crm.contabilidad.cxc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la maquina de estados pura de {@link EstadoCuentaPorCobrar}
 * (Req 36). Verifican los estados finales ({@code pagada}/{@code cancelada}), las
 * transiciones permitidas hacia {@code cancelada} usadas por la cancelacion en
 * cascada (FEAT-003) y el mapeo a/desde la etiqueta persistida. Imita el patron de
 * {@code EstadoFacturaProveedorTest}.
 */
class EstadoCuentaPorCobrarTest {

    @Test
    @DisplayName("pagada y cancelada son estados finales")
    void finales() {
        assertThat(EstadoCuentaPorCobrar.PAGADA.esFinal()).isTrue();
        assertThat(EstadoCuentaPorCobrar.CANCELADA.esFinal()).isTrue();
    }

    @Test
    @DisplayName("pendiente y parcial no son finales")
    void noFinales() {
        assertThat(EstadoCuentaPorCobrar.PENDIENTE.esFinal()).isFalse();
        assertThat(EstadoCuentaPorCobrar.PARCIAL.esFinal()).isFalse();
    }

    @Test
    @DisplayName("pendiente y parcial transitan a cancelada")
    void transicionACancelada() {
        assertThat(EstadoCuentaPorCobrar.PENDIENTE
                .puedeTransicionarA(EstadoCuentaPorCobrar.CANCELADA)).isTrue();
        assertThat(EstadoCuentaPorCobrar.PARCIAL
                .puedeTransicionarA(EstadoCuentaPorCobrar.CANCELADA)).isTrue();
    }

    @Test
    @DisplayName("desde un estado final no hay transicion a cancelada")
    void desdeFinalNoTransita() {
        assertThat(EstadoCuentaPorCobrar.PAGADA
                .puedeTransicionarA(EstadoCuentaPorCobrar.CANCELADA)).isFalse();
        assertThat(EstadoCuentaPorCobrar.CANCELADA
                .puedeTransicionarA(EstadoCuentaPorCobrar.CANCELADA)).isFalse();
    }

    @Test
    @DisplayName("mapeo a/desde la etiqueta de base de datos (round-trip)")
    void mapeoEtiqueta() {
        assertThat(EstadoCuentaPorCobrar.PENDIENTE.valorBd()).isEqualTo("pendiente");
        assertThat(EstadoCuentaPorCobrar.PARCIAL.valorBd()).isEqualTo("parcial");
        assertThat(EstadoCuentaPorCobrar.PAGADA.valorBd()).isEqualTo("pagada");
        assertThat(EstadoCuentaPorCobrar.CANCELADA.valorBd()).isEqualTo("cancelada");
        for (EstadoCuentaPorCobrar estado : EstadoCuentaPorCobrar.values()) {
            assertThat(EstadoCuentaPorCobrar.desdeValorBd(estado.valorBd())).isEqualTo(estado);
        }
        assertThat(EstadoCuentaPorCobrar.desdeValorBd("  CANCELADA "))
                .isEqualTo(EstadoCuentaPorCobrar.CANCELADA);
    }
}
