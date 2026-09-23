package com.dessti.crm.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del calculo del hash encadenado de auditoria
 * (Req 10.4, 10.7). Verifican determinismo, encadenamiento y sensibilidad a
 * cambios de contenido, sin necesidad de base de datos.
 */
class CalculadoraHashCadenaTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant TS = Instant.parse("2024-01-15T10:30:00Z");

    private String hash(String actor, String hashPrevio) {
        return CalculadoraHashCadena.calcular(
                TENANT, actor, "crear", "cliente",
                "detalle", "{\"a\":1}", "{\"a\":2}", "trace-abc", TS, hashPrevio);
    }

    @Test
    @DisplayName("hash_actual es SHA-256(contenido || hash_previo), determinista y de 64 hex")
    void hashEsDeterministaYSha256() {
        String h1 = hash("juan", CalculadoraHashCadena.HASH_SEMILLA);
        String h2 = hash("juan", CalculadoraHashCadena.HASH_SEMILLA);

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Dos secuencias identicas de eventos producen la misma cadena de hashes")
    void secuenciasIdenticasProducenMismaCadena() {
        // Cadena A: genesis -> registro1 -> registro2
        String a0 = CalculadoraHashCadena.HASH_SEMILLA;
        String a1 = hash("ana", a0);
        String a2 = hash("luis", a1);

        // Cadena B: misma secuencia de eventos
        String b0 = CalculadoraHashCadena.HASH_SEMILLA;
        String b1 = hash("ana", b0);
        String b2 = hash("luis", b1);

        assertThat(a1).isEqualTo(b1);
        assertThat(a2).isEqualTo(b2);
    }

    @Test
    @DisplayName("El hash depende del hash_previo (encadenamiento efectivo)")
    void hashDependeDelHashPrevio() {
        String conSemilla = hash("ana", CalculadoraHashCadena.HASH_SEMILLA);
        String conOtroPrevio = hash("ana", "a".repeat(64));

        assertThat(conSemilla).isNotEqualTo(conOtroPrevio);
    }

    @Test
    @DisplayName("Cambiar cualquier campo cambia el hash (manipulacion detectable)")
    void cambiarUnCampoCambiaElHash() {
        String base = hash("ana", CalculadoraHashCadena.HASH_SEMILLA);

        String actorDistinto = hash("ana2", CalculadoraHashCadena.HASH_SEMILLA);
        String recursoDistinto = CalculadoraHashCadena.calcular(
                TENANT, "ana", "crear", "factura",
                "detalle", "{\"a\":1}", "{\"a\":2}", "trace-abc", TS,
                CalculadoraHashCadena.HASH_SEMILLA);
        String tenantDistinto = CalculadoraHashCadena.calcular(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "ana", "crear", "cliente",
                "detalle", "{\"a\":1}", "{\"a\":2}", "trace-abc", TS,
                CalculadoraHashCadena.HASH_SEMILLA);
        String valorNuevoDistinto = CalculadoraHashCadena.calcular(
                TENANT, "ana", "crear", "cliente",
                "detalle", "{\"a\":1}", "{\"a\":999}", "trace-abc", TS,
                CalculadoraHashCadena.HASH_SEMILLA);

        assertThat(base)
                .isNotEqualTo(actorDistinto)
                .isNotEqualTo(recursoDistinto)
                .isNotEqualTo(tenantDistinto)
                .isNotEqualTo(valorNuevoDistinto);
    }

    @Test
    @DisplayName("Distingue tenant nulo (plataforma) de campo vacio y no colisiona")
    void distingueNuloDeVacio() {
        String tenantNulo = CalculadoraHashCadena.calcular(
                null, "sysadmin", "login", "sesion",
                null, null, null, null, TS, CalculadoraHashCadena.HASH_SEMILLA);
        String tenantVacioSimulado = CalculadoraHashCadena.calcular(
                null, "sysadmin", "login", "sesion",
                "", "", "", "", TS, CalculadoraHashCadena.HASH_SEMILLA);

        assertThat(tenantNulo).isNotEqualTo(tenantVacioSimulado);
        assertThat(tenantNulo).hasSize(64);
    }

    @Test
    @DisplayName("sha256Hex coincide con un vector conocido de SHA-256")
    void sha256VectorConocido() {
        // SHA-256("abc") es un vector estandar conocido.
        assertThat(CalculadoraHashCadena.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
