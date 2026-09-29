package com.dessti.crm.vertical.anuncios.permiso.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dessti.crm.vertical.anuncios.permiso.domain.EstadoPermisoInstalacion;

/**
 * Pruebas del {@link PermisoAprobadoAdapter}: verifican que la guarda de
 * aprobacion (historica) y la de VIGENCIA (aprobado + no vencido) delegan en la
 * consulta correcta del repositorio, y que la vigencia se evalua contra la fecha
 * actual del {@link Clock} inyectado. Sin Spring ni base de datos.
 */
class PermisoAprobadoAdapterTest {

    private static final UUID SITIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    // Reloj fijo: hoy = 2026-03-01 (UTC).
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOY = LocalDate.of(2026, 3, 1);

    private final PermisoInstalacionRepository repo = mock(PermisoInstalacionRepository.class);
    private final PermisoAprobadoAdapter adaptador = new PermisoAprobadoAdapter(repo, RELOJ);

    @Test
    void sitioTienePermisoAprobado_delegaEnLaConsultaPorEstado() {
        when(repo.existsBySitioIdAndEstado(SITIO, EstadoPermisoInstalacion.APROBADO)).thenReturn(true);

        assertThat(adaptador.sitioTienePermisoAprobado(SITIO)).isTrue();
        verify(repo).existsBySitioIdAndEstado(SITIO, EstadoPermisoInstalacion.APROBADO);
    }

    @Test
    void sitioTienePermisoVigente_consultaAprobadoYNoVencidoAHoy() {
        when(repo.existsBySitioIdAndEstadoAndFechaVencimientoGreaterThanEqual(
                SITIO, EstadoPermisoInstalacion.APROBADO, HOY)).thenReturn(true);

        assertThat(adaptador.sitioTienePermisoVigente(SITIO)).isTrue();
        // La vigencia usa la fecha de HOY del reloj inyectado (no la de aprobacion).
        verify(repo).existsBySitioIdAndEstadoAndFechaVencimientoGreaterThanEqual(
                SITIO, EstadoPermisoInstalacion.APROBADO, HOY);
    }

    @Test
    void sitioTienePermisoVigente_esFalsoSiNoHayAprobadoVigente() {
        when(repo.existsBySitioIdAndEstadoAndFechaVencimientoGreaterThanEqual(
                eq(SITIO), eq(EstadoPermisoInstalacion.APROBADO), eq(HOY))).thenReturn(false);

        assertThat(adaptador.sitioTienePermisoVigente(SITIO)).isFalse();
    }

    @Test
    void sitioNuloEsFalsoSinConsultar() {
        assertThat(adaptador.sitioTienePermisoAprobado(null)).isFalse();
        assertThat(adaptador.sitioTienePermisoVigente(null)).isFalse();
    }
}
