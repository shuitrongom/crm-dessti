package com.dessti.crm.calidad.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias del agregado {@link Riesgo} (Req 70.3): derivacion del nivel a partir
 * de la matriz probabilidad x impacto, reevaluacion y maquina de estados. Son pruebas puras
 * de dominio, sin Spring ni base de datos.
 */
class RiesgoTest {

    @Test
    void identificarDerivaElNivelYDejaElRiesgoIdentificado() {
        Riesgo riesgo = Riesgo.identificar(
                "descripcion", Probabilidad.ALTA, Impacto.ALTO, "acciones", "actor");

        assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.IDENTIFICADO);
        assertThat(riesgo.getNivelDerivado()).isEqualTo(NivelRiesgo.CRITICO);
    }

    @Test
    void nivelDerivadoSigueLaMatrizDePesos() {
        assertThat(Riesgo.identificar("d", Probabilidad.BAJA, Impacto.BAJO, null, "a").getNivelDerivado())
                .isEqualTo(NivelRiesgo.BAJO);
        assertThat(Riesgo.identificar("d", Probabilidad.BAJA, Impacto.MEDIO, null, "a").getNivelDerivado())
                .isEqualTo(NivelRiesgo.MEDIO);
        assertThat(Riesgo.identificar("d", Probabilidad.MEDIA, Impacto.MEDIO, null, "a").getNivelDerivado())
                .isEqualTo(NivelRiesgo.ALTO);
        assertThat(Riesgo.identificar("d", Probabilidad.ALTA, Impacto.MEDIO, null, "a").getNivelDerivado())
                .isEqualTo(NivelRiesgo.CRITICO);
    }

    @Test
    void reevaluarRecalculaElNivelDerivado() {
        Riesgo riesgo = Riesgo.identificar(
                "descripcion", Probabilidad.BAJA, Impacto.BAJO, null, "actor");
        assertThat(riesgo.getNivelDerivado()).isEqualTo(NivelRiesgo.BAJO);

        riesgo.reevaluar(Probabilidad.ALTA, Impacto.ALTO, "actor");

        assertThat(riesgo.getProbabilidad()).isEqualTo(Probabilidad.ALTA);
        assertThat(riesgo.getImpacto()).isEqualTo(Impacto.ALTO);
        assertThat(riesgo.getNivelDerivado()).isEqualTo(NivelRiesgo.CRITICO);
    }

    @Test
    void cambiarEstadoRespetaLaMaquina() {
        Riesgo riesgo = Riesgo.identificar("d", Probabilidad.MEDIA, Impacto.MEDIO, null, "actor");

        riesgo.cambiarEstado(EstadoRiesgo.EN_TRATAMIENTO, "actor");
        assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.EN_TRATAMIENTO);
        riesgo.cambiarEstado(EstadoRiesgo.MITIGADO, "actor");
        assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.MITIGADO);
    }

    @Test
    void cambiarEstadoDesdeFinalSeRechaza() {
        Riesgo riesgo = Riesgo.identificar("d", Probabilidad.MEDIA, Impacto.MEDIO, null, "actor");
        riesgo.cambiarEstado(EstadoRiesgo.ACEPTADO, "actor");

        assertThatThrownBy(() -> riesgo.cambiarEstado(EstadoRiesgo.MITIGADO, "actor"))
                .isInstanceOf(TransicionInvalidaException.class);
    }
}
