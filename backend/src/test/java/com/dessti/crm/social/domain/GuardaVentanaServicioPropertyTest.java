package com.dessti.crm.social.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 37: Guarda de
 * Ventana_Servicio para envio de mensajes</strong> (Req 64.6, 64.7).
 *
 * <p>Ejercita el nucleo de decision PURO {@link GuardaVentanaServicio} contra un
 * modelo de referencia calculado de forma independiente, sin base de datos ni
 * contexto de Spring. Los instantes se generan como desplazamientos deterministas
 * (en minutos) respecto a un instante base {@code ahora}, y la ventana como un
 * numero de horas positivo, de modo que el borde de igualdad (exactamente en la
 * ventana) sea reproducible.</p>
 *
 * <h2>Invariantes verificados (Property 37)</h2>
 * <ol>
 *   <li><strong>Dentro de la ventana (Req 64.6):</strong> con un ultimo entrante no
 *       nulo, {@code dentroDeVentana} equivale a
 *       {@code (ahora - ultimoEntrante) <= ventana}.</li>
 *   <li><strong>Sin entrantes:</strong> {@code ultimoEntrante == null} implica FUERA
 *       de la ventana (se exige Plantilla_Mensaje).</li>
 *   <li><strong>Texto libre (Req 64.6/64.7):</strong> el envio de texto libre se
 *       permite <em>si y solo si</em> se esta dentro de la ventana.</li>
 *   <li><strong>Plantilla / interactivo (Req 64.7):</strong> siempre se permiten,
 *       dentro y fuera de la ventana.</li>
 * </ol>
 */
class GuardaVentanaServicioPropertyTest {

    private static final Instant AHORA = Instant.parse("2025-01-15T12:00:00Z");

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Amplitud de la ventana en horas (1..168 = hasta una semana). */
    @Provide
    Arbitrary<Integer> ventanas() {
        return Arbitraries.integers().between(1, 168);
    }

    /**
     * Desplazamiento en minutos del ultimo entrante respecto a {@code ahora}: valores
     * negativos = entrante en el pasado (transcurrido positivo); positivos = entrante
     * "en el futuro" (transcurrido negativo, borde defensivo). Rango amplio para
     * cubrir dentro y fuera de la ventana.
     */
    @Provide
    Arbitrary<Long> desplazamientosMinutos() {
        return Arbitraries.longs().between(-20_160L, 1_440L);
    }

    @Provide
    Arbitrary<TipoMensaje> tiposLibres() {
        return Arbitraries.of(TipoMensaje.TEXTO);
    }

    @Provide
    Arbitrary<TipoMensaje> tiposNoLibres() {
        return Arbitraries.of(TipoMensaje.PLANTILLA, TipoMensaje.INTERACTIVO);
    }

    // ----------------------------------------------------------------------
    // Property 37 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 37: Guarda de Ventana_Servicio para envío de mensajes
    @Property(tries = 1000)
    void dentroDeVentanaEquivaleAlTranscurridoAcotado(
            @ForAll("desplazamientosMinutos") long desplazamientoMin,
            @ForAll("ventanas") int ventanaHoras) {

        Instant ultimoEntrante = AHORA.plus(Duration.ofMinutes(desplazamientoMin));
        boolean resultado = GuardaVentanaServicio.dentroDeVentana(ultimoEntrante, AHORA, ventanaHoras);

        Duration transcurrido = Duration.between(ultimoEntrante, AHORA);
        boolean esperado = transcurrido.isNegative()
                || transcurrido.compareTo(Duration.ofHours(ventanaHoras)) <= 0;
        assertThat(resultado)
                .as("dentroDeVentana <=> (ahora - ultimoEntrante)=%s <= %sh", transcurrido, ventanaHoras)
                .isEqualTo(esperado);
    }

    // Feature: crm-anuncios-luminosos, Property 37: Guarda de Ventana_Servicio para envío de mensajes
    @Property(tries = 1000)
    void sinEntrantePrevioSiempreEstaFueraDeLaVentana(@ForAll("ventanas") int ventanaHoras) {
        assertThat(GuardaVentanaServicio.dentroDeVentana(null, AHORA, ventanaHoras))
                .as("sin ningun entrante previo se esta fuera de la ventana (exige plantilla)")
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 37: Guarda de Ventana_Servicio para envío de mensajes
    @Property(tries = 1000)
    void textoLibreSePermiteSoloDentroDeLaVentana(
            @ForAll("desplazamientosMinutos") long desplazamientoMin,
            @ForAll("ventanas") int ventanaHoras,
            @ForAll("tiposLibres") TipoMensaje tipo) {

        Instant ultimoEntrante = AHORA.plus(Duration.ofMinutes(desplazamientoMin));
        boolean permite = GuardaVentanaServicio.permiteEnvio(tipo, ultimoEntrante, AHORA, ventanaHoras);
        boolean dentro = GuardaVentanaServicio.dentroDeVentana(ultimoEntrante, AHORA, ventanaHoras);

        assertThat(permite)
                .as("texto libre permitido <=> dentro de la ventana (Req 64.6/64.7)")
                .isEqualTo(dentro);
    }

    // Feature: crm-anuncios-luminosos, Property 37: Guarda de Ventana_Servicio para envío de mensajes
    @Property(tries = 1000)
    void textoLibreSinEntranteSiempreSeRechaza(
            @ForAll("ventanas") int ventanaHoras,
            @ForAll("tiposLibres") TipoMensaje tipo) {

        assertThat(GuardaVentanaServicio.permiteEnvio(tipo, null, AHORA, ventanaHoras))
                .as("texto libre sin entrante previo se rechaza (exige plantilla, Req 64.7)")
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 37: Guarda de Ventana_Servicio para envío de mensajes
    @Property(tries = 1000)
    void plantillaEInteractivoSiemprePermitidos(
            @ForAll("desplazamientosMinutos") long desplazamientoMin,
            @ForAll("ventanas") int ventanaHoras,
            @ForAll("tiposNoLibres") TipoMensaje tipo) {

        Instant ultimoEntrante = AHORA.plus(Duration.ofMinutes(desplazamientoMin));
        assertThat(GuardaVentanaServicio.permiteEnvio(tipo, ultimoEntrante, AHORA, ventanaHoras))
                .as("plantilla/interactivo permitidos dentro y fuera de la ventana (Req 64.7)")
                .isTrue();
        // Tambien sin ningun entrante previo.
        assertThat(GuardaVentanaServicio.permiteEnvio(tipo, null, AHORA, ventanaHoras))
                .as("plantilla/interactivo permitidos incluso sin entrante previo (Req 64.7)")
                .isTrue();
    }
}
