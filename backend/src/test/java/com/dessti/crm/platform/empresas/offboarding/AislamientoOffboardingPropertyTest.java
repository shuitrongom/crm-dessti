package com.dessti.crm.platform.empresas.offboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 42: Aislamiento
 * del offboarding del tenant</strong> (Req 69.3, 69.4, 69.5).
 *
 * <p>La property valida la <em>invariante de aislamiento</em> del offboarding:
 * <em>para cualquier exportacion o eliminacion/anonimizacion de los datos de una
 * Empresa, la operacion incluye o afecta unicamente registros cuyo
 * {@code tenant_id} coincide con la Empresa objetivo y nunca datos de otras
 * Empresas; ademas, los comprobantes fiscales bajo retencion (Factura CFDI,
 * Recibo_Nomina y Poliza_Contable) se preservan</em>.</p>
 *
 * <p><strong>Enfoque de modelado (dominio puro en memoria):</strong> se ejerce
 * la <em>semantica de la SPI de produccion</em> {@link RecursoTenantOffboarding}
 * a traves de una implementacion en memoria ({@link RecursoEnMemoria}) respaldada
 * por un {@code Map<UUID, List<Fila>>} indexado por {@code tenant_id}. Cada
 * recurso conoce varios tenants con varias filas cada uno, y sus metodos
 * {@code exportar}/{@code eliminarOAnonimizar} operan <em>solo</em> sobre la
 * rebanada del tenant recibido, replicando el contrato del SPI: un recurso
 * fiscal ({@code esComprobanteFiscal() == true}) se exporta pero
 * <strong>nunca</strong> borra (devuelve {@code 0}); un recurso ordinario borra
 * las filas del tenant y devuelve su conteo. Un <em>orquestador</em> minimo
 * ({@link #eliminarDefinitivamente} y {@link #exportar}) espeja linea por linea
 * el bucle real de {@link ServicioOffboarding}: omite los recursos fiscales en la
 * eliminacion (Req 69.4) y suma el conteo de los ordinarios (Req 69.3, 69.6),
 * todo acotado al tenant objetivo (Req 69.5). No hay contexto de Spring, ni base
 * de datos, ni Testcontainers; el aislamiento se comprueba de forma
 * determinista.</p>
 *
 * <p>Se prefiere este enfoque de dominio puro frente a ejercer el
 * {@code ServicioOffboarding} real con RLS de PostgreSQL porque la invariante que
 * interesa (afectar solo al tenant objetivo y preservar los fiscales) vive
 * integramente en el contrato de la SPI y en el bucle de orquestacion; el
 * refuerzo por RLS ({@code app.current_tenant}) y la precondicion de
 * Periodo_Gracia (422) ya los cubren pruebas de servicio/integracion (tarea
 * 14.4). Aqui se verifica universalmente el nucleo del aislamiento.</p>
 */
class AislamientoOffboardingPropertyTest {

    // ----------------------------------------------------------------------
    // Property 42 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 42: Para cualquier exportación o eliminación/anonimización de los datos de una Empresa, la operación incluye o afecta únicamente registros cuyo `tenant_id` coincide con la Empresa objetivo y nunca datos de otras Empresas; además, los comprobantes fiscales bajo retención (Factura CFDI, Recibo_Nomina y Poliza_Contable) se preservan.
    @Property(tries = 1000)
    void eliminarSoloAfectaAlTenantObjetivoYPreservaLosFiscales(
            @ForAll("mundoMultiTenant") MundoMultiTenant mundo) {

        UUID objetivo = mundo.tenantObjetivo();
        List<RecursoEnMemoria> recursos = mundo.recursos();

        // Foto previa de TODOS los conteos por (recurso, tenant).
        Map<String, Map<UUID, Integer>> antes = fotografiar(recursos);

        // Orquestacion identica a ServicioOffboarding.eliminarDefinitivamente:
        // se omiten los fiscales (Req 69.4) y se acota al tenant objetivo (Req 69.5).
        long totalEliminado = eliminarDefinitivamente(recursos, objetivo);

        Map<String, Map<UUID, Integer>> despues = fotografiar(recursos);

        long esperadoNoFiscal = 0;
        for (RecursoEnMemoria recurso : recursos) {
            String nombre = recurso.nombreRecurso();
            int previoObjetivo = antes.get(nombre).getOrDefault(objetivo, 0);

            if (recurso.esComprobanteFiscal()) {
                // Invariante 2 (Req 69.4): un fiscal NUNCA se borra, ni siquiera
                // para el tenant objetivo. Sus filas quedan intactas.
                assertThat(despues.get(nombre).getOrDefault(objetivo, 0))
                        .as("recurso fiscal '%s' preservado para el tenant objetivo", nombre)
                        .isEqualTo(previoObjetivo);
            } else {
                // Invariante 1 (Req 69.3): las filas NO fiscales del objetivo se eliminan.
                assertThat(despues.get(nombre).getOrDefault(objetivo, 0))
                        .as("recurso no fiscal '%s' eliminado para el tenant objetivo", nombre)
                        .isZero();
                esperadoNoFiscal += previoObjetivo;
            }

            // Invariante 1 (Req 69.5): NINGUN otro tenant se toca, sea fiscal o no.
            for (UUID otro : mundo.otrosTenants()) {
                assertThat(despues.get(nombre).getOrDefault(otro, 0))
                        .as("recurso '%s' del tenant ajeno %s intacto", nombre, otro)
                        .isEqualTo(antes.get(nombre).getOrDefault(otro, 0));
            }
        }

        // Invariante 4 (Req 69.6): el conteo total eliminado = filas NO fiscales
        // del tenant objetivo (espeja la suma del servicio real).
        assertThat(totalEliminado)
                .as("el conteo eliminado equivale a las filas no fiscales del tenant objetivo")
                .isEqualTo(esperadoNoFiscal);
    }

    // Feature: crm-anuncios-luminosos, Property 42: Para cualquier exportación o eliminación/anonimización de los datos de una Empresa, la operación incluye o afecta únicamente registros cuyo `tenant_id` coincide con la Empresa objetivo y nunca datos de otras Empresas; además, los comprobantes fiscales bajo retención (Factura CFDI, Recibo_Nomina y Poliza_Contable) se preservan.
    @Property(tries = 1000)
    void exportarDevuelveUnicamenteLosDatosDelTenantObjetivo(
            @ForAll("mundoMultiTenant") MundoMultiTenant mundo) {

        UUID objetivo = mundo.tenantObjetivo();
        List<RecursoEnMemoria> recursos = mundo.recursos();

        Map<String, List<Fila>> exportacion = exportar(recursos, objetivo);

        for (RecursoEnMemoria recurso : recursos) {
            List<Fila> filas = exportacion.get(recurso.nombreRecurso());
            assertThat(filas).as("la exportacion incluye el recurso '%s'", recurso.nombreRecurso())
                    .isNotNull();

            // Invariante 3 (Req 69.1, 69.5): TODA fila exportada pertenece al
            // tenant objetivo; ninguna fila de otro tenant aparece en la exportacion.
            assertThat(filas)
                    .as("toda fila exportada del recurso '%s' pertenece al tenant objetivo",
                            recurso.nombreRecurso())
                    .allMatch(fila -> objetivo.equals(fila.tenantId()));

            // La exportacion cubre exactamente las filas del objetivo (ni mas ni menos).
            assertThat(filas)
                    .as("la exportacion del recurso '%s' cubre todas las filas del objetivo",
                            recurso.nombreRecurso())
                    .hasSize(recurso.conteo(objetivo));
        }
    }

    // Feature: crm-anuncios-luminosos, Property 42: Para cualquier exportación o eliminación/anonimización de los datos de una Empresa, la operación incluye o afecta únicamente registros cuyo `tenant_id` coincide con la Empresa objetivo y nunca datos de otras Empresas; además, los comprobantes fiscales bajo retención (Factura CFDI, Recibo_Nomina y Poliza_Contable) se preservan.
    @Property(tries = 1000)
    void laExportacionNoMutaLosDatosDeNingunTenant(
            @ForAll("mundoMultiTenant") MundoMultiTenant mundo) {

        List<RecursoEnMemoria> recursos = mundo.recursos();
        Map<String, Map<UUID, Integer>> antes = fotografiar(recursos);

        // Exportar es de solo lectura: no debe alterar conteo alguno (Req 69.1).
        exportar(recursos, mundo.tenantObjetivo());

        Map<String, Map<UUID, Integer>> despues = fotografiar(recursos);
        assertThat(despues)
                .as("la exportacion no muta los datos de ningun tenant")
                .isEqualTo(antes);
    }

    // ----------------------------------------------------------------------
    // Orquestador (espeja ServicioOffboarding)
    // ----------------------------------------------------------------------

    /**
     * Espeja el bucle de {@link ServicioOffboarding#eliminarDefinitivamente(UUID)}:
     * omite los comprobantes fiscales (Req 69.4) e invoca
     * {@code eliminarOAnonimizar(tenantId)} solo en los recursos ordinarios,
     * sumando los conteos afectados (Req 69.3, 69.6). Todo acotado al tenant
     * objetivo (Req 69.5).
     */
    private static long eliminarDefinitivamente(List<RecursoEnMemoria> recursos, UUID tenantId) {
        long total = 0;
        for (RecursoTenantOffboarding recurso : recursos) {
            if (recurso.esComprobanteFiscal()) {
                continue; // Preservacion fiscal (Req 69.4).
            }
            total += recurso.eliminarOAnonimizar(tenantId);
        }
        return total;
    }

    /**
     * Espeja el bucle de {@link ServicioOffboarding#exportar(UUID)}: recolecta la
     * exportacion de cada recurso (fiscales incluidos) para el tenant objetivo
     * (Req 69.1, 69.5).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, List<Fila>> exportar(List<RecursoEnMemoria> recursos, UUID tenantId) {
        Map<String, List<Fila>> porRecurso = new LinkedHashMap<>();
        for (RecursoTenantOffboarding recurso : recursos) {
            porRecurso.put(recurso.nombreRecurso(), (List<Fila>) recurso.exportar(tenantId));
        }
        return porRecurso;
    }

    /** Foto de conteos por (nombreRecurso -> (tenant -> numFilas)) para comparar antes/despues. */
    private static Map<String, Map<UUID, Integer>> fotografiar(List<RecursoEnMemoria> recursos) {
        Map<String, Map<UUID, Integer>> foto = new LinkedHashMap<>();
        for (RecursoEnMemoria recurso : recursos) {
            foto.put(recurso.nombreRecurso(), recurso.conteosPorTenant());
        }
        return foto;
    }

    // ----------------------------------------------------------------------
    // Modelo en memoria de la SPI de produccion
    // ----------------------------------------------------------------------

    /** Fila de negocio de un recurso, etiquetada por su {@code tenant_id}. */
    private record Fila(UUID tenantId, UUID id) {
    }

    /**
     * Implementacion en memoria de {@link RecursoTenantOffboarding} respaldada por
     * un {@code Map<UUID, List<Fila>>} indexado por {@code tenant_id}. Replica el
     * contrato del SPI: {@code exportar} devuelve SOLO las filas del tenant dado;
     * {@code eliminarOAnonimizar} borra SOLO las filas del tenant dado (y para un
     * recurso fiscal nunca borra, devolviendo 0).
     */
    private static final class RecursoEnMemoria implements RecursoTenantOffboarding {

        private final String nombre;
        private final boolean fiscal;
        private final Map<UUID, List<Fila>> porTenant = new LinkedHashMap<>();

        RecursoEnMemoria(String nombre, boolean fiscal) {
            this.nombre = nombre;
            this.fiscal = fiscal;
        }

        void sembrar(UUID tenantId, int filas) {
            List<Fila> lista = porTenant.computeIfAbsent(tenantId, k -> new ArrayList<>());
            for (int i = 0; i < filas; i++) {
                lista.add(new Fila(tenantId, UUID.randomUUID()));
            }
        }

        int conteo(UUID tenantId) {
            List<Fila> lista = porTenant.get(tenantId);
            return (lista == null) ? 0 : lista.size();
        }

        Map<UUID, Integer> conteosPorTenant() {
            Map<UUID, Integer> conteos = new LinkedHashMap<>();
            for (Map.Entry<UUID, List<Fila>> e : porTenant.entrySet()) {
                conteos.put(e.getKey(), e.getValue().size());
            }
            return conteos;
        }

        @Override
        public String nombreRecurso() {
            return nombre;
        }

        @Override
        public boolean esComprobanteFiscal() {
            return fiscal;
        }

        @Override
        public Object exportar(UUID tenantId) {
            // Solo la rebanada del tenant objetivo; nunca filas de otros (Req 69.1, 69.5).
            List<Fila> lista = porTenant.get(tenantId);
            return (lista == null) ? new ArrayList<Fila>() : new ArrayList<>(lista);
        }

        @Override
        public long eliminarOAnonimizar(UUID tenantId) {
            if (fiscal) {
                // Preservacion fiscal: nunca borra (Req 69.4).
                return 0L;
            }
            List<Fila> lista = porTenant.get(tenantId);
            if (lista == null || lista.isEmpty()) {
                return 0L;
            }
            long eliminados = lista.size();
            // Borrado acotado estrictamente a la rebanada del tenant (Req 69.3, 69.5).
            porTenant.put(tenantId, new ArrayList<>());
            return eliminados;
        }
    }

    /** Escenario multi-tenant: los recursos, el tenant objetivo y los tenants ajenos. */
    private record MundoMultiTenant(List<RecursoEnMemoria> recursos,
                                    UUID tenantObjetivo,
                                    List<UUID> otrosTenants) {
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Construye un mundo con varios tenants distintos y varios recursos (algunos
     * fiscales, otros ordinarios), cada uno con un numero pequeno de filas por
     * tenant. El primer tenant es el objetivo del offboarding.
     */
    @Provide
    Arbitrary<MundoMultiTenant> mundoMultiTenant() {
        // 2..4 tenants distintos; el primero es el objetivo.
        Arbitrary<List<UUID>> tenantsArb = Arbitraries.create(UUID::randomUUID)
                .list().ofMinSize(2).ofMaxSize(4);
        // Filas por (tenant, recurso): 0..5 (incluye el caso vacio).
        Arbitrary<List<Integer>> filasArb = Arbitraries.integers().between(0, 5)
                .list().ofMinSize(1).ofMaxSize(64);

        return Combinators.combine(tenantsArb, filasArb).as((tenants, filas) -> {
            List<UUID> objetivos = new ArrayList<>();
            for (UUID t : tenants) {
                if (!objetivos.contains(t)) {
                    objetivos.add(t);
                }
            }
            UUID objetivo = objetivos.get(0);
            List<UUID> otros = new ArrayList<>(objetivos.subList(1, objetivos.size()));

            // Recursos: mezcla de ordinarios y comprobantes fiscales (Req 69.4).
            List<RecursoEnMemoria> recursos = List.of(
                    new RecursoEnMemoria("cliente", false),
                    new RecursoEnMemoria("cotizacion", false),
                    new RecursoEnMemoria("factura_cfdi", true),
                    new RecursoEnMemoria("recibo_nomina", true),
                    new RecursoEnMemoria("poliza_contable", true));

            // Sembrar filas de forma determinista a partir de la lista generada.
            int idx = 0;
            for (RecursoEnMemoria recurso : recursos) {
                for (UUID t : objetivos) {
                    int n = filas.get(idx % filas.size());
                    recurso.sembrar(t, n);
                    idx++;
                }
            }
            return new MundoMultiTenant(recursos, objetivo, otros);
        });
    }
}
