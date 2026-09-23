package com.dessti.crm.vertical.anuncios.levantamiento.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.vertical.anuncios.levantamiento.domain.EstadoLevantamiento;

/**
 * Pruebas unitarias del {@link LevantamientoCompletadoAdapter} (guarda del Req 16.5
 * que consumira el bloque 22). Verifican que el puerto devuelve {@code true} solo
 * cuando el repositorio confirma un Levantamiento {@code completado}, y que trata
 * los identificadores nulos como no completados.
 */
class LevantamientoCompletadoAdapterTest {

    private static final UUID SITIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LEV = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private LevantamientoSitioRepository repositorio;
    private LevantamientoCompletadoAdapter adaptador;

    @BeforeEach
    void setUp() {
        repositorio = mock(LevantamientoSitioRepository.class);
        adaptador = new LevantamientoCompletadoAdapter(repositorio);
    }

    @Test
    @DisplayName("sitioTieneLevantamientoCompletado es true solo si hay un Levantamiento completado del Sitio (Req 16.5)")
    void sitioCompletado() {
        when(repositorio.existsBySitioIdAndEstado(SITIO, EstadoLevantamiento.COMPLETADO))
                .thenReturn(true);
        assertThat(adaptador.sitioTieneLevantamientoCompletado(SITIO)).isTrue();

        when(repositorio.existsBySitioIdAndEstado(SITIO, EstadoLevantamiento.COMPLETADO))
                .thenReturn(false);
        assertThat(adaptador.sitioTieneLevantamientoCompletado(SITIO)).isFalse();
    }

    @Test
    @DisplayName("estaCompletado es true solo si el Levantamiento concreto esta completado (Req 16.5)")
    void levantamientoCompletado() {
        when(repositorio.existsByIdAndEstado(LEV, EstadoLevantamiento.COMPLETADO)).thenReturn(true);
        assertThat(adaptador.estaCompletado(LEV)).isTrue();

        when(repositorio.existsByIdAndEstado(LEV, EstadoLevantamiento.COMPLETADO)).thenReturn(false);
        assertThat(adaptador.estaCompletado(LEV)).isFalse();
    }

    @Test
    @DisplayName("identificadores nulos se tratan como no completados (guarda fail-safe)")
    void nulos() {
        assertThat(adaptador.sitioTieneLevantamientoCompletado(null)).isFalse();
        assertThat(adaptador.estaCompletado(null)).isFalse();
    }
}
