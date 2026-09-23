package com.dessti.crm.platform.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del dominio de bloqueo por intentos fallidos de
 * {@link UsuarioAuth} (Req 2.1, 2.2). Toda la logica temporal se ejerce con un
 * {@link Clock} fijo o avanzado manualmente para ser determinista.
 */
class UsuarioAuthTest {

    private static final Instant T0 = Instant.parse("2025-01-01T10:00:00Z");
    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static Clock relojEn(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }

    private static UsuarioAuth usuario() {
        return UsuarioAuthTestFactory.activo(ID, TENANT, "juan@empresa.com", "$2a$hash");
    }

    @Test
    @DisplayName("5 fallos consecutivos en la ventana -> bloqueo por 15 min")
    void bloqueaTras5Fallos() {
        UsuarioAuth u = usuario();
        Clock reloj = relojEn(T0);

        for (int i = 1; i <= 5; i++) {
            u.registrarFallo(reloj);
        }

        assertThat(u.getIntentosFallidos()).isEqualTo(5);
        assertThat(u.estaBloqueado(reloj)).isTrue();
        assertThat(u.getBloqueadoHasta())
                .isEqualTo(T0.plus(Duration.ofMinutes(UsuarioAuth.MINUTOS_BLOQUEO)));
    }

    @Test
    @DisplayName("4 fallos no bloquean la cuenta")
    void cuatroFallosNoBloquean() {
        UsuarioAuth u = usuario();
        Clock reloj = relojEn(T0);

        for (int i = 1; i <= 4; i++) {
            u.registrarFallo(reloj);
        }

        assertThat(u.getIntentosFallidos()).isEqualTo(4);
        assertThat(u.estaBloqueado(reloj)).isFalse();
        assertThat(u.getBloqueadoHasta()).isNull();
    }

    @Test
    @DisplayName("Mientras esta bloqueada, estaBloqueado sigue true hasta expirar")
    void bloqueadaHastaExpirar() {
        UsuarioAuth u = usuario();
        for (int i = 1; i <= 5; i++) {
            u.registrarFallo(relojEn(T0));
        }

        // 14 min despues: sigue bloqueada.
        assertThat(u.estaBloqueado(relojEn(T0.plus(Duration.ofMinutes(14))))).isTrue();
        // Justo en el limite (15 min): ya no esta bloqueada (bloqueado_hasta no es futuro).
        assertThat(u.estaBloqueado(relojEn(T0.plus(Duration.ofMinutes(15))))).isFalse();
        // 16 min despues: desbloqueada.
        assertThat(u.estaBloqueado(relojEn(T0.plus(Duration.ofMinutes(16))))).isFalse();
    }

    @Test
    @DisplayName("Al expirar el bloqueo, el siguiente fallo reinicia el contador a 1 (Req 2.1)")
    void contadorSeReiniciaTrasExpirarBloqueo() {
        UsuarioAuth u = usuario();
        for (int i = 1; i <= 5; i++) {
            u.registrarFallo(relojEn(T0));
        }
        assertThat(u.getIntentosFallidos()).isEqualTo(5);

        // Un nuevo fallo despues de que expiro el bloqueo (16 min): cuenta como
        // el primero de una nueva racha.
        Clock despues = relojEn(T0.plus(Duration.ofMinutes(16)));
        u.registrarFallo(despues);

        assertThat(u.getIntentosFallidos()).isEqualTo(1);
        assertThat(u.estaBloqueado(despues)).isFalse();
    }

    @Test
    @DisplayName("Un login exitoso reinicia el contador, limpia el bloqueo y el ancla de la ventana")
    void exitoReinicia() {
        UsuarioAuth u = usuario();
        u.registrarFallo(relojEn(T0));
        u.registrarFallo(relojEn(T0));
        assertThat(u.getIntentosFallidos()).isEqualTo(2);
        assertThat(u.getPrimerIntentoFallido()).isEqualTo(T0);

        u.registrarExito();

        assertThat(u.getIntentosFallidos()).isZero();
        assertThat(u.getBloqueadoHasta()).isNull();
        assertThat(u.getPrimerIntentoFallido()).isNull();
        assertThat(u.estaBloqueado(relojEn(T0))).isFalse();
    }

    @Test
    @DisplayName("El primer fallo ancla la ventana en su instante (Req 2.1)")
    void primerFalloAnclaLaVentana() {
        UsuarioAuth u = usuario();
        assertThat(u.getPrimerIntentoFallido()).isNull();

        u.registrarFallo(relojEn(T0));

        assertThat(u.getIntentosFallidos()).isEqualTo(1);
        assertThat(u.getPrimerIntentoFallido()).isEqualTo(T0);
    }

    @Test
    @DisplayName("Un fallo estrictamente despues de la ventana reinicia la racha a 1 y reancla la ventana")
    void falloTrasVentanaReiniciaLaRacha() {
        UsuarioAuth u = usuario();
        // 4 fallos en T0 (dentro de la ventana), sin llegar a bloquear.
        for (int i = 1; i <= 4; i++) {
            u.registrarFallo(relojEn(T0));
        }
        assertThat(u.getIntentosFallidos()).isEqualTo(4);
        assertThat(u.getPrimerIntentoFallido()).isEqualTo(T0);

        // Un 5to fallo justo despues del borde de la ventana (15 min + 1 s):
        // la ventana anclada en T0 ya transcurrio -> racha nueva desde 1.
        Instant fuera = T0.plus(Duration.ofMinutes(UsuarioAuth.MINUTOS_VENTANA)).plusSeconds(1);
        u.registrarFallo(relojEn(fuera));

        assertThat(u.getIntentosFallidos())
                .as("fallo fuera de la ventana reinicia el contador a 1")
                .isEqualTo(1);
        assertThat(u.getPrimerIntentoFallido())
                .as("la ventana se reancla en el nuevo primer fallo")
                .isEqualTo(fuera);
        assertThat(u.estaBloqueado(relojEn(fuera))).isFalse();
    }

    @Test
    @DisplayName("Un fallo exactamente en el borde de la ventana (15 min) todavia cuenta: 5 fallos bloquean")
    void falloEnElBordeDeLaVentanaTodaviaCuenta() {
        UsuarioAuth u = usuario();
        // 4 fallos en T0.
        for (int i = 1; i <= 4; i++) {
            u.registrarFallo(relojEn(T0));
        }
        // 5to fallo EXACTAMENTE a los 15 min (borde superior inclusivo).
        Instant borde = T0.plus(Duration.ofMinutes(UsuarioAuth.MINUTOS_VENTANA));
        u.registrarFallo(relojEn(borde));

        assertThat(u.getIntentosFallidos())
                .as("el 5to fallo en el borde exacto cuenta como dentro de la ventana")
                .isEqualTo(5);
        assertThat(u.estaBloqueado(relojEn(borde)))
                .as("5 fallos con el ultimo en el borde exacto bloquean la cuenta")
                .isTrue();
        assertThat(u.getBloqueadoHasta())
                .isEqualTo(borde.plus(Duration.ofMinutes(UsuarioAuth.MINUTOS_BLOQUEO)));
    }

    @Test
    @DisplayName("Fallos espaciados mas alla de la ventana nunca acumulan al bloqueo")
    void fallosEspaciadosNuncaBloquean() {
        UsuarioAuth u = usuario();
        Instant t = T0;
        // 10 fallos, cada uno a 16 min del anterior (> ventana): siempre racha=1.
        for (int i = 0; i < 10; i++) {
            u.registrarFallo(relojEn(t));
            assertThat(u.getIntentosFallidos())
                    .as("cada fallo espaciado > ventana reinicia la racha a 1")
                    .isEqualTo(1);
            assertThat(u.estaBloqueado(relojEn(t))).isFalse();
            t = t.plus(Duration.ofMinutes(UsuarioAuth.MINUTOS_VENTANA + 1));
        }
    }

    @Test
    @DisplayName("minutosRestantesBloqueo redondea hacia arriba y es 0 sin bloqueo")
    void minutosRestantes() {
        UsuarioAuth u = usuario();
        assertThat(u.minutosRestantesBloqueo(relojEn(T0))).isZero();

        for (int i = 1; i <= 5; i++) {
            u.registrarFallo(relojEn(T0));
        }
        // A los 0 min: quedan 15 min.
        assertThat(u.minutosRestantesBloqueo(relojEn(T0))).isEqualTo(15);
        // A los 14 min y 30 s: queda menos de 1 min -> redondea a 1.
        assertThat(u.minutosRestantesBloqueo(relojEn(T0.plus(Duration.ofSeconds(14 * 60 + 30)))))
                .isEqualTo(1);
    }
}
