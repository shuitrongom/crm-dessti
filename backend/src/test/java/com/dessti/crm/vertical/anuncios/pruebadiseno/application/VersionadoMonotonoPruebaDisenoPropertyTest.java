package com.dessti.crm.vertical.anuncios.pruebadiseno.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.pruebadiseno.adapter.out.persistence.PruebaDisenoRepository;
import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.EstadoPruebaDiseno;
import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.PruebaDiseno;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 8: Versionado
 * monotono de Pruebas de Diseno</strong> (Req 15.3, 15.4).
 *
 * <p>La property valida la <em>invariante de versionado monotono</em> del ciclo
 * de vida de las {@link PruebaDiseno}: <em>para cualquier Prueba_Diseno en estado
 * "pendiente" que sea rechazada, el sistema conserva el historial y genera una
 * nueva Prueba_Diseno con numero de version igual al anterior mas 1 y estado
 * "pendiente"</em>.</p>
 *
 * <h2>Enfoque de modelado (opcion A: servicio real + repositorio en memoria)</h2>
 * <p>Se ejerce el <strong>servicio de produccion</strong>
 * {@link ServicioPruebasDiseno} tal cual (constructor
 * {@code (PruebaDisenoRepository, CotizacionExistentePort, AuditoriaPort, Clock)}),
 * respaldado por una <strong>implementacion en memoria del
 * {@link PruebaDisenoRepository}</strong> con estado real: un
 * {@code Map<UUID, PruebaDiseno>} donde {@code save} almacena/actualiza filas por
 * id y {@code findMaxNumeroVersionByCotizacionId} devuelve el verdadero maximo de
 * {@code numero_version} de la Cotizacion. Asi el calculo {@code max + 1} del
 * servicio se comprueba contra un estado autentico, no contra un valor prefijado
 * por un mock. Se prefiere este enfoque (sobre probar solo el dominio) porque la
 * coordinacion del versionado {@code rechazar -> nueva version = max + 1} vive en
 * la capa de aplicacion, y es exactamente la invariante de la Property 8. Espeja
 * el patron de {@code RevocacionTokenRefrescoPropertyTest}, que respalda un puerto
 * de produccion con una implementacion en memoria.</p>
 *
 * <p>El {@link PruebaDisenoRepository} es un {@code JpaRepository} con muchos
 * metodos; solo tres se usan en las rutas bajo prueba ({@code save},
 * {@code findById}, {@code findMaxNumeroVersionByCotizacionId}). Se construye por
 * ello un doble de Mockito con respuestas <em>con estado</em> respaldadas por el
 * mismo {@code Map}, lo que da un fake minimo y fiel sin implementar la interfaz
 * completa. El {@link CotizacionExistentePort} devuelve siempre {@code true} y el
 * {@link AuditoriaPort} es un no-op. El {@link Clock} es fijo (UTC) para
 * determinismo. El {@link TenantContext} se fija antes de cada operacion (el
 * servicio audita via {@code TenantContext.require()}) y se limpia con
 * {@link AfterTry}.</p>
 */
class VersionadoMonotonoPruebaDisenoPropertyTest {

    /** Reloj fijo (UTC): el sellado del instante de decision es deterministico. */
    private static final Instant AHORA = Instant.parse("2025-01-15T10:00:00Z");
    private static final Clock RELOJ_FIJO = Clock.fixed(AHORA, ZoneOffset.UTC);

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** El TenantContext es un ThreadLocal: se limpia tras cada intento (Req 23). */
    @AfterTry
    void limpiarTenant() {
        TenantContext.clear();
    }

    // ----------------------------------------------------------------------
    // Property 8 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 8: Para cualquier Prueba_Diseno en estado "pendiente" que sea rechazada, el sistema conserva el historial y genera una nueva Prueba_Diseno con número de versión igual al anterior más 1 y estado "pendiente".
    @Property(tries = 1000)
    void laGeneracionInicialProduceVersion1Pendiente(@ForAll("cotizaciones") UUID cotizacionId) {
        Escenario esc = nuevoEscenario();

        PruebaDisenoDto v1 = esc.servicio.generar(cotizacionId);

        // Invariante 1 (Req 15.1): la primera generacion es version 1 en pendiente.
        assertThat(v1.numeroVersion()).isEqualTo(PruebaDiseno.VERSION_INICIAL);
        assertThat(v1.numeroVersion()).isEqualTo(1);
        assertThat(v1.estado()).isEqualTo("pendiente");
        assertThat(v1.cotizacionId()).isEqualTo(cotizacionId);
        // Y es la unica prueba pendiente de la Cotizacion.
        assertThat(esc.repo.pendientesDe(cotizacionId)).hasSize(1);
    }

    // Feature: crm-anuncios-luminosos, Property 8: Para cualquier Prueba_Diseno en estado "pendiente" que sea rechazada, el sistema conserva el historial y genera una nueva Prueba_Diseno con número de versión igual al anterior más 1 y estado "pendiente".
    @Property(tries = 1000)
    void cadaRechazoConservaElHistorialYGeneraLaVersionMasUnoPendiente(
            @ForAll("cotizaciones") UUID cotizacionId,
            @ForAll @IntRange(min = 0, max = 15) int numeroRechazos) {

        Escenario esc = nuevoEscenario();

        PruebaDisenoDto pendiente = esc.servicio.generar(cotizacionId);
        int versionEsperada = 1;

        for (int i = 0; i < numeroRechazos; i++) {
            assertThat(pendiente.numeroVersion()).isEqualTo(versionEsperada);
            UUID idRechazada = pendiente.id();

            ResultadoRechazoPruebaDiseno resultado = esc.servicio.rechazar(idRechazada);

            // Invariante 2 (Req 15.3): la version rechazada conserva su numero y pasa
            // a 'rechazada'; se genera una NUEVA pendiente con numero = anterior + 1.
            assertThat(resultado.rechazada().numeroVersion()).isEqualTo(versionEsperada);
            assertThat(resultado.rechazada().estado()).isEqualTo("rechazada");
            assertThat(resultado.nuevaVersion().numeroVersion()).isEqualTo(versionEsperada + 1);
            assertThat(resultado.nuevaVersion().estado()).isEqualTo("pendiente");
            assertThat(resultado.nuevaVersion().cotizacionId()).isEqualTo(cotizacionId);
            // Es una prueba distinta (el historial se conserva, no se muta la anterior).
            assertThat(resultado.nuevaVersion().id()).isNotEqualTo(idRechazada);

            pendiente = resultado.nuevaVersion();
            versionEsperada++;
        }

        // Invariante 3 (versionado monotono, Req 15.3): los numeros de version de la
        // Cotizacion son 1..N contiguos, estrictamente crecientes y sin duplicados.
        List<Integer> versiones = esc.repo.versionesOrdenadasDe(cotizacionId);
        assertThat(versiones).hasSize(numeroRechazos + 1);
        assertThat(versiones).doesNotHaveDuplicates();
        assertThat(versiones).isSorted();
        for (int i = 0; i < versiones.size(); i++) {
            assertThat(versiones.get(i)).isEqualTo(i + 1);
        }

        // Invariante 4 (a lo sumo una pendiente): tras la cadena de rechazos, solo la
        // ultima version sigue pendiente; todas las anteriores estan rechazadas.
        assertThat(esc.repo.pendientesDe(cotizacionId)).hasSize(1);
        assertThat(esc.repo.pendientesDe(cotizacionId).get(0).getNumeroVersion())
                .isEqualTo(numeroRechazos + 1);
        assertThat(esc.repo.enEstadoDe(cotizacionId, EstadoPruebaDiseno.RECHAZADA))
                .hasSize(numeroRechazos);
        assertThat(esc.repo.enEstadoDe(cotizacionId, EstadoPruebaDiseno.APROBADA)).isEmpty();
    }

    // Feature: crm-anuncios-luminosos, Property 8: Para cualquier Prueba_Diseno en estado "pendiente" que sea rechazada, el sistema conserva el historial y genera una nueva Prueba_Diseno con número de versión igual al anterior más 1 y estado "pendiente".
    @Property(tries = 1000)
    void laAprobacionEsTerminalNoGeneraNuevaVersionYCongela(
            @ForAll("cotizaciones") UUID cotizacionId,
            @ForAll @IntRange(min = 0, max = 15) int rechazosPrevios) {

        Escenario esc = nuevoEscenario();

        // Se recorre una linea de rechazos y luego se aprueba la version vigente.
        PruebaDisenoDto pendiente = esc.servicio.generar(cotizacionId);
        for (int i = 0; i < rechazosPrevios; i++) {
            pendiente = esc.servicio.rechazar(pendiente.id()).nuevaVersion();
        }
        int versionAprobada = pendiente.numeroVersion();
        int totalAntes = esc.repo.versionesOrdenadasDe(cotizacionId).size();

        PruebaDisenoDto aprobada = esc.servicio.aprobar(pendiente.id());

        // Invariante 5 (Req 15.2): aprobar es terminal. No aparece ninguna nueva
        // version y no queda ninguna prueba pendiente en la Cotizacion.
        assertThat(aprobada.estado()).isEqualTo("aprobada");
        assertThat(aprobada.numeroVersion()).isEqualTo(versionAprobada);
        assertThat(esc.repo.versionesOrdenadasDe(cotizacionId)).hasSize(totalAntes);
        assertThat(esc.repo.pendientesDe(cotizacionId)).isEmpty();
        assertThat(esc.repo.enEstadoDe(cotizacionId, EstadoPruebaDiseno.APROBADA)).hasSize(1);
    }

    // Feature: crm-anuncios-luminosos, Property 8: Para cualquier Prueba_Diseno en estado "pendiente" que sea rechazada, el sistema conserva el historial y genera una nueva Prueba_Diseno con número de versión igual al anterior más 1 y estado "pendiente".
    @Property(tries = 1000)
    void unaVersionYaDecididaEsInmutableYNoPuedeVolverATransicionar(
            @ForAll("cotizaciones") UUID cotizacionId,
            @ForAll boolean rechazarPrimero) {

        Escenario esc = nuevoEscenario();
        PruebaDisenoDto pendiente = esc.servicio.generar(cotizacionId);

        // Se decide la version vigente (aprobar o rechazar), quedando 'decidida'.
        UUID idDecidida;
        int totalTrasDecidir;
        if (rechazarPrimero) {
            ResultadoRechazoPruebaDiseno r = esc.servicio.rechazar(pendiente.id());
            idDecidida = r.rechazada().id();
            totalTrasDecidir = esc.repo.versionesOrdenadasDe(cotizacionId).size();
        } else {
            PruebaDisenoDto a = esc.servicio.aprobar(pendiente.id());
            idDecidida = a.id();
            totalTrasDecidir = esc.repo.versionesOrdenadasDe(cotizacionId).size();
        }

        // Invariante 6 (inmutabilidad, Req 15.4): decidir de nuevo una prueba ya
        // decidida se rechaza con 409; el historial no cambia (no se crea/mut fila).
        assertThatThrownBy(() -> esc.servicio.aprobar(idDecidida))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(() -> esc.servicio.rechazar(idDecidida))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(esc.repo.versionesOrdenadasDe(cotizacionId)).hasSize(totalTrasDecidir);
    }

    // ----------------------------------------------------------------------
    // Escenario: servicio de produccion + repositorio en memoria + dobles no-op
    // ----------------------------------------------------------------------

    private Escenario nuevoEscenario() {
        TenantContext.set(TENANT);
        RepositorioEnMemoria repo = new RepositorioEnMemoria();
        PruebaDisenoRepository doble = repo.comoMockito();

        CotizacionExistentePort cotizacionExistente = mock(CotizacionExistentePort.class);
        when(cotizacionExistente.existeCotizacion(any(UUID.class))).thenReturn(true);

        // AuditoriaPort es un no-op: el mock de Mockito ignora cada registro.
        AuditoriaPort auditoria = mock(AuditoriaPort.class);

        ServicioPruebasDiseno servicio =
                new ServicioPruebasDiseno(doble, cotizacionExistente, auditoria, RELOJ_FIJO);
        return new Escenario(servicio, repo);
    }

    private record Escenario(ServicioPruebasDiseno servicio, RepositorioEnMemoria repo) {
    }

    /**
     * Repositorio en memoria con estado real respaldado por {@code Map<UUID,
     * PruebaDiseno>}. Expone un doble de Mockito que delega en este estado para los
     * tres metodos que ejercen las rutas bajo prueba: {@code save} (persiste/actualiza
     * por id, devolviendo la misma instancia como hace JPA con entidades gestionadas),
     * {@code findById} y {@code findMaxNumeroVersionByCotizacionId} (verdadero maximo).
     */
    private static final class RepositorioEnMemoria {

        private final Map<UUID, PruebaDiseno> porId = new HashMap<>();

        PruebaDisenoRepository comoMockito() {
            PruebaDisenoRepository doble = mock(PruebaDisenoRepository.class);
            when(doble.save(any(PruebaDiseno.class))).thenAnswer(inv -> {
                PruebaDiseno prueba = inv.getArgument(0);
                porId.put(prueba.getId(), prueba);
                return prueba;
            });
            when(doble.findById(any(UUID.class))).thenAnswer(inv ->
                    Optional.ofNullable(porId.get(inv.<UUID>getArgument(0))));
            when(doble.findMaxNumeroVersionByCotizacionId(any(UUID.class))).thenAnswer(inv -> {
                UUID cotizacionId = inv.getArgument(0);
                return porId.values().stream()
                        .filter(p -> cotizacionId.equals(p.getCotizacionId()))
                        .map(PruebaDiseno::getNumeroVersion)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
            });
            return doble;
        }

        List<Integer> versionesOrdenadasDe(UUID cotizacionId) {
            return porId.values().stream()
                    .filter(p -> cotizacionId.equals(p.getCotizacionId()))
                    .map(PruebaDiseno::getNumeroVersion)
                    .sorted()
                    .toList();
        }

        List<PruebaDiseno> pendientesDe(UUID cotizacionId) {
            return enEstadoDe(cotizacionId, EstadoPruebaDiseno.PENDIENTE);
        }

        List<PruebaDiseno> enEstadoDe(UUID cotizacionId, EstadoPruebaDiseno estado) {
            List<PruebaDiseno> resultado = new ArrayList<>();
            for (PruebaDiseno p : porId.values()) {
                if (cotizacionId.equals(p.getCotizacionId()) && p.getEstado() == estado) {
                    resultado.add(p);
                }
            }
            return resultado;
        }
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> cotizaciones() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
