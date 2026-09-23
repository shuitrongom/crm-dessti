package com.dessti.crm.platform.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del {@link LimitadorTasaPorIp} (Req 2.4). Usa un reloj
 * mutable para avanzar el tiempo de forma determinista y verificar el limite y
 * el reinicio de la ventana.
 */
class LimitadorTasaPorIpTest {

    private static final String IP = "203.0.113.7";
    private static final long VENTANA_MS = 60_000L;

    /** Reloj cuyo instante se puede avanzar manualmente en las pruebas. */
    private static final class RelojMutable extends Clock {
        private final AtomicReference<Instant> ahora;

        RelojMutable(Instant inicial) {
            this.ahora = new AtomicReference<>(inicial);
        }

        void avanzar(long millis) {
            ahora.updateAndGet(i -> i.plusMillis(millis));
        }

        @Override public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return ahora.get();
        }

        @Override public long millis() {
            return ahora.get().toEpochMilli();
        }
    }

    @Test
    @DisplayName("Permite exactamente el maximo y rechaza el excedente (429)")
    void rechazaExcedente() {
        Clock reloj = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        LimitadorTasaPorIp limitador = new LimitadorTasaPorIp(100, VENTANA_MS, reloj);

        for (int i = 1; i <= 100; i++) {
            assertThat(limitador.permitir(IP))
                    .as("peticion %d dentro del limite", i)
                    .isTrue();
        }
        // La 101a peticion excede el limite de 100/min.
        assertThat(limitador.permitir(IP)).isFalse();
        assertThat(limitador.permitir(IP)).isFalse();
    }

    @Test
    @DisplayName("Al iniciar una ventana nueva, el contador se reinicia")
    void reiniciaVentana() {
        RelojMutable reloj = new RelojMutable(Instant.parse("2025-01-01T00:00:00Z"));
        LimitadorTasaPorIp limitador = new LimitadorTasaPorIp(3, VENTANA_MS, reloj);

        assertThat(limitador.permitir(IP)).isTrue();
        assertThat(limitador.permitir(IP)).isTrue();
        assertThat(limitador.permitir(IP)).isTrue();
        assertThat(limitador.permitir(IP)).isFalse(); // 4a excede en la 1a ventana

        // Avanza mas alla de la ventana: nueva ventana, contador reiniciado.
        reloj.avanzar(VENTANA_MS);
        assertThat(limitador.permitir(IP)).isTrue();
    }

    @Test
    @DisplayName("El limite es independiente por IP")
    void independientePorIp() {
        Clock reloj = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        LimitadorTasaPorIp limitador = new LimitadorTasaPorIp(1, VENTANA_MS, reloj);

        assertThat(limitador.permitir("10.0.0.1")).isTrue();
        assertThat(limitador.permitir("10.0.0.1")).isFalse();
        // Otra IP conserva su propio cupo.
        assertThat(limitador.permitir("10.0.0.2")).isTrue();
    }
}
