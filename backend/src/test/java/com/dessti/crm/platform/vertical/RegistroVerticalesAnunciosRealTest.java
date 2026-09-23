package com.dessti.crm.platform.vertical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.vertical.anuncios.AnunciosVertical;

/**
 * Prueba de arranque/registro de verticales sin regresion (spec
 * operacion-produccion-enterprise, tarea 1.5; Req 2.1, 3.1, 16.6).
 *
 * <p>A diferencia de {@link RegistroVerticalesPropertyTest} —que ejercita el
 * registro con verticales <em>fake</em>—, esta prueba usa el
 * {@link AnunciosVertical} <strong>REAL</strong> para verificar que, tras la
 * reclasificacion de la tarea 1.4 (retiro de {@code orden_fabricacion} y
 * {@code proyecto} del conjunto de {@link AnunciosVertical#recursos()}), el
 * {@link RegistroVerticales}:</p>
 *
 * <ol>
 *   <li>inicializa sin excepcion (no se introducen duplicados de Giro, modulo ni
 *       recurso al retirar los recursos);</li>
 *   <li>resuelve {@code giroDeRecurso('orden_fabricacion')} y
 *       {@code giroDeRecurso('proyecto')} a <strong>vacio</strong> (recursos de
 *       Nucleo, transversales a todo Giro);</li>
 *   <li>conserva {@code giroDeRecurso('levantamiento_sitio')} mapeando al Giro
 *       {@code anuncios-luminosos} (sin regresion);</li>
 *   <li>conserva {@code giroDeModulo('operacion')} mapeando al Giro
 *       {@code anuncios-luminosos} (sin cambios).</li>
 * </ol>
 *
 * <p>El {@link RegistroVerticales} se construye directamente con la lista que
 * contiene el {@link AnunciosVertical} real, igual que los tests existentes
 * construyen el registro con fakes, pero aqui con la implementacion de
 * produccion —sin base de datos ni contexto de Spring, pues el constructor es
 * determinista y sin estado.</p>
 */
@DisplayName("RegistroVerticales con el AnunciosVertical REAL (tarea 1.5)")
class RegistroVerticalesAnunciosRealTest {

    /** Clave canonica del Giro de anuncios, coherente con AnunciosVertical.GIRO. */
    private static final String GIRO_ANUNCIOS = "anuncios-luminosos";

    /**
     * El registro se inicializa sin excepcion con el vertical de anuncios real
     * tras retirar los recursos {@code orden_fabricacion} y {@code proyecto}
     * (arranque sin regresion, Req 2.1, 3.1, 16.6).
     */
    @Test
    @DisplayName("inicializa sin excepcion con el AnunciosVertical real")
    void inicializaSinExcepcionConAnunciosReal() {
        assertThatCode(() -> new RegistroVerticales(List.of(new AnunciosVertical())))
                .as("el registro debe arrancar sin fallar con el vertical de anuncios real")
                .doesNotThrowAnyException();
    }

    /**
     * Tras la tarea 1.4, {@code orden_fabricacion} y {@code proyecto} dejan de ser
     * recursos de vertical: {@code giroDeRecurso(...)} devuelve vacio para ambos,
     * indicando que son recursos de Nucleo (transversales a todo Giro).
     */
    @Test
    @DisplayName("orden_fabricacion y proyecto quedan sin giro (recursos de Nucleo)")
    void ordenFabricacionYProyectoSonRecursosDeNucleo() {
        RegistroVerticales registro = new RegistroVerticales(List.of(new AnunciosVertical()));

        assertThat(registro.giroDeRecurso("orden_fabricacion"))
                .as("'orden_fabricacion' ya no lo declara ningun vertical -> recurso de Nucleo (vacio)")
                .isEmpty();

        assertThat(registro.giroDeRecurso("proyecto"))
                .as("'proyecto' ya no lo declara ningun vertical -> recurso de Nucleo (vacio)")
                .isEmpty();
    }

    /**
     * El resto de recursos especificos de anuncios se conserva: en particular
     * {@code levantamiento_sitio} sigue mapeando al Giro {@code anuncios-luminosos}
     * (sin regresion).
     */
    @Test
    @DisplayName("levantamiento_sitio sigue mapeando a anuncios-luminosos")
    void levantamientoSitioSigueMapeandoAAnuncios() {
        RegistroVerticales registro = new RegistroVerticales(List.of(new AnunciosVertical()));

        assertThat(registro.giroDeRecurso("levantamiento_sitio"))
                .as("'levantamiento_sitio' debe seguir resolviendo al Giro de anuncios")
                .contains(GIRO_ANUNCIOS);
    }

    /**
     * El modulo {@code operacion} permanece aportado por el vertical de anuncios:
     * {@code giroDeModulo('operacion')} sigue mapeando a {@code anuncios-luminosos}
     * (sin cambios respecto a la tarea 1.4, que no toca {@code modulos()}).
     */
    @Test
    @DisplayName("giroDeModulo('operacion') sigue mapeando a anuncios-luminosos")
    void moduloOperacionSigueMapeandoAAnuncios() {
        RegistroVerticales registro = new RegistroVerticales(List.of(new AnunciosVertical()));

        assertThat(registro.giroDeModulo("operacion"))
                .as("el modulo 'operacion' debe seguir resolviendo al Giro de anuncios")
                .contains(GIRO_ANUNCIOS);
    }
}
