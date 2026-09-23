package com.dessti.crm.operacion.produccion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la maquina de estados pura {@link EstadoOrdenFabricacion}
 * (Req 7.5, 7.6). Verifican las transiciones permitidas, el rechazo de toda otra
 * transicion (incluidas las que parten de un estado final), los estados finales y
 * la traduccion de/hacia la etiqueta ASCII persistida (incluida {@code en_produccion}
 * sin acento).
 */
class EstadoOrdenFabricacionTest {

    @Test
    @DisplayName("las transiciones permitidas del Req 7.5 se aceptan")
    void transicionesPermitidas() {
        assertThat(EstadoOrdenFabricacion.PENDIENTE
                .puedeTransicionarA(EstadoOrdenFabricacion.EN_PRODUCCION)).isTrue();
        assertThat(EstadoOrdenFabricacion.PENDIENTE
                .puedeTransicionarA(EstadoOrdenFabricacion.CANCELADA)).isTrue();
        assertThat(EstadoOrdenFabricacion.EN_PRODUCCION
                .puedeTransicionarA(EstadoOrdenFabricacion.TERMINADA)).isTrue();
        assertThat(EstadoOrdenFabricacion.EN_PRODUCCION
                .puedeTransicionarA(EstadoOrdenFabricacion.CANCELADA)).isTrue();
    }

    @Test
    @DisplayName("transiciones no incluidas se rechazan (Req 7.6)")
    void transicionesNoPermitidas() {
        // Saltos no permitidos.
        assertThat(EstadoOrdenFabricacion.PENDIENTE
                .puedeTransicionarA(EstadoOrdenFabricacion.TERMINADA)).isFalse();
        // Retrocesos.
        assertThat(EstadoOrdenFabricacion.EN_PRODUCCION
                .puedeTransicionarA(EstadoOrdenFabricacion.PENDIENTE)).isFalse();
        // A si mismo (no declarada).
        assertThat(EstadoOrdenFabricacion.PENDIENTE
                .puedeTransicionarA(EstadoOrdenFabricacion.PENDIENTE)).isFalse();
    }

    @Test
    @DisplayName("los estados finales no admiten ninguna transicion posterior (Req 7.6)")
    void estadosFinalesSinSalida() {
        assertThat(EstadoOrdenFabricacion.TERMINADA.esFinal()).isTrue();
        assertThat(EstadoOrdenFabricacion.CANCELADA.esFinal()).isTrue();
        for (EstadoOrdenFabricacion destino : EstadoOrdenFabricacion.values()) {
            assertThat(EstadoOrdenFabricacion.TERMINADA.puedeTransicionarA(destino)).isFalse();
            assertThat(EstadoOrdenFabricacion.CANCELADA.puedeTransicionarA(destino)).isFalse();
        }
    }

    @Test
    @DisplayName("pendiente y en_produccion no son finales")
    void estadosNoFinales() {
        assertThat(EstadoOrdenFabricacion.PENDIENTE.esFinal()).isFalse();
        assertThat(EstadoOrdenFabricacion.EN_PRODUCCION.esFinal()).isFalse();
    }

    @Test
    @DisplayName("valorBd usa etiquetas ASCII; en_produccion sin acento")
    void etiquetasAscii() {
        assertThat(EstadoOrdenFabricacion.PENDIENTE.valorBd()).isEqualTo("pendiente");
        assertThat(EstadoOrdenFabricacion.EN_PRODUCCION.valorBd()).isEqualTo("en_produccion");
        assertThat(EstadoOrdenFabricacion.TERMINADA.valorBd()).isEqualTo("terminada");
        assertThat(EstadoOrdenFabricacion.CANCELADA.valorBd()).isEqualTo("cancelada");
    }

    @Test
    @DisplayName("desdeValorBd es la inversa de valorBd e ignora mayusculas/espacios")
    void desdeValorBd() {
        for (EstadoOrdenFabricacion estado : EstadoOrdenFabricacion.values()) {
            assertThat(EstadoOrdenFabricacion.desdeValorBd(estado.valorBd())).isEqualTo(estado);
        }
        assertThat(EstadoOrdenFabricacion.desdeValorBd("  EN_PRODUCCION "))
                .isEqualTo(EstadoOrdenFabricacion.EN_PRODUCCION);
    }

    @Test
    @DisplayName("desdeValorBd rechaza nulo y etiquetas desconocidas")
    void desdeValorBdInvalido() {
        assertThatThrownBy(() -> EstadoOrdenFabricacion.desdeValorBd(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EstadoOrdenFabricacion.desdeValorBd("en_producción"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EstadoOrdenFabricacion.desdeValorBd("desconocido"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
