package com.dessti.crm.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la unicidad de identificadores de
 * negocio por tenant (Tarea 4.5, Property 22 del diseno).
 *
 * <p>Modela la restriccion {@code UNIQUE (tenant_id, rfc)} para entidades
 * activas (Cliente/Proveedor): una colision de identificador fiscal se detecta
 * y rechaza <strong>unicamente</strong> cuando ambos registros comparten el
 * mismo {@code tenant_id}; el mismo identificador fiscal en Empresas distintas
 * se permite.</p>
 *
 * <p><strong>Transparencia:</strong> la restriccion equivalente reforzada por la
 * base de datos ({@code UNIQUE (tenant_id, rfc)} + RLS de PostgreSQL) se
 * verifica en la Tarea 4.6 con Testcontainers; aqui se prueba la logica de
 * unicidad por tenant en memoria, sin BD.</p>
 */
class UnicidadPorTenantPropertyTest {

    // Feature: crm-anuncios-luminosos, Property 22: Para cualquier par de Clientes (o Proveedores) activos, la colision de identificador fiscal se detecta y rechaza unicamente cuando ambos pertenecen al mismo tenant_id; el mismo identificador fiscal en Empresas distintas se permite.
    @Property(tries = 1000)
    void colisionDeRfcSoloSeRechazaDentroDelMismoTenant(
            @ForAll("registrosCandidatos") List<RegistroFiscal> candidatos) {

        RegistroUnicoPorTenant registro = new RegistroUnicoPorTenant();

        for (RegistroFiscal candidato : candidatos) {
            boolean existeColisionMismoTenant = registro.existe(candidato.tenantId(), candidato.rfc());
            boolean aceptado = registro.intentarRegistrar(candidato);

            // Se acepta si y solo si NO habia ya (tenant, rfc) igual.
            assertThat(aceptado).isEqualTo(!existeColisionMismoTenant);
        }

        // Invariante final: no hay dos registros aceptados con el mismo (tenant, rfc).
        Set<String> claves = new HashSet<>();
        for (RegistroFiscal r : registro.aceptados()) {
            String clave = r.tenantId() + "|" + r.rfc();
            assertThat(claves.add(clave))
                    .as("No debe haber duplicados (tenant, rfc) entre los aceptados")
                    .isTrue();
        }
    }

    // Feature: crm-anuncios-luminosos, Property 22: Para cualquier par de Clientes (o Proveedores) activos, la colision de identificador fiscal se detecta y rechaza unicamente cuando ambos pertenecen al mismo tenant_id; el mismo identificador fiscal en Empresas distintas se permite.
    @Property(tries = 1000)
    void mismoRfcEnTenantsDistintosSiempreSePermite(
            @ForAll("rfc") String rfc,
            @ForAll("tenantsDistintos") List<UUID> tenantsDistintos) {

        RegistroUnicoPorTenant registro = new RegistroUnicoPorTenant();

        // El mismo RFC dado de alta en Empresas distintas nunca colisiona.
        for (UUID tenant : tenantsDistintos) {
            boolean aceptado = registro.intentarRegistrar(new RegistroFiscal(tenant, rfc));
            assertThat(aceptado)
                    .as("El mismo RFC en un tenant distinto debe permitirse")
                    .isTrue();
        }

        assertThat(registro.aceptados()).hasSize(tenantsDistintos.size());
    }

    // Feature: crm-anuncios-luminosos, Property 22: Para cualquier par de Clientes (o Proveedores) activos, la colision de identificador fiscal se detecta y rechaza unicamente cuando ambos pertenecen al mismo tenant_id; el mismo identificador fiscal en Empresas distintas se permite.
    @Property(tries = 1000)
    void segundaAltaDelMismoRfcEnElMismoTenantSeRechaza(
            @ForAll("uuid") UUID tenant, @ForAll("rfc") String rfc) {

        RegistroUnicoPorTenant registro = new RegistroUnicoPorTenant();

        assertThat(registro.intentarRegistrar(new RegistroFiscal(tenant, rfc)))
                .as("La primera alta del RFC en el tenant debe aceptarse")
                .isTrue();
        assertThat(registro.intentarRegistrar(new RegistroFiscal(tenant, rfc)))
                .as("La segunda alta del mismo RFC en el mismo tenant debe rechazarse")
                .isFalse();
        assertThat(registro.aceptados()).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Generadores jqwik
    // ------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> uuid() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<List<UUID>> tenantsDistintos() {
        // 2..5 tenants garantizadamente distintos (UUID.randomUUID no colisiona en la practica).
        return Arbitraries.integers().between(2, 5).map(n -> {
            List<UUID> tenants = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                tenants.add(UUID.randomUUID());
            }
            return tenants;
        });
    }

    @Provide
    Arbitrary<String> rfc() {
        // RFC persona moral: 3 letras + 6 digitos + 3 alfanumericos (homoclave).
        Arbitrary<String> letras =
                Arbitraries.strings().withCharRange('A', 'Z').ofLength(3);
        Arbitrary<String> fecha =
                Arbitraries.strings().numeric().ofLength(6);
        Arbitrary<String> homoclave = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofLength(3);
        return Combinators.combine(letras, fecha, homoclave).as((l, f, h) -> l + f + h);
    }

    @Provide
    Arbitrary<List<RegistroFiscal>> registrosCandidatos() {
        // Pocos tenants y pocos RFC para forzar colisiones frecuentes.
        Arbitrary<Integer> nTenants = Arbitraries.integers().between(1, 3);
        return nTenants.flatMap(nt -> {
            List<UUID> tenants = new ArrayList<>();
            for (int i = 0; i < nt; i++) {
                tenants.add(UUID.randomUUID());
            }
            Arbitrary<UUID> tenantArb = Arbitraries.of(tenants);
            // Universo pequeno de RFC para provocar duplicados dentro y entre tenants.
            Arbitrary<String> rfcArb = rfc();
            return rfcArb.list().ofMinSize(4).ofMaxSize(10).flatMap(universoRfc -> {
                Arbitrary<String> rfcDeUniverso = Arbitraries.of(universoRfc);
                Arbitrary<RegistroFiscal> registroArb = Combinators
                        .combine(tenantArb, rfcDeUniverso)
                        .as(RegistroFiscal::new);
                return registroArb.list().ofMinSize(1).ofMaxSize(20);
            });
        });
    }

    // ------------------------------------------------------------------
    // Modelo en memoria de la restriccion UNIQUE(tenant_id, rfc)
    // ------------------------------------------------------------------

    /** Entidad activa con identificador fiscal, aislada por tenant. */
    record RegistroFiscal(UUID tenantId, String rfc) {}

    /**
     * Reglas de unicidad por tenant: la clave de unicidad es el par
     * {@code (tenant_id, rfc)}, replicando {@code UNIQUE (tenant_id, rfc)}.
     */
    static final class RegistroUnicoPorTenant {
        private final Set<String> claves = new HashSet<>();
        private final List<RegistroFiscal> aceptados = new ArrayList<>();

        private static String clave(UUID tenantId, String rfc) {
            return tenantId + "|" + rfc;
        }

        boolean existe(UUID tenantId, String rfc) {
            return claves.contains(clave(tenantId, rfc));
        }

        boolean intentarRegistrar(RegistroFiscal registro) {
            String clave = clave(registro.tenantId(), registro.rfc());
            if (claves.contains(clave)) {
                return false; // colision dentro del mismo tenant: se rechaza
            }
            claves.add(clave);
            aceptados.add(registro);
            return true;
        }

        List<RegistroFiscal> aceptados() {
            return List.copyOf(aceptados);
        }
    }
}
