package com.dessti.crm.calidad.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias del agregado {@link ContextoOrganizacion} (Req 70.5): la justificacion
 * se exige y conserva incluso cuando el cambio climatico se concluye "no pertinente"
 * (clausula 4.2). Son pruebas puras de dominio, sin Spring ni base de datos.
 */
class ContextoOrganizacionTest {

    @Test
    void determinarConservaLaJustificacionCuandoNoEsPertinente() {
        ContextoOrganizacion contexto = ContextoOrganizacion.determinar(
                "cuestion", TipoContexto.INTERNA, false,
                "no aplica por la naturaleza del negocio", null, null, "actor");

        assertThat(contexto.isClimaPertinente()).isFalse();
        assertThat(contexto.getJustificacion())
                .isEqualTo("no aplica por la naturaleza del negocio");
    }

    @Test
    void determinarConservaLaJustificacionCuandoEsPertinente() {
        ContextoOrganizacion contexto = ContextoOrganizacion.determinar(
                "cuestion externa", TipoContexto.EXTERNA, true,
                "riesgo fisico por eventos climaticos", "reguladores", "cumplimiento", "actor");

        assertThat(contexto.isClimaPertinente()).isTrue();
        assertThat(contexto.getJustificacion()).isEqualTo("riesgo fisico por eventos climaticos");
        assertThat(contexto.getParteInteresada()).isEqualTo("reguladores");
        assertThat(contexto.getExpectativa()).isEqualTo("cumplimiento");
    }

    @Test
    void determinarRechazaJustificacionEnBlanco() {
        assertThatThrownBy(() -> ContextoOrganizacion.determinar(
                "cuestion", TipoContexto.INTERNA, false, "   ", null, null, "actor"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void determinarRechazaCuestionEnBlanco() {
        assertThatThrownBy(() -> ContextoOrganizacion.determinar(
                "  ", TipoContexto.INTERNA, false, "justificacion", null, null, "actor"))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
