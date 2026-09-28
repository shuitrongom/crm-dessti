package com.dessti.crm.contabilidad.polizas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas del dominio del cierre de periodo contable: la maquina de estados pura
 * ({@link EstadoPeriodo}) y las transiciones de {@link PeriodoContable}
 * (cerrar/reabrir), incluidos los rechazos por transicion invalida (409) y por
 * motivo de reapertura vacio (422), y la validacion de rango de anio/mes.
 */
class PeriodoContableTest {

    private static final String ACTOR = "contador@empresa";

    // ------------------------------------------------------------------
    // Maquina de estados pura (EstadoPeriodo)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La maquina permite abierto->cerrado y cerrado->abierto")
    void transicionesValidas() {
        assertThat(EstadoPeriodo.ABIERTO.puedeTransicionarA(EstadoPeriodo.CERRADO)).isTrue();
        assertThat(EstadoPeriodo.CERRADO.puedeTransicionarA(EstadoPeriodo.ABIERTO)).isTrue();
    }

    @Test
    @DisplayName("La maquina rechaza transiciones a si mismo (abierto->abierto, cerrado->cerrado)")
    void transicionesInvalidas() {
        assertThat(EstadoPeriodo.ABIERTO.puedeTransicionarA(EstadoPeriodo.ABIERTO)).isFalse();
        assertThat(EstadoPeriodo.CERRADO.puedeTransicionarA(EstadoPeriodo.CERRADO)).isFalse();
    }

    @Test
    @DisplayName("valorBd y desdeValorBd son inversas y desdeValorBd normaliza mayusculas/espacios")
    void serializacionEstado() {
        assertThat(EstadoPeriodo.ABIERTO.valorBd()).isEqualTo("abierto");
        assertThat(EstadoPeriodo.CERRADO.valorBd()).isEqualTo("cerrado");
        assertThat(EstadoPeriodo.desdeValorBd("  CERRADO ")).isEqualTo(EstadoPeriodo.CERRADO);
        assertThatThrownBy(() -> EstadoPeriodo.desdeValorBd("otro"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // Entidad PeriodoContable
    // ------------------------------------------------------------------

    @Test
    @DisplayName("crearCerrado produce un periodo cerrado con fecha de cierre y actor")
    void crearCerrado() {
        TenantContext.set(UUID.randomUUID());
        try {
            PeriodoContable p = PeriodoContable.crearCerrado(2026, 3, ACTOR);
            assertThat(p.getAnio()).isEqualTo(2026);
            assertThat(p.getMes()).isEqualTo(3);
            assertThat(p.getEstado()).isEqualTo(EstadoPeriodo.CERRADO);
            assertThat(p.estaCerrado()).isTrue();
            assertThat(p.getFechaCierre()).isNotNull();
            assertThat(p.getCerradoPor()).isEqualTo(ACTOR);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("cerrar un periodo ya cerrado lanza TransicionInvalidaException (409)")
    void cerrarYaCerradoRechaza() {
        TenantContext.set(UUID.randomUUID());
        try {
            PeriodoContable p = PeriodoContable.crearCerrado(2026, 3, ACTOR);
            assertThatThrownBy(() -> p.cerrar(ACTOR))
                    .isInstanceOf(TransicionInvalidaException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("reabrir con motivo pasa cerrado->abierto y registra motivo, fecha y actor")
    void reabrirConMotivo() {
        TenantContext.set(UUID.randomUUID());
        try {
            PeriodoContable p = PeriodoContable.crearCerrado(2026, 3, ACTOR);
            p.reabrir("Ajuste solicitado por revision", "revisor@empresa");
            assertThat(p.getEstado()).isEqualTo(EstadoPeriodo.ABIERTO);
            assertThat(p.estaCerrado()).isFalse();
            assertThat(p.getFechaReapertura()).isNotNull();
            assertThat(p.getReabiertoPor()).isEqualTo("revisor@empresa");
            assertThat(p.getMotivoReapertura()).isEqualTo("Ajuste solicitado por revision");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("reabrir con motivo vacio lanza ReglaNegocioException (422)")
    void reabrirSinMotivoRechaza() {
        TenantContext.set(UUID.randomUUID());
        try {
            PeriodoContable p = PeriodoContable.crearCerrado(2026, 3, ACTOR);
            assertThatThrownBy(() -> p.reabrir("   ", ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("reabrir un periodo no cerrado lanza TransicionInvalidaException (409)")
    void reabrirNoCerradoRechaza() {
        TenantContext.set(UUID.randomUUID());
        try {
            // Se cierra y se reabre; un segundo reabrir sobre el estado abierto debe fallar.
            PeriodoContable p = PeriodoContable.crearCerrado(2026, 3, ACTOR);
            p.reabrir("Primera reapertura", ACTOR);
            assertThatThrownBy(() -> p.reabrir("Segunda reapertura", ACTOR))
                    .isInstanceOf(TransicionInvalidaException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("validarAnioMes rechaza anio y mes fuera de rango (422)")
    void validarAnioMesFueraDeRango() {
        assertThatThrownBy(() -> PeriodoContable.validarAnioMes(1999, 1))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> PeriodoContable.validarAnioMes(2101, 1))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> PeriodoContable.validarAnioMes(2026, 0))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> PeriodoContable.validarAnioMes(2026, 13))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
