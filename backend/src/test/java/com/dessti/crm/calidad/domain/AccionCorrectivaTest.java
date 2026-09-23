package com.dessti.crm.calidad.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias del agregado {@link AccionCorrectiva} (Req 70.2): maquina de estados y
 * guarda de cierre por eficacia verificada (Property 43). Son pruebas puras de dominio, sin
 * Spring ni base de datos.
 */
class AccionCorrectivaTest {

    private static AccionCorrectiva abierta() {
        return AccionCorrectiva.abrir(
                UUID.randomUUID(), UUID.randomUUID(), "causa raiz", "acciones", "actor");
    }

    @Test
    void abrirDejaLaAccionEnEstadoAbiertaSinEficaciaNiCierre() {
        AccionCorrectiva accion = abierta();

        assertThat(accion.getEstado()).isEqualTo(EstadoAccionCorrectiva.ABIERTA);
        assertThat(accion.isEficaciaVerificada()).isFalse();
        assertThat(accion.getCerradaEn()).isNull();
        assertThat(accion.getEvidenciaCierre()).isNull();
        assertThat(accion.estaCerrada()).isFalse();
    }

    @Test
    void abrirRechazaResponsableNulo() {
        assertThatThrownBy(() -> AccionCorrectiva.abrir(null, null, "c", "a", "actor"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void avanzarSigueLaSecuenciaDeLaMaquina() {
        AccionCorrectiva accion = abierta();

        accion.avanzar(EstadoAccionCorrectiva.EN_ANALISIS, "actor");
        assertThat(accion.getEstado()).isEqualTo(EstadoAccionCorrectiva.EN_ANALISIS);
        accion.avanzar(EstadoAccionCorrectiva.EN_EJECUCION, "actor");
        assertThat(accion.getEstado()).isEqualTo(EstadoAccionCorrectiva.EN_EJECUCION);
        accion.avanzar(EstadoAccionCorrectiva.VERIFICACION, "actor");
        assertThat(accion.getEstado()).isEqualTo(EstadoAccionCorrectiva.VERIFICACION);
    }

    @Test
    void avanzarRechazaSaltoInvalido() {
        AccionCorrectiva accion = abierta();

        assertThatThrownBy(() -> accion.avanzar(EstadoAccionCorrectiva.VERIFICACION, "actor"))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(accion.getEstado()).isEqualTo(EstadoAccionCorrectiva.ABIERTA);
    }

    @Test
    void avanzarNoPermiteAlcanzarCierre() {
        AccionCorrectiva accion = abierta();
        accion.avanzar(EstadoAccionCorrectiva.EN_ANALISIS, "actor");
        accion.avanzar(EstadoAccionCorrectiva.EN_EJECUCION, "actor");
        accion.avanzar(EstadoAccionCorrectiva.VERIFICACION, "actor");

        assertThatThrownBy(() -> accion.avanzar(EstadoAccionCorrectiva.CERRADA, "actor"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void cierreExitosoConEficaciaVerificada() {
        AccionCorrectiva accion = abierta();
        accion.avanzar(EstadoAccionCorrectiva.EN_ANALISIS, "actor");
        accion.avanzar(EstadoAccionCorrectiva.EN_EJECUCION, "actor");
        accion.avanzar(EstadoAccionCorrectiva.VERIFICACION, "actor");
        accion.verificarEficacia("evidencia", "actor");

        assertThatCode(() -> accion.cerrar("actor")).doesNotThrowAnyException();
        assertThat(accion.getEstado()).isEqualTo(EstadoAccionCorrectiva.CERRADA);
        assertThat(accion.getCerradaEn()).isNotNull();
        assertThat(accion.getEvidenciaCierre()).isEqualTo("evidencia");
    }

    @Test
    void cierreSinEficaciaVerificadaSeRechaza() {
        AccionCorrectiva accion = abierta();
        accion.avanzar(EstadoAccionCorrectiva.EN_ANALISIS, "actor");
        accion.avanzar(EstadoAccionCorrectiva.EN_EJECUCION, "actor");
        accion.avanzar(EstadoAccionCorrectiva.VERIFICACION, "actor");

        assertThatThrownBy(() -> accion.cerrar("actor"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(accion.getEstado()).isEqualTo(EstadoAccionCorrectiva.VERIFICACION);
        assertThat(accion.getCerradaEn()).isNull();
    }

    @Test
    void cierreDesdeEstadoNoVerificacionSeRechazaPorTransicion() {
        AccionCorrectiva accion = abierta();
        accion.verificarEficacia("evidencia", "actor");

        assertThatThrownBy(() -> accion.cerrar("actor"))
                .isInstanceOf(TransicionInvalidaException.class);
    }
}
