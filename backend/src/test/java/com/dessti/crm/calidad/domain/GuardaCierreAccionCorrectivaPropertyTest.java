package com.dessti.crm.calidad.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 43: Guarda de cierre de
 * Accion_Correctiva (eficacia verificada)</strong> (Valida el Requisito 70.2).
 *
 * <p>Ejercita el nucleo de dominio PURO {@link AccionCorrectiva} y su maquina de estados
 * {@link EstadoAccionCorrectiva}, sin base de datos ni contexto de Spring. La construccion
 * ({@link AccionCorrectiva#abrir}) y las transiciones no tocan el {@code TenantContext}
 * (que solo se resuelve en {@code @PrePersist}), por lo que la logica es completamente
 * determinista y verificable.</p>
 *
 * <h2>Invariantes verificados (Property 43)</h2>
 * <ol>
 *   <li><strong>Cierre condicionado a la eficacia (Req 70.2):</strong> para una
 *       Accion_Correctiva en estado {@code verificacion}, el cierre
 *       ({@link AccionCorrectiva#cerrar}) tiene exito <em>si y solo si</em>
 *       {@code eficacia_verificada == true}; si es {@code false}, el cierre se rechaza con
 *       {@link ReglaNegocioException} y el estado permanece {@code verificacion} sin marca
 *       de cierre.</li>
 *   <li><strong>Estado final inmutable:</strong> desde {@code cerrada} (final) cualquier
 *       intento de cierre posterior se rechaza siempre.</li>
 * </ol>
 */
class GuardaCierreAccionCorrectivaPropertyTest {

    /**
     * Lleva una Accion_Correctiva recien abierta hasta el estado {@code verificacion},
     * avanzando por la maquina de estados sin verificar aun la eficacia.
     *
     * @return una Accion_Correctiva en estado {@code verificacion}, sin eficacia verificada.
     */
    private static AccionCorrectiva enVerificacion() {
        AccionCorrectiva accion = AccionCorrectiva.abrir(
                UUID.randomUUID(), UUID.randomUUID(), "causa raiz", "acciones planificadas", "actor");
        accion.avanzar(EstadoAccionCorrectiva.EN_ANALISIS, "actor");
        accion.avanzar(EstadoAccionCorrectiva.EN_EJECUCION, "actor");
        accion.avanzar(EstadoAccionCorrectiva.VERIFICACION, "actor");
        return accion;
    }

    // Feature: crm-anuncios-luminosos, Property 43: Guarda de cierre de Accion_Correctiva (eficacia verificada)
    // Validates: Requirements 70.2
    @Property(tries = 1000)
    void cierreEnVerificacionExitoSiiEficaciaVerificada(@ForAll boolean eficaciaVerificada) {
        AccionCorrectiva accion = enVerificacion();
        if (eficaciaVerificada) {
            accion.verificarEficacia("evidencia de eficacia", "actor");
        }

        if (eficaciaVerificada) {
            assertThatCode(() -> accion.cerrar("actor"))
                    .as("con eficacia verificada, el cierre debe tener exito (Req 70.2)")
                    .doesNotThrowAnyException();
            assertThat(accion.getEstado())
                    .as("tras el cierre exitoso el estado es cerrada")
                    .isEqualTo(EstadoAccionCorrectiva.CERRADA);
            assertThat(accion.estaCerrada()).isTrue();
            assertThat(accion.getCerradaEn())
                    .as("el cierre exitoso fija la marca temporal de cierre")
                    .isNotNull();
        } else {
            assertThatThrownBy(() -> accion.cerrar("actor"))
                    .as("sin eficacia verificada, el cierre debe rechazarse (Req 70.2, Property 43)")
                    .isInstanceOf(ReglaNegocioException.class);
            assertThat(accion.getEstado())
                    .as("un cierre rechazado no cambia el estado")
                    .isEqualTo(EstadoAccionCorrectiva.VERIFICACION);
            assertThat(accion.getCerradaEn())
                    .as("un cierre rechazado no fija la marca de cierre")
                    .isNull();
        }
    }

    // Feature: crm-anuncios-luminosos, Property 43: Guarda de cierre de Accion_Correctiva (eficacia verificada)
    // Validates: Requirements 70.2
    @Property(tries = 1000)
    void cerradaEsFinalRechazaNuevoCierre(@ForAll boolean ignorado) {
        AccionCorrectiva accion = enVerificacion();
        accion.verificarEficacia("evidencia", "actor");
        accion.cerrar("actor");

        assertThat(accion.getEstado()).isEqualTo(EstadoAccionCorrectiva.CERRADA);
        assertThatThrownBy(() -> accion.cerrar("actor"))
                .as("desde el estado final 'cerrada' no se admite un nuevo cierre")
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(accion.getEstado())
                .as("el estado final permanece cerrada")
                .isEqualTo(EstadoAccionCorrectiva.CERRADA);
    }

    // Feature: crm-anuncios-luminosos, Property 43: Guarda de cierre de Accion_Correctiva (eficacia verificada)
    // Validates: Requirements 70.2
    @Property(tries = 1000)
    void cierreDesdeEstadoNoVerificacionSiempreRechazado(@ForAll boolean eficaciaVerificada) {
        // Una Accion_Correctiva recien abierta (estado 'abierta') no admite cierre directo,
        // con independencia de la eficacia: la maquina exige pasar por 'verificacion'.
        AccionCorrectiva accion = AccionCorrectiva.abrir(
                UUID.randomUUID(), UUID.randomUUID(), "causa", "acciones", "actor");
        if (eficaciaVerificada) {
            accion.verificarEficacia("evidencia", "actor");
        }
        assertThatThrownBy(() -> accion.cerrar("actor"))
                .as("desde 'abierta' el cierre se rechaza por transicion invalida (409)")
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(accion.getEstado()).isEqualTo(EstadoAccionCorrectiva.ABIERTA);
    }
}
