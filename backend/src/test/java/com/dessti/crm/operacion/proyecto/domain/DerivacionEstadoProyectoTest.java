package com.dessti.crm.operacion.proyecto.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias por ejemplos concretos de la derivacion del estado
 * consolidado de un Proyecto
 * ({@link DerivacionEstadoProyecto#derivar(PerfilFasesGiro, java.util.List)};
 * Req 3.2, 3.4, 3.5, 14.2; tarea 1.13).
 *
 * <p>Se construyen casos deterministas con {@link AvanceFasesSitio}, cuyo
 * constructor recibe las cuatro banderas en el orden operativo de las fases:
 * {@code (tieneLevantamientoCompletado, tienePermisoAprobado,
 * tieneOrdenFabricacionTerminada, tieneInstalacionCompletada)}.</p>
 *
 * <ul>
 *   <li>Perfil {@link PerfilFasesGiro#ANUNCIOS}: recorre las cuatro fases en
 *       secuencia; el estado refleja la primera fase que no cubran todos los
 *       Sitios.</li>
 *   <li>Perfil {@link PerfilFasesGiro#GENERICO}: solo evalua Produccion; ignora
 *       levantamiento, permiso e instalacion.</li>
 * </ul>
 */
class DerivacionEstadoProyectoTest {

    // ------------------------------------------------------------------
    // Ayudas para construir el avance de un Sitio con banderas legibles.
    // Orden del constructor: levantamiento, permiso, fabricacion, instalacion.
    // ------------------------------------------------------------------

    /** Sitio sin ninguna fase cubierta. */
    private static AvanceFasesSitio sinNada() {
        return new AvanceFasesSitio(false, false, false, false);
    }

    /** Sitio con solo el Levantamiento_Sitio completado. */
    private static AvanceFasesSitio conLevantamiento() {
        return new AvanceFasesSitio(true, false, false, false);
    }

    /** Sitio con Levantamiento + Permiso aprobado. */
    private static AvanceFasesSitio conPermiso() {
        return new AvanceFasesSitio(true, true, false, false);
    }

    /** Sitio con Levantamiento + Permiso + Orden_Fabricacion terminada. */
    private static AvanceFasesSitio conFabricacion() {
        return new AvanceFasesSitio(true, true, true, false);
    }

    /** Sitio con las cuatro fases cubiertas (instalacion incluida). */
    private static AvanceFasesSitio completo() {
        return new AvanceFasesSitio(true, true, true, true);
    }

    /** Sitio generico: solo tiene la Orden_Fabricacion terminada (sin las otras fases). */
    private static AvanceFasesSitio soloFabricacion() {
        return new AvanceFasesSitio(false, false, true, false);
    }

    // ==================================================================
    // Perfil ANUNCIOS: las cuatro fases en secuencia.
    // ==================================================================

    @Test
    @DisplayName("ANUNCIOS: proyecto sin Sitios -> SIN_SITIOS")
    void anunciosSinSitios() {
        assertThat(DerivacionEstadoProyecto.derivar(PerfilFasesGiro.ANUNCIOS, List.of()))
                .isEqualTo(EstadoConsolidadoProyecto.SIN_SITIOS);
    }

    @Test
    @DisplayName("ANUNCIOS: un Sitio sin levantamiento -> EN_LEVANTAMIENTO")
    void anunciosSinLevantamiento() {
        // Un Sitio sin ninguna fase: falta la primera fase (Levantamiento).
        assertThat(DerivacionEstadoProyecto.derivar(
                PerfilFasesGiro.ANUNCIOS, List.of(sinNada())))
                .isEqualTo(EstadoConsolidadoProyecto.EN_LEVANTAMIENTO);
    }

    @Test
    @DisplayName("ANUNCIOS: todos con levantamiento pero alguno sin permiso -> EN_TRAMITE_PERMISOS")
    void anunciosFaltaPermiso() {
        // Todos con levantamiento; uno de ellos aun sin permiso aprobado.
        assertThat(DerivacionEstadoProyecto.derivar(
                PerfilFasesGiro.ANUNCIOS, List.of(conPermiso(), conLevantamiento())))
                .isEqualTo(EstadoConsolidadoProyecto.EN_TRAMITE_PERMISOS);
    }

    @Test
    @DisplayName("ANUNCIOS: todos con levantamiento+permiso pero alguno sin OF terminada -> EN_PRODUCCION")
    void anunciosFaltaFabricacion() {
        // Todos con levantamiento y permiso; uno aun sin Orden_Fabricacion terminada.
        assertThat(DerivacionEstadoProyecto.derivar(
                PerfilFasesGiro.ANUNCIOS, List.of(conFabricacion(), conPermiso())))
                .isEqualTo(EstadoConsolidadoProyecto.EN_PRODUCCION);
    }

    @Test
    @DisplayName("ANUNCIOS: todos con las 3 fases previas pero alguno sin instalacion -> EN_INSTALACION")
    void anunciosFaltaInstalacion() {
        // Todos con levantamiento, permiso y OF terminada; uno aun sin instalacion.
        assertThat(DerivacionEstadoProyecto.derivar(
                PerfilFasesGiro.ANUNCIOS, List.of(completo(), conFabricacion())))
                .isEqualTo(EstadoConsolidadoProyecto.EN_INSTALACION);
    }

    @Test
    @DisplayName("ANUNCIOS: todos con las 4 fases -> COMPLETADO")
    void anunciosCompletado() {
        assertThat(DerivacionEstadoProyecto.derivar(
                PerfilFasesGiro.ANUNCIOS, List.of(completo(), completo())))
                .isEqualTo(EstadoConsolidadoProyecto.COMPLETADO);
    }

    // ==================================================================
    // Perfil GENERICO: solo evalua la fase de Produccion.
    // ==================================================================

    @Test
    @DisplayName("GENERICO: proyecto sin Sitios -> SIN_SITIOS")
    void genericoSinSitios() {
        assertThat(DerivacionEstadoProyecto.derivar(PerfilFasesGiro.GENERICO, List.of()))
                .isEqualTo(EstadoConsolidadoProyecto.SIN_SITIOS);
    }

    @Test
    @DisplayName("GENERICO: todos los Sitios con OF terminada -> COMPLETADO (aunque falten las otras fases)")
    void genericoTodosConFabricacionEsCompletado() {
        // Ningun Sitio tiene levantamiento/permiso/instalacion, pero todos tienen
        // la Orden_Fabricacion terminada: para el perfil generico eso basta.
        assertThat(DerivacionEstadoProyecto.derivar(
                PerfilFasesGiro.GENERICO, List.of(soloFabricacion(), soloFabricacion())))
                .isEqualTo(EstadoConsolidadoProyecto.COMPLETADO);
    }

    @Test
    @DisplayName("GENERICO: alguno sin OF terminada -> EN_PRODUCCION")
    void genericoAlgunoSinFabricacionEsEnProduccion() {
        // Uno con OF terminada y otro sin ella: la fase Produccion no la cubren todos.
        assertThat(DerivacionEstadoProyecto.derivar(
                PerfilFasesGiro.GENERICO, List.of(soloFabricacion(), sinNada())))
                .isEqualTo(EstadoConsolidadoProyecto.EN_PRODUCCION);
    }
}
