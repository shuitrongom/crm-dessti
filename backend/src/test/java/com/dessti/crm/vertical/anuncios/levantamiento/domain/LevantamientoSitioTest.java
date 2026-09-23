package com.dessti.crm.vertical.anuncios.levantamiento.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias del dominio {@link LevantamientoSitio} (Req 16.1, 16.2, 16.4).
 * No arrancan Spring ni base de datos. Cubren la creacion (en_proceso + validacion
 * de datos obligatorios), la marca de completado (actor + marca UTC) y la
 * inmutabilidad del estado final (completar un ya completado -> 409).
 */
class LevantamientoSitioTest {

    private static final UUID SITIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COTIZACION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDEN = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2024-05-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("crear fija estado en_proceso con datos y vinculos, sin marca de completado (Req 16.1, 16.2)")
    void crear() {
        LevantamientoSitio l = LevantamientoSitio.crear(
                "3x2 m", "Muro de concreto", "220V trifasico", SITIO, COTIZACION, ORDEN, "instalacion");

        assertThat(l.getEstado()).isEqualTo(EstadoLevantamiento.EN_PROCESO);
        assertThat(l.getMediciones()).isEqualTo("3x2 m");
        assertThat(l.getTipoSuperficie()).isEqualTo("Muro de concreto");
        assertThat(l.getCondicionesElectricas()).isEqualTo("220V trifasico");
        assertThat(l.getSitioId()).isEqualTo(SITIO);
        assertThat(l.getCotizacionId()).isEqualTo(COTIZACION);
        assertThat(l.getOrdenFabricacionId()).isEqualTo(ORDEN);
        assertThat(l.getCompletadoPor()).isNull();
        assertThat(l.getCompletadoEn()).isNull();
        assertThat(l.getId()).isNotNull();
        assertThat(l.estaCompletado()).isFalse();
    }

    @Test
    @DisplayName("crear permite vinculos nulos: los enlaces son opcionales (Req 16.2)")
    void crearSinVinculos() {
        LevantamientoSitio l = LevantamientoSitio.crear(
                "medidas", "estructura metalica", "sin acometida", null, null, null, "instalacion");

        assertThat(l.getSitioId()).isNull();
        assertThat(l.getCotizacionId()).isNull();
        assertThat(l.getOrdenFabricacionId()).isNull();
        assertThat(l.getEstado()).isEqualTo(EstadoLevantamiento.EN_PROCESO);
    }

    @Test
    @DisplayName("crear rechaza mediciones vacias (Req 16.1 -> 422)")
    void crearSinMediciones() {
        assertThatThrownBy(() -> LevantamientoSitio.crear(
                "  ", "muro", "220V", null, null, null, "instalacion"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear rechaza tipo de superficie vacio (Req 16.1 -> 422)")
    void crearSinSuperficie() {
        assertThatThrownBy(() -> LevantamientoSitio.crear(
                "3x2", null, "220V", null, null, null, "instalacion"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear rechaza condiciones electricas vacias (Req 16.1 -> 422)")
    void crearSinElectricas() {
        assertThatThrownBy(() -> LevantamientoSitio.crear(
                "3x2", "muro", "", null, null, null, "instalacion"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("completar cambia a completado y fija actor y marca UTC (Req 16.4)")
    void completar() {
        LevantamientoSitio l = LevantamientoSitio.crear(
                "3x2", "muro", "220V", SITIO, null, null, "instalacion");

        l.completar("supervisor", RELOJ);

        assertThat(l.getEstado()).isEqualTo(EstadoLevantamiento.COMPLETADO);
        assertThat(l.estaCompletado()).isTrue();
        assertThat(l.getCompletadoPor()).isEqualTo("supervisor");
        assertThat(l.getCompletadoEn()).isEqualTo(Instant.parse("2024-05-01T12:00:00Z"));
    }

    @Test
    @DisplayName("completar un Levantamiento ya completado es transicion invalida (Req 16.4 -> 409)")
    void completarYaCompletado() {
        LevantamientoSitio l = LevantamientoSitio.crear(
                "3x2", "muro", "220V", SITIO, null, null, "instalacion");
        l.completar("supervisor", RELOJ);

        assertThatThrownBy(() -> l.completar("supervisor", RELOJ))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("completar rechaza reloj nulo (Req 16.4 -> 422)")
    void completarSinReloj() {
        LevantamientoSitio l = LevantamientoSitio.crear(
                "3x2", "muro", "220V", SITIO, null, null, "instalacion");

        assertThatThrownBy(() -> l.completar("supervisor", null))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
