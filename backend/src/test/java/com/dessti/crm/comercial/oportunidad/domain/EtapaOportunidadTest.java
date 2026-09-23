package com.dessti.crm.comercial.oportunidad.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la maquina de estados <strong>pura</strong> del pipeline
 * de Oportunidades (Req 14.3, 14.4). Ejercen la funcion
 * {@link EtapaOportunidad#puedeTransicionarA(EtapaOportunidad)} de forma
 * exhaustiva sobre todos los pares de etapas, verificando que se acepta
 * exactamente el conjunto de transiciones definidas y se rechaza cualquier otra,
 * incluidas todas las que parten de una etapa final. Esta funcion es la semilla
 * que consolidara la tarea 46.1 y ejercera la Property 5 en la 46.2.
 */
class EtapaOportunidadTest {

    /** Transiciones permitidas exactas del Req 14.3 (avance lineal + '-> perdido'). */
    private static Set<EtapaOportunidad> destinosPermitidos(EtapaOportunidad actual) {
        return switch (actual) {
            case NUEVO -> EnumSet.of(EtapaOportunidad.CALIFICADO, EtapaOportunidad.PERDIDO);
            case CALIFICADO -> EnumSet.of(EtapaOportunidad.PROPUESTA, EtapaOportunidad.PERDIDO);
            case PROPUESTA -> EnumSet.of(EtapaOportunidad.NEGOCIACION, EtapaOportunidad.PERDIDO);
            case NEGOCIACION -> EnumSet.of(EtapaOportunidad.GANADO, EtapaOportunidad.PERDIDO);
            case GANADO, PERDIDO -> EnumSet.noneOf(EtapaOportunidad.class);
        };
    }

    @Test
    @DisplayName("La tabla acepta exactamente las transiciones del Req 14.3 y rechaza el resto")
    void aceptaExactamenteLasTransicionesDefinidas() {
        for (EtapaOportunidad actual : EtapaOportunidad.values()) {
            Set<EtapaOportunidad> permitidas = destinosPermitidos(actual);
            for (EtapaOportunidad destino : EtapaOportunidad.values()) {
                boolean esperado = permitidas.contains(destino);
                assertThat(actual.puedeTransicionarA(destino))
                        .as("%s -> %s", actual.valorBd(), destino.valorBd())
                        .isEqualTo(esperado);
            }
        }
    }

    @Test
    @DisplayName("El avance lineal del pipeline es valido (Req 14.3)")
    void avanceLinealValido() {
        assertThat(EtapaOportunidad.NUEVO.puedeTransicionarA(EtapaOportunidad.CALIFICADO)).isTrue();
        assertThat(EtapaOportunidad.CALIFICADO.puedeTransicionarA(EtapaOportunidad.PROPUESTA)).isTrue();
        assertThat(EtapaOportunidad.PROPUESTA.puedeTransicionarA(EtapaOportunidad.NEGOCIACION)).isTrue();
        assertThat(EtapaOportunidad.NEGOCIACION.puedeTransicionarA(EtapaOportunidad.GANADO)).isTrue();
    }

    @Test
    @DisplayName("Desde cualquier etapa no final se puede transitar a 'perdido' (Req 14.3)")
    void cualquierNoFinalAPerdido() {
        assertThat(EtapaOportunidad.NUEVO.puedeTransicionarA(EtapaOportunidad.PERDIDO)).isTrue();
        assertThat(EtapaOportunidad.CALIFICADO.puedeTransicionarA(EtapaOportunidad.PERDIDO)).isTrue();
        assertThat(EtapaOportunidad.PROPUESTA.puedeTransicionarA(EtapaOportunidad.PERDIDO)).isTrue();
        assertThat(EtapaOportunidad.NEGOCIACION.puedeTransicionarA(EtapaOportunidad.PERDIDO)).isTrue();
    }

    @Test
    @DisplayName("Las etapas finales 'ganado' y 'perdido' no admiten transiciones (Req 14.4)")
    void etapasFinalesSinTransiciones() {
        assertThat(EtapaOportunidad.GANADO.esFinal()).isTrue();
        assertThat(EtapaOportunidad.PERDIDO.esFinal()).isTrue();
        for (EtapaOportunidad destino : EtapaOportunidad.values()) {
            assertThat(EtapaOportunidad.GANADO.puedeTransicionarA(destino))
                    .as("ganado -> %s", destino.valorBd()).isFalse();
            assertThat(EtapaOportunidad.PERDIDO.puedeTransicionarA(destino))
                    .as("perdido -> %s", destino.valorBd()).isFalse();
        }
    }

    @Test
    @DisplayName("Transiciones que saltan etapas o retroceden son invalidas (Req 14.4)")
    void transicionesInvalidasComunes() {
        assertThat(EtapaOportunidad.NUEVO.puedeTransicionarA(EtapaOportunidad.PROPUESTA)).isFalse();
        assertThat(EtapaOportunidad.NUEVO.puedeTransicionarA(EtapaOportunidad.GANADO)).isFalse();
        assertThat(EtapaOportunidad.CALIFICADO.puedeTransicionarA(EtapaOportunidad.NUEVO)).isFalse();
        assertThat(EtapaOportunidad.PROPUESTA.puedeTransicionarA(EtapaOportunidad.GANADO)).isFalse();
        // Una etapa no transita a si misma salvo declaracion explicita.
        assertThat(EtapaOportunidad.NUEVO.puedeTransicionarA(EtapaOportunidad.NUEVO)).isFalse();
    }

    @Test
    @DisplayName("El valor de BD y su inversa son consistentes")
    void valorBdConsistente() {
        for (EtapaOportunidad etapa : EtapaOportunidad.values()) {
            assertThat(EtapaOportunidad.desdeValorBd(etapa.valorBd())).isEqualTo(etapa);
        }
        assertThat(EtapaOportunidad.desdeValorBd("  NEGOCIACION  ")).isEqualTo(EtapaOportunidad.NEGOCIACION);
    }
}
