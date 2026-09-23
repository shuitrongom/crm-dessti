package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias basadas en ejemplos (JUnit 5 + AssertJ, sin Spring ni BD) de
 * las <strong>transiciones de estado</strong> y del cómputo derivado
 * {@link Suscripcion#estaVencida(LocalDate)} del Contrato, para el rediseño
 * {@code plan-vs-suscripcion-contratacion}.
 *
 * <p>Cubren de forma puntual (example/boundary) los criterios de aceptación:</p>
 * <ul>
 *   <li>Req 5.2 / 8.1: {@code EN_PRUEBA -> ACTIVA} vía {@code activarFacturacion}
 *       (happy path, con {@code facturacionActivada}, {@code inicioFacturacion} y
 *       {@code vigenciaFin}).</li>
 *   <li>Req 8.4: activar la facturación desde un estado distinto de {@code EN_PRUEBA}
 *       se rechaza con {@link ReglaNegocioException} (mapea a HTTP 422).</li>
 *   <li>Req 5.4: {@code CANCELADA} es terminal; suspender/activar facturación desde
 *       {@code CANCELADA} se rechaza.</li>
 *   <li>Req 5.3 / 6: semántica exacta de frontera de {@code estaVencida(hoy)}.</li>
 * </ul>
 *
 * <p>Complementa (sin duplicar) al {@code PlanVsSuscripcionDominioPropertyTest}:
 * aquí el foco son las fronteras exactas de {@code estaVencida} y el rechazo desde
 * estado terminal, no cubiertos por las propiedades.</p>
 *
 * <p><strong>Semántica de {@code estaVencida} observada en el código</strong>
 * ({@code Suscripcion.estaVencida}): devuelve {@code true} únicamente cuando el
 * estado es {@code ACTIVA} o {@code EN_PRUEBA}, {@code vigenciaFin != null},
 * {@code hoy != null} y {@code vigenciaFin.isBefore(hoy)}. El corte es
 * <em>estricto</em> ({@code hoy > vigenciaFin}): el propio día de fin de vigencia
 * ({@code hoy == vigenciaFin}) todavía NO está vencido, y {@code vigenciaFin == null}
 * nunca vence.</p>
 */
@DisplayName("Suscripcion (Contrato): transiciones de estado y estaVencida")
class SuscripcionTransicionesTest {

    private static final String ACTOR = "super_admin";
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID PAQUETE = UUID.randomUUID();
    private static final LocalDate INICIO = LocalDate.of(2025, 1, 15);

    // ----------------------------------------------------------------------
    // Req 5.2 / 8.1: EN_PRUEBA -> ACTIVA vía activarFacturacion (happy path)
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("activarFacturacion (Req 5.2, 8.1)")
    class ActivarFacturacion {

        @Test
        @DisplayName("EN_PRUEBA -> ACTIVA fija estado, facturacion, inicioFacturacion y vigenciaFin")
        void enPruebaTransicionaAActivaEnHappyPath() {
            Suscripcion contrato = Suscripcion.crearEnPrueba(TENANT, PAQUETE, INICIO, 3, ACTOR);
            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.EN_PRUEBA);
            assertThat(contrato.isFacturacionActivada()).isFalse();
            assertThat(contrato.getInicioFacturacion()).isNull();

            LocalDate inicioFacturacion = INICIO.plusMonths(3).plusDays(1);
            LocalDate nuevaVigenciaFin = INICIO.plusMonths(3).plusYears(1);

            contrato.activarFacturacion(inicioFacturacion, nuevaVigenciaFin, ACTOR);

            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
            assertThat(contrato.isFacturacionActivada()).isTrue();
            assertThat(contrato.getInicioFacturacion()).isEqualTo(inicioFacturacion);
            assertThat(contrato.getVigenciaFin()).isEqualTo(nuevaVigenciaFin);
            assertThat(contrato.getUpdatedBy()).isEqualTo(ACTOR);
        }

        // ------------------------------------------------------------------
        // Req 8.4: rechazo desde estados != EN_PRUEBA (mapea a 422)
        // ------------------------------------------------------------------

        @Test
        @DisplayName("desde ACTIVA se rechaza con ReglaNegocioException y no cambia el estado")
        void desdeActivaSeRechaza() {
            Suscripcion contrato = Suscripcion.crearDeSuscripcion(TENANT, PAQUETE, INICIO, null, ACTOR);
            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);

            assertThatThrownBy(() -> contrato.activarFacturacion(INICIO.plusDays(1), null, ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);

            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
            assertThat(contrato.isFacturacionActivada()).isFalse();
            assertThat(contrato.getInicioFacturacion()).isNull();
        }

        @Test
        @DisplayName("desde SUSPENDIDA se rechaza con ReglaNegocioException y no cambia el estado")
        void desdeSuspendidaSeRechaza() {
            Suscripcion contrato = Suscripcion.crearDeSuscripcion(TENANT, PAQUETE, INICIO, null, ACTOR);
            contrato.suspender(ACTOR);
            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.SUSPENDIDA);

            assertThatThrownBy(() -> contrato.activarFacturacion(INICIO.plusDays(1), null, ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);

            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.SUSPENDIDA);
            assertThat(contrato.isFacturacionActivada()).isFalse();
        }

        @Test
        @DisplayName("desde CANCELADA (terminal) se rechaza con ReglaNegocioException")
        void desdeCanceladaSeRechaza() {
            Suscripcion contrato = Suscripcion.crearDeSuscripcion(TENANT, PAQUETE, INICIO, null, ACTOR);
            contrato.cancelar(ACTOR);
            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);

            assertThatThrownBy(() -> contrato.activarFacturacion(INICIO.plusDays(1), null, ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);

            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);
            assertThat(contrato.isFacturacionActivada()).isFalse();
        }
    }

    // ----------------------------------------------------------------------
    // Req 5.4: CANCELADA es terminal
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("Estado terminal CANCELADA (Req 5.4)")
    class EstadoTerminalCancelada {

        @Test
        @DisplayName("suspender desde CANCELADA se rechaza con ReglaNegocioException y no cambia el estado")
        void suspenderDesdeCanceladaSeRechaza() {
            Suscripcion contrato = Suscripcion.crearDeSuscripcion(TENANT, PAQUETE, INICIO, null, ACTOR);
            contrato.cancelar(ACTOR);
            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);

            assertThatThrownBy(() -> contrato.suspender(ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);

            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);
        }

        @Test
        @DisplayName("activar desde CANCELADA se rechaza con ReglaNegocioException y no cambia el estado")
        void activarDesdeCanceladaSeRechaza() {
            Suscripcion contrato = Suscripcion.crearDeSuscripcion(TENANT, PAQUETE, INICIO, null, ACTOR);
            contrato.cancelar(ACTOR);

            assertThatThrownBy(() -> contrato.activar(ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);

            assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);
        }
    }

    // ----------------------------------------------------------------------
    // Req 5.3 / 6: fronteras exactas de estaVencida(hoy)
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("estaVencida(hoy): fronteras (Req 5.3, 6)")
    class EstaVencida {

        /** Contrato ACTIVA con fin de vigencia explícito (los estados que dan acceso). */
        private Suscripcion contratoActivoConFin(LocalDate fin) {
            return Suscripcion.crearDeSuscripcion(TENANT, PAQUETE, INICIO, fin, ACTOR);
        }

        @Test
        @DisplayName("hoy == vigenciaFin (último día válido) NO está vencida (corte estricto)")
        void enElUltimoDiaNoEstaVencida() {
            LocalDate fin = INICIO.plusMonths(6);
            Suscripcion contrato = contratoActivoConFin(fin);

            assertThat(contrato.estaVencida(fin)).isFalse();
        }

        @Test
        @DisplayName("hoy > vigenciaFin (día siguiente al fin) SÍ está vencida")
        void despuesDelFinEstaVencida() {
            LocalDate fin = INICIO.plusMonths(6);
            Suscripcion contrato = contratoActivoConFin(fin);

            assertThat(contrato.estaVencida(fin.plusDays(1))).isTrue();
        }

        @Test
        @DisplayName("hoy < vigenciaFin NO está vencida")
        void antesDelFinNoEstaVencida() {
            LocalDate fin = INICIO.plusMonths(6);
            Suscripcion contrato = contratoActivoConFin(fin);

            assertThat(contrato.estaVencida(fin.minusDays(1))).isFalse();
        }

        @Test
        @DisplayName("vigenciaFin == null nunca está vencida (sin fecha de fin)")
        void sinFinNuncaEstaVencida() {
            Suscripcion contrato = contratoActivoConFin(null);

            assertThat(contrato.estaVencida(INICIO.plusYears(10))).isFalse();
        }

        @Test
        @DisplayName("EN_PRUEBA vencida cuando hoy > vigenciaFin de la prueba")
        void enPruebaVencidaTrasElFinDeVigencia() {
            Suscripcion contrato = Suscripcion.crearEnPrueba(TENANT, PAQUETE, INICIO, 2, ACTOR);
            LocalDate finPrueba = INICIO.plusMonths(2);

            assertThat(contrato.estaVencida(finPrueba)).isFalse();
            assertThat(contrato.estaVencida(finPrueba.plusDays(1))).isTrue();
        }
    }

    // ----------------------------------------------------------------------
    // Guardas de robustez del happy path (no duplican propiedades)
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("activarFacturacion sin inicioFacturacion se rechaza desde EN_PRUEBA")
    void activarFacturacionSinFechaSeRechaza() {
        Suscripcion contrato = Suscripcion.crearEnPrueba(TENANT, PAQUETE, INICIO, 3, ACTOR);

        assertThatThrownBy(() -> contrato.activarFacturacion(null, INICIO.plusYears(1), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.EN_PRUEBA);
        assertThat(contrato.isFacturacionActivada()).isFalse();
    }

    @Test
    @DisplayName("cancelar es idempotente en cuanto al estado final")
    void cancelarDejaEstadoCancelada() {
        Suscripcion contrato = Suscripcion.crearDeSuscripcion(TENANT, PAQUETE, INICIO, null, ACTOR);

        assertThatCode(() -> contrato.cancelar(ACTOR)).doesNotThrowAnyException();
        assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);
    }
}
