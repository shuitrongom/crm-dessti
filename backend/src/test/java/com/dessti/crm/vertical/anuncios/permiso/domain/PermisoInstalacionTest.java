package com.dessti.crm.vertical.anuncios.permiso.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias del dominio {@link PermisoInstalacion} (Req 17.1, 17.2, 17.3,
 * 17.5). No arrancan Spring ni base de datos. Cubren la creacion (solicitado +
 * validacion de tipo/fecha obligatorios), la aprobacion/rechazo (actor + marca UTC),
 * la inmutabilidad de los estados finales (transiciones invalidas -> 409) y el
 * calculo de vencimiento proximo (30 dias).
 */
class PermisoInstalacionTest {

    private static final UUID SITIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant AHORA = Instant.parse("2024-05-01T12:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final LocalDate VENCE = LocalDate.of(2025, 5, 1);

    @Test
    @DisplayName("crear fija estado solicitado con tipo, fecha y sitio, sin decision (Req 17.1)")
    void crear() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, VENCE, SITIO, "instalacion");

        assertThat(p.getEstado()).isEqualTo(EstadoPermisoInstalacion.SOLICITADO);
        assertThat(p.getTipo()).isEqualTo(TipoPermisoInstalacion.MUNICIPAL);
        assertThat(p.getFechaVencimiento()).isEqualTo(VENCE);
        assertThat(p.getSitioId()).isEqualTo(SITIO);
        assertThat(p.getDecididoPor()).isNull();
        assertThat(p.getDecididoEn()).isNull();
        assertThat(p.getId()).isNotNull();
        assertThat(p.estaAprobado()).isFalse();
    }

    @Test
    @DisplayName("crear rechaza tipo nulo (Req 17.1 -> 422)")
    void crearSinTipo() {
        assertThatThrownBy(() -> PermisoInstalacion.crear(null, VENCE, SITIO, "instalacion"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear rechaza fecha de vencimiento nula (Req 17.1 -> 422)")
    void crearSinFecha() {
        assertThatThrownBy(() -> PermisoInstalacion.crear(
                TipoPermisoInstalacion.ARRENDADOR, null, SITIO, "instalacion"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("aprobar cambia a aprobado y fija actor y marca UTC (Req 17.2)")
    void aprobar() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, VENCE, SITIO, "instalacion");

        p.aprobar("supervisor", RELOJ);

        assertThat(p.getEstado()).isEqualTo(EstadoPermisoInstalacion.APROBADO);
        assertThat(p.estaAprobado()).isTrue();
        assertThat(p.getDecididoPor()).isEqualTo("supervisor");
        assertThat(p.getDecididoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("rechazar cambia a rechazado y fija actor y marca UTC (Req 17.2)")
    void rechazar() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.ARRENDADOR, VENCE, SITIO, "instalacion");

        p.rechazar("supervisor", RELOJ);

        assertThat(p.getEstado()).isEqualTo(EstadoPermisoInstalacion.RECHAZADO);
        assertThat(p.getDecididoPor()).isEqualTo("supervisor");
        assertThat(p.getDecididoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("aprobar un permiso ya aprobado es transicion invalida (Req 17.3 -> 409)")
    void aprobarDesdeFinalAprobado() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, VENCE, SITIO, "instalacion");
        p.aprobar("supervisor", RELOJ);

        assertThatThrownBy(() -> p.aprobar("supervisor", RELOJ))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("rechazar un permiso ya aprobado (transicion desde final) es invalida (Req 17.3 -> 409)")
    void rechazarDesdeFinalAprobado() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, VENCE, SITIO, "instalacion");
        p.aprobar("supervisor", RELOJ);

        assertThatThrownBy(() -> p.rechazar("supervisor", RELOJ))
                .isInstanceOf(TransicionInvalidaException.class);
        // El estado se conserva (Req 17.3).
        assertThat(p.getEstado()).isEqualTo(EstadoPermisoInstalacion.APROBADO);
    }

    @Test
    @DisplayName("aprobar un permiso ya rechazado (transicion desde final) es invalida (Req 17.3 -> 409)")
    void aprobarDesdeFinalRechazado() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, VENCE, SITIO, "instalacion");
        p.rechazar("supervisor", RELOJ);

        assertThatThrownBy(() -> p.aprobar("supervisor", RELOJ))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(p.getEstado()).isEqualTo(EstadoPermisoInstalacion.RECHAZADO);
    }

    @Test
    @DisplayName("aprobar rechaza reloj nulo (Req 17.2 -> 422)")
    void aprobarSinReloj() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, VENCE, SITIO, "instalacion");

        assertThatThrownBy(() -> p.aprobar("supervisor", null))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("venceEnProximosDias detecta un permiso que vence dentro de la ventana (Req 17.5)")
    void venceDentroDeVentana() {
        // Vence 20 dias despues de AHORA (2024-05-21).
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, LocalDate.of(2024, 5, 21), SITIO, "instalacion");

        assertThat(p.venceEnProximosDias(30, RELOJ)).isTrue();
    }

    @Test
    @DisplayName("venceEnProximosDias es falso si vence despues de la ventana (Req 17.5)")
    void venceFueraDeVentana() {
        // Vence 40 dias despues de AHORA.
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, LocalDate.of(2024, 6, 10), SITIO, "instalacion");

        assertThat(p.venceEnProximosDias(30, RELOJ)).isFalse();
    }

    @Test
    @DisplayName("venceEnProximosDias es falso si ya vencio (fecha anterior a hoy) (Req 17.5)")
    void yaVencido() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, LocalDate.of(2024, 4, 1), SITIO, "instalacion");

        assertThat(p.venceEnProximosDias(30, RELOJ)).isFalse();
    }

    @Test
    @DisplayName("venceEnProximosDias es verdadero justo en el limite de la ventana (Req 17.5)")
    void venceEnLimite() {
        // Vence exactamente a 30 dias (2024-05-31).
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, LocalDate.of(2024, 5, 31), SITIO, "instalacion");

        assertThat(p.venceEnProximosDias(30, RELOJ)).isTrue();
    }
}
