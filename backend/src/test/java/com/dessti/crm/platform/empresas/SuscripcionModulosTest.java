package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del override por Empresa del subconjunto de modulos
 * habilitados sobre la {@link Suscripcion} (Req 25.4). Verifican la semantica
 * critica null-vs-vacio, la normalizacion (recorte + minusculas + dedup) y las
 * comprobaciones {@code tieneOverrideModulos}/{@code tieneModulo}.
 */
class SuscripcionModulosTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static Suscripcion nueva() {
        return Suscripcion.crear(TENANT, PLAN_ID, LocalDate.of(2025, 1, 1), null, "super");
    }

    @Test
    @DisplayName("Recien creada NO tiene override: hereda todos los modulos del Plan (null)")
    void sinOverridePorDefecto() {
        Suscripcion s = nueva();
        assertThat(s.tieneOverrideModulos()).isFalse();
        assertThat(s.getModulosHabilitados()).isNull();
        // Sin override, tieneModulo siempre es false (el adaptador recurre al Plan).
        assertThat(s.tieneModulo("facturacion")).isFalse();
    }

    @Test
    @DisplayName("Asignar un subconjunto fija el override y normaliza (minusculas/recorte/dedup)")
    void asignarSubconjuntoNormaliza() {
        Suscripcion s = nueva();
        s.asignarModulos(Set.of("  Comercial ", "FACTURACION", "comercial"), "super");

        assertThat(s.tieneOverrideModulos()).isTrue();
        assertThat(s.getModulosHabilitados())
                .containsExactlyInAnyOrder("comercial", "facturacion");
        assertThat(s.tieneModulo("comercial")).isTrue();
        assertThat(s.tieneModulo("  FACTURACION ")).isTrue(); // insensible a caso/espacios
        assertThat(s.tieneModulo("inventario")).isFalse();
    }

    @Test
    @DisplayName("Override vacio = cero modulos habilitados (todos denegados)")
    void overrideVacioDeniegaTodo() {
        Suscripcion s = nueva();
        s.asignarModulos(Set.of(), "super");

        assertThat(s.tieneOverrideModulos()).isTrue();
        assertThat(s.getModulosHabilitados()).isEmpty();
        assertThat(s.tieneModulo("comercial")).isFalse();
        assertThat(s.tieneModulo("facturacion")).isFalse();
    }

    @Test
    @DisplayName("Asignar null limpia el override: vuelve a heredar del Plan")
    void asignarNullLimpiaOverride() {
        Suscripcion s = nueva();
        s.asignarModulos(Set.of("comercial"), "super");
        assertThat(s.tieneOverrideModulos()).isTrue();

        s.asignarModulos(null, "super");
        assertThat(s.tieneOverrideModulos()).isFalse();
        assertThat(s.getModulosHabilitados()).isNull();
    }

    @Test
    @DisplayName("El getter devuelve una copia inmutable cuando hay override")
    void getterCopiaInmutable() {
        Suscripcion s = nueva();
        s.asignarModulos(Set.of("comercial"), "super");
        List<String> modulos = s.getModulosHabilitados();
        assertThat(modulos).containsExactly("comercial");
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> modulos.add("otro"));
    }

    @Test
    @DisplayName("tieneModulo trata nulos/vacios como no habilitado")
    void tieneModuloEntradasInvalidas() {
        Suscripcion s = nueva();
        s.asignarModulos(Set.of("comercial"), "super");
        assertThat(s.tieneModulo(null)).isFalse();
        assertThat(s.tieneModulo("  ")).isFalse();
    }
}