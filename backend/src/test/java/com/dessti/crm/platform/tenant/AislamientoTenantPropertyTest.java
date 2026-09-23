package com.dessti.crm.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterProperty;

/**
 * Pruebas basadas en propiedades (jqwik) del aislamiento multi-empresa
 * (Tarea 4.4, Property 1 del diseno).
 *
 * <p>Estas pruebas validan la <strong>invariante de diseno</strong> del
 * aislamiento por {@code tenant_id} (Capa 1: discriminador en la aplicacion)
 * de forma property-based sobre un almacen multi-tenant en memoria que replica
 * la misma regla que impondran los repositorios reales (tareas 15+): toda
 * lectura, modificacion o refetch se restringe a registros cuyo
 * {@code tenant_id} coincide con el del {@link TenantContext} (derivado del
 * JWT), un acceso a otro tenant resulta en "no encontrado" (equivalente al 404,
 * sin revelar existencia) y el {@code tenant_id} nunca se toma de un parametro
 * de la peticion.</p>
 *
 * <p><strong>Transparencia:</strong> la garantia equivalente a nivel de base de
 * datos (Row-Level Security de PostgreSQL, segunda capa de defensa) se verifica
 * en la Tarea 4.6 con Testcontainers; aqui se prueba unicamente la logica de la
 * capa de aplicacion, en memoria y sin BD.</p>
 */
class AislamientoTenantPropertyTest {

    @AfterProperty
    void limpiarContexto() {
        // Evita fugas del ThreadLocal entre iteraciones/propiedades.
        TenantContext.clear();
    }

    // Feature: crm-anuncios-luminosos, Property 1: Para cualquier conjunto de datos de negocio distribuidos entre varias Empresas y para cualquier Usuario autenticado que no sea Super_Administrador, toda operacion de lectura, modificacion o referencia devuelve o afecta unicamente registros cuyo tenant_id coincide con la Empresa del Usuario; cualquier intento de acceder a un recurso de otra Empresa resulta en 404 y el tenant_id nunca se toma de parametros de la peticion.
    @Property(tries = 1000)
    void lecturasSoloDevuelvenRegistrosDelTenantDelContexto(
            @ForAll("escenarios") EscenarioMultiTenant escenario) {

        TenantStore store = escenario.construirStore();

        for (UUID tenant : escenario.tenants()) {
            TenantContext.set(tenant);
            try {
                List<Registro> visibles = store.listar();

                // Toda operacion de lectura devuelve unicamente registros del tenant del contexto.
                assertThat(visibles)
                        .allSatisfy(r -> assertThat(r.tenantId()).isEqualTo(tenant));

                // La lista visible es exactamente el subconjunto esperado para ese tenant.
                assertThat(visibles)
                        .containsExactlyInAnyOrderElementsOf(escenario.registrosDe(tenant));
            } finally {
                TenantContext.clear();
            }
        }
    }

    // Feature: crm-anuncios-luminosos, Property 1: Para cualquier conjunto de datos de negocio distribuidos entre varias Empresas y para cualquier Usuario autenticado que no sea Super_Administrador, toda operacion de lectura, modificacion o referencia devuelve o afecta unicamente registros cuyo tenant_id coincide con la Empresa del Usuario; cualquier intento de acceder a un recurso de otra Empresa resulta en 404 y el tenant_id nunca se toma de parametros de la peticion.
    @Property(tries = 1000)
    void accesoARecursoDeOtroTenantResultaEnNoEncontrado(
            @ForAll("escenarios") EscenarioMultiTenant escenario) {

        TenantStore store = escenario.construirStore();
        List<Registro> todos = escenario.todosLosRegistros();

        for (UUID tenantContexto : escenario.tenants()) {
            TenantContext.set(tenantContexto);
            try {
                for (Registro objetivo : todos) {
                    Optional<Registro> encontrado = store.buscarPorId(objetivo.id());
                    boolean intentoModificar = store.modificar(objetivo.id(), "nuevo-valor");

                    if (objetivo.tenantId().equals(tenantContexto)) {
                        // Recurso del propio tenant: visible y modificable.
                        assertThat(encontrado).isPresent();
                        assertThat(encontrado.get().tenantId()).isEqualTo(tenantContexto);
                        assertThat(intentoModificar).isTrue();
                    } else {
                        // Recurso de otro tenant: "no encontrado" (equivale a 404, sin revelar existencia).
                        assertThat(encontrado).isEmpty();
                        assertThat(intentoModificar).isFalse();
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }
    }

    // Feature: crm-anuncios-luminosos, Property 1: Para cualquier conjunto de datos de negocio distribuidos entre varias Empresas y para cualquier Usuario autenticado que no sea Super_Administrador, toda operacion de lectura, modificacion o referencia devuelve o afecta unicamente registros cuyo tenant_id coincide con la Empresa del Usuario; cualquier intento de acceder a un recurso de otra Empresa resulta en 404 y el tenant_id nunca se toma de parametros de la peticion.
    @Property(tries = 1000)
    void tenantIdSeTomaDelContextoNoDeParametros(
            @ForAll("escenarios") EscenarioMultiTenant escenario,
            @ForAll("uuid") UUID tenantParametroMalicioso) {

        TenantStore store = escenario.construirStore();

        for (UUID tenantContexto : escenario.tenants()) {
            TenantContext.set(tenantContexto);
            try {
                // Un supuesto "parametro de peticion" con otro tenant NO altera el aislamiento:
                // la vista sigue restringida al tenant del contexto, ignorando el parametro.
                List<Registro> visiblesConParametro =
                        store.listarIgnorandoParametro(tenantParametroMalicioso);

                assertThat(visiblesConParametro)
                        .allSatisfy(r -> assertThat(r.tenantId()).isEqualTo(tenantContexto));
                assertThat(visiblesConParametro)
                        .containsExactlyInAnyOrderElementsOf(escenario.registrosDe(tenantContexto));
            } finally {
                TenantContext.clear();
            }
        }
    }

    // Feature: crm-anuncios-luminosos, Property 1: Para cualquier conjunto de datos de negocio distribuidos entre varias Empresas y para cualquier Usuario autenticado que no sea Super_Administrador, toda operacion de lectura, modificacion o referencia devuelve o afecta unicamente registros cuyo tenant_id coincide con la Empresa del Usuario; cualquier intento de acceder a un recurso de otra Empresa resulta en 404 y el tenant_id nunca se toma de parametros de la peticion.
    @Property(tries = 1000)
    void prePersistAsignaTenantDelContextoYRequireFallaSinContexto(
            @ForAll("uuid") UUID tenantContexto) {

        // Sin contexto, require() debe fallar: no se puede persistir un registro multi-tenant.
        TenantContext.clear();
        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(TenantContextException.class);

        // Con contexto, el @PrePersist de TenantScopedEntity asigna el tenant del contexto,
        // nunca uno provisto por el codigo de negocio.
        TenantContext.set(tenantContexto);
        try {
            EntidadDePrueba entidad = new EntidadDePrueba();
            entidad.simularPrePersist();
            assertThat(entidad.getTenantId()).isEqualTo(tenantContexto);
            assertThat(entidad.getCreatedAt()).isNotNull();
            assertThat(entidad.getUpdatedAt()).isNotNull();
        } finally {
            TenantContext.clear();
        }
    }

    // ------------------------------------------------------------------
    // Generadores jqwik
    // ------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> uuid() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<EscenarioMultiTenant> escenarios() {
        // Entre 2 y 5 empresas distintas.
        Arbitrary<Integer> numeroTenants = Arbitraries.integers().between(2, 5);
        return numeroTenants.flatMap(n -> {
            List<UUID> tenants = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                tenants.add(UUID.randomUUID());
            }
            // Entre 0 y 6 registros por tenant.
            Arbitrary<List<Integer>> conteos =
                    Arbitraries.integers().between(0, 6).list().ofSize(n);
            return conteos.map(cs -> new EscenarioMultiTenant(tenants, cs));
        });
    }

    // ------------------------------------------------------------------
    // Modelo/dobles en memoria que replican la invariante del diseno
    // ------------------------------------------------------------------

    /** Registro de negocio multi-tenant minimo para la prueba. */
    record Registro(UUID id, UUID tenantId, String valor) {}

    /** Escenario de datos distribuidos entre varias empresas. */
    static final class EscenarioMultiTenant {
        private final List<UUID> tenants;
        private final List<Registro> registros = new ArrayList<>();

        EscenarioMultiTenant(List<UUID> tenants, List<Integer> conteoPorTenant) {
            this.tenants = tenants;
            for (int i = 0; i < tenants.size(); i++) {
                UUID t = tenants.get(i);
                int cuantos = conteoPorTenant.get(i);
                for (int j = 0; j < cuantos; j++) {
                    registros.add(new Registro(UUID.randomUUID(), t, "valor-" + i + "-" + j));
                }
            }
        }

        List<UUID> tenants() {
            return tenants;
        }

        List<Registro> todosLosRegistros() {
            return List.copyOf(registros);
        }

        List<Registro> registrosDe(UUID tenant) {
            return registros.stream().filter(r -> r.tenantId().equals(tenant)).toList();
        }

        TenantStore construirStore() {
            return new TenantStore(registros);
        }
    }

    /**
     * Almacen en memoria que aplica la MISMA regla que un repositorio real
     * multi-tenant: el {@code tenant_id} efectivo proviene del
     * {@link TenantContext}, jamas de un parametro.
     */
    static final class TenantStore {
        private final Map<UUID, Registro> porId = new HashMap<>();

        TenantStore(List<Registro> semilla) {
            for (Registro r : semilla) {
                porId.put(r.id(), r);
            }
        }

        List<Registro> listar() {
            UUID tenant = TenantContext.require();
            return porId.values().stream().filter(r -> r.tenantId().equals(tenant)).toList();
        }

        /** El parametro se ignora deliberadamente: el aislamiento usa el contexto. */
        List<Registro> listarIgnorandoParametro(UUID tenantParametro) {
            return listar();
        }

        Optional<Registro> buscarPorId(UUID id) {
            UUID tenant = TenantContext.require();
            Registro r = porId.get(id);
            if (r == null || !r.tenantId().equals(tenant)) {
                return Optional.empty(); // equivale a 404, no revela existencia
            }
            return Optional.of(r);
        }

        boolean modificar(UUID id, String nuevoValor) {
            UUID tenant = TenantContext.require();
            Registro r = porId.get(id);
            if (r == null || !r.tenantId().equals(tenant)) {
                return false; // recurso de otro tenant: no se afecta nada
            }
            porId.put(id, new Registro(id, tenant, nuevoValor));
            return true;
        }
    }

    /**
     * Entidad de prueba que extiende {@link TenantScopedEntity} para verificar el
     * comportamiento del {@code @PrePersist} sin JPA/BD. Expone el hook protegido
     * a traves de {@link #simularPrePersist()}.
     */
    static final class EntidadDePrueba extends TenantScopedEntity {
        void simularPrePersist() {
            onPersistAssignTenant();
        }
    }
}
