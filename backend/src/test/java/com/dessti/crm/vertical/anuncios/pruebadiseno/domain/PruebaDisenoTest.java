package com.dessti.crm.vertical.anuncios.pruebadiseno.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias del dominio {@link PruebaDiseno} (Req 15.1, 15.2, 15.3, 15.4).
 * No arrancan Spring ni base de datos. Cubren la generacion inicial (version 1
 * pendiente), la aprobacion/rechazo con actor y marca UTC, la generacion de la
 * siguiente version y la inmutabilidad (decidir una prueba ya decidida -> 409).
 */
class PruebaDisenoTest {

    private static final UUID COTIZACION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2024-05-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("generarInicial crea version 1 en estado pendiente sin decision (Req 15.1)")
    void generarInicial() {
        PruebaDiseno prueba = PruebaDiseno.generarInicial(COTIZACION, "diseno");

        assertThat(prueba.getNumeroVersion()).isEqualTo(1);
        assertThat(prueba.getEstado()).isEqualTo(EstadoPruebaDiseno.PENDIENTE);
        assertThat(prueba.getCotizacionId()).isEqualTo(COTIZACION);
        assertThat(prueba.getDecididaEn()).isNull();
        assertThat(prueba.getAprobadaPor()).isNull();
        assertThat(prueba.getRechazadaPor()).isNull();
        assertThat(prueba.getId()).isNotNull();
    }

    @Test
    @DisplayName("generarInicial rechaza Cotizacion nula (Req 15.1 -> 422)")
    void generarInicialSinCotizacion() {
        assertThatThrownBy(() -> PruebaDiseno.generarInicial(null, "diseno"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("aprobar cambia a aprobada, fija actor y marca UTC de la decision (Req 15.2)")
    void aprobar() {
        PruebaDiseno prueba = PruebaDiseno.generarInicial(COTIZACION, "diseno");

        prueba.aprobar("cliente", RELOJ);

        assertThat(prueba.getEstado()).isEqualTo(EstadoPruebaDiseno.APROBADA);
        assertThat(prueba.getAprobadaPor()).isEqualTo("cliente");
        assertThat(prueba.getDecididaEn()).isEqualTo(Instant.parse("2024-05-01T12:00:00Z"));
    }

    @Test
    @DisplayName("rechazar cambia a rechazada, fija actor y marca UTC (Req 15.3)")
    void rechazar() {
        PruebaDiseno prueba = PruebaDiseno.generarInicial(COTIZACION, "diseno");

        prueba.rechazar("cliente", RELOJ);

        assertThat(prueba.getEstado()).isEqualTo(EstadoPruebaDiseno.RECHAZADA);
        assertThat(prueba.getRechazadaPor()).isEqualTo("cliente");
        assertThat(prueba.getDecididaEn()).isEqualTo(Instant.parse("2024-05-01T12:00:00Z"));
    }

    @Test
    @DisplayName("aprobar una prueba ya decidida es transicion invalida (Req 15.4 -> 409)")
    void aprobarYaDecidida() {
        PruebaDiseno prueba = PruebaDiseno.generarInicial(COTIZACION, "diseno");
        prueba.aprobar("cliente", RELOJ);

        assertThatThrownBy(() -> prueba.aprobar("cliente", RELOJ))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(() -> prueba.rechazar("cliente", RELOJ))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("rechazar una prueba ya rechazada es transicion invalida (Req 15.4 -> 409)")
    void rechazarYaDecidida() {
        PruebaDiseno prueba = PruebaDiseno.generarInicial(COTIZACION, "diseno");
        prueba.rechazar("cliente", RELOJ);

        assertThatThrownBy(() -> prueba.rechazar("cliente", RELOJ))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("siguienteVersion crea una version >= 2 en pendiente (Req 15.3, Property 8)")
    void siguienteVersion() {
        PruebaDiseno v3 = PruebaDiseno.siguienteVersion(COTIZACION, 3, "diseno");

        assertThat(v3.getNumeroVersion()).isEqualTo(3);
        assertThat(v3.getEstado()).isEqualTo(EstadoPruebaDiseno.PENDIENTE);
        assertThat(v3.getCotizacionId()).isEqualTo(COTIZACION);
    }

    @Test
    @DisplayName("siguienteVersion rechaza un numero <= 1 (debe ser mayor que la inicial)")
    void siguienteVersionInvalida() {
        assertThatThrownBy(() -> PruebaDiseno.siguienteVersion(COTIZACION, 1, "diseno"))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
