package com.dessti.crm.calidad.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias del agregado {@link CambioSgc} (Req 70.4): guarda de campos previos a
 * la aprobacion, registro de actor/marca UTC al aprobar, y maquina de estados
 * propuesto->aprobado->implementado con rechazado como final alterno. Son pruebas puras de
 * dominio, sin Spring ni base de datos.
 */
class CambioSgcTest {

    private static CambioSgc propuesto() {
        return CambioSgc.proponer(
                "titulo", "proposito", "consecuencias", "recursos", UUID.randomUUID(), "actor");
    }

    @Test
    void proponerExigeTodosLosCamposObligatorios() {
        assertThatThrownBy(() -> CambioSgc.proponer("t", "  ", "c", "r", UUID.randomUUID(), "actor"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> CambioSgc.proponer("t", "p", "c", "r", null, "actor"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void aprobarRegistraActorYMarcaUtcYTransitaAAprobado() {
        CambioSgc cambio = propuesto();
        Instant cuando = Instant.parse("2026-01-15T10:00:00Z");

        cambio.aprobar("gerente", cuando);

        assertThat(cambio.getEstado()).isEqualTo(EstadoCambioSgc.APROBADO);
        assertThat(cambio.getAprobadoPor()).isEqualTo("gerente");
        assertThat(cambio.getAprobadoEn()).isEqualTo(cuando);
    }

    @Test
    void aprobarExigeActorYMarca() {
        CambioSgc cambio = propuesto();

        assertThatThrownBy(() -> cambio.aprobar("  ", Instant.now()))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> cambio.aprobar("gerente", null))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(cambio.getEstado()).isEqualTo(EstadoCambioSgc.PROPUESTO);
    }

    @Test
    void implementarSoloDesdeAprobado() {
        CambioSgc cambio = propuesto();
        assertThatThrownBy(() -> cambio.implementar("actor"))
                .isInstanceOf(TransicionInvalidaException.class);

        cambio.aprobar("gerente", Instant.now());
        assertThatCode(() -> cambio.implementar("actor")).doesNotThrowAnyException();
        assertThat(cambio.getEstado()).isEqualTo(EstadoCambioSgc.IMPLEMENTADO);
    }

    @Test
    void rechazarEsFinalAlternoDesdePropuesto() {
        CambioSgc cambio = propuesto();

        cambio.rechazar("actor");
        assertThat(cambio.getEstado()).isEqualTo(EstadoCambioSgc.RECHAZADO);
        assertThatThrownBy(() -> cambio.aprobar("gerente", Instant.now()))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void noSePuedeRechazarUnCambioYaAprobado() {
        CambioSgc cambio = propuesto();
        cambio.aprobar("gerente", Instant.now());

        assertThatThrownBy(() -> cambio.rechazar("actor"))
                .isInstanceOf(TransicionInvalidaException.class);
    }
}
