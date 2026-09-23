package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias del ciclo de vida de offboarding de {@link Empresa}
 * (Req 69.2, 69.3): cancelacion terminal con Periodo_Gracia y consulta de
 * expiracion de la gracia. Dominio puro, sin dependencias de framework.
 */
class EmpresaOffboardingTest {

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");
    private static final Duration GRACIA = Duration.ofDays(30);

    private Empresa nueva() {
        return Empresa.crear("Rotulos SA", "RSA010101AAA", java.util.UUID.randomUUID(), "super-admin");
    }

    @Test
    @DisplayName("cancelar: fija CANCELADA, fecha_cancelacion y fin_periodo_gracia = ahora + gracia (Req 69.2)")
    void cancelarFijaEstadoYGracia() {
        Empresa e = nueva();
        e.cancelar(T0, GRACIA, "super-admin");

        assertThat(e.getEstado()).isEqualTo(EstadoEmpresa.CANCELADA);
        assertThat(e.estaCancelada()).isTrue();
        assertThat(e.getFechaCancelacion()).isEqualTo(T0);
        assertThat(e.getFinPeriodoGracia()).isEqualTo(T0.plus(GRACIA));
    }

    @Test
    @DisplayName("cancelar es idempotente: no reinicia el Periodo_Gracia ya en curso")
    void cancelarIdempotente() {
        Empresa e = nueva();
        e.cancelar(T0, GRACIA, "super-admin");
        Instant finOriginal = e.getFinPeriodoGracia();

        // Segunda cancelacion mas tarde: no debe extender la gracia.
        e.cancelar(T0.plus(Duration.ofDays(10)), Duration.ofDays(90), "otro-admin");

        assertThat(e.getFechaCancelacion()).isEqualTo(T0);
        assertThat(e.getFinPeriodoGracia()).isEqualTo(finOriginal);
    }

    @Test
    @DisplayName("periodoGraciaExpirado: false durante la gracia, true al alcanzar el fin (Req 69.3)")
    void periodoGraciaExpirado() {
        Empresa e = nueva();
        e.cancelar(T0, GRACIA, "super-admin");
        Instant fin = T0.plus(GRACIA);

        assertThat(e.periodoGraciaExpirado(T0)).isFalse();
        assertThat(e.periodoGraciaExpirado(fin.minusSeconds(1))).isFalse();
        assertThat(e.periodoGraciaExpirado(fin)).isTrue();          // <= ahora
        assertThat(e.periodoGraciaExpirado(fin.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("periodoGraciaExpirado: false para una Empresa no cancelada (fail-safe)")
    void periodoGraciaExpiradoNoCancelada() {
        Empresa e = nueva();
        assertThat(e.periodoGraciaExpirado(T0.plusSeconds(999_999))).isFalse();
    }

    @Test
    @DisplayName("cancelar rechaza gracia nula o negativa (Req 69.2)")
    void cancelarRechazaGraciaInvalida() {
        Empresa e = nueva();
        assertThatThrownBy(() -> e.cancelar(T0, null, "super-admin"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> e.cancelar(T0, Duration.ofDays(-1), "super-admin"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("activar/suspender rechazan una Empresa cancelada: la cancelacion es terminal")
    void cancelacionEsTerminal() {
        Empresa e = nueva();
        e.cancelar(T0, GRACIA, "super-admin");
        assertThatThrownBy(() -> e.activar("super-admin")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> e.suspender("super-admin")).isInstanceOf(ReglaNegocioException.class);
    }
}
