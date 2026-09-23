package com.dessti.crm.platform.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.vertical.RegistroVerticales;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 25: Autorizacion
 * RBAC con denegacion por defecto</strong> (Req 3.1, 3.2, 3.5, 3.6, 25.4).
 *
 * <p>Reutiliza la pieza de produccion {@link Autorizador} tal cual, poblando el
 * {@link SecurityContextHolder} con un {@link UsernamePasswordAuthenticationToken}
 * que porta las {@code authorities} del Usuario (un {@link SimpleGrantedAuthority}
 * por permiso atomico {@code recurso:operacion}) y, cuando aplica, el
 * {@link TenantContext} con un {@code tenant_id}. No hay contexto de Spring ni
 * base de datos: el {@link PlanModulosPort} se inyecta como un stub que devuelve
 * un booleano controlado.</p>
 *
 * <p>La property comprueba universalmente el si-y-solo-si de la autorizacion y la
 * denegacion por defecto:</p>
 * <ol>
 *   <li>Con Usuario autenticado, {@code tiene(recurso, operacion)} es {@code true}
 *       si y solo si el conjunto de permisos concedidos contiene el permiso
 *       requerido (comparado sin distinguir mayusculas); en su ausencia,
 *       {@code false} (denegacion por defecto, Req 3.1, 3.5).</li>
 *   <li>Sin autenticacion (contexto vacio/anonimo), {@code tiene(...)} es siempre
 *       {@code false}, con independencia del permiso (Req 3.5, 3.6).</li>
 *   <li>{@code moduloHabilitado} solo puede devolver {@code true} con Usuario
 *       autenticado, {@code tenant_id} en contexto y puerto que habilita el
 *       modulo; en ausencia de autenticacion, de tenant o con modulo en blanco,
 *       deniega (Req 25.4).</li>
 * </ol>
 *
 * <p><strong>Aislamiento entre iteraciones:</strong> el {@link SecurityContextHolder}
 * y el {@link TenantContext} son thread-local y jqwik reutiliza el hilo entre
 * intentos; por ello {@link #limpiarContexto()} (anotado {@link AfterTry}) limpia
 * ambos tras cada intento para evitar fugas de estado.</p>
 */
class AutorizacionRbacPropertyTest {

    // ----------------------------------------------------------------------
    // Aislamiento entre intentos: SecurityContextHolder + TenantContext son
    // thread-local y jqwik reutiliza el hilo. Limpiar siempre tras cada try.
    // ----------------------------------------------------------------------
    @AfterTry
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ----------------------------------------------------------------------
    // Utilidades de contexto
    // ----------------------------------------------------------------------

    /** Autentica un principal con las authorities indicadas (permisos atomicos). */
    private static void autenticarCon(List<String> authorities) {
        List<SimpleGrantedAuthority> gas = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        Authentication auth = new UsernamePasswordAuthenticationToken("usuario-prueba", "N/A", gas);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Puerto de Plan que devuelve un booleano fijo y controlado. El resto de
     * colaboradores del doble gating (registro de verticales vacio, puerto de
     * Giro y auditoria) son irrelevantes para esta property, centrada en
     * {@code tiene}: se aportan implementaciones neutras/no-op.
     */
    private static Autorizador autorizadorConPuerto(boolean habilitado) {
        PlanModulosPort puerto = (tenantId, modulo) -> habilitado;
        RegistroVerticales registroVacio = new RegistroVerticales(List.of());
        GiroEmpresaPort giroEmpresa = tenantId -> java.util.Optional.empty();
        AuditoriaPort auditoria = org.mockito.Mockito.mock(AuditoriaPort.class);
        return new Autorizador(puerto, registroVacio, giroEmpresa, auditoria);
    }

    // ----------------------------------------------------------------------
    // Property 25 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 25: Para cualquier Usuario autenticado y para cualquier operación, la operación se autoriza si y solo si algún Rol del Usuario posee el Permiso atómico requerido; en ausencia de ese Permiso (o de Rol válido) la operación se deniega con 403, evaluada siempre dentro del `tenant_id` del Usuario.
    @Property(tries = 1000)
    void autorizaSiYSoloSiElPermisoRequeridoEstaConcedido(
            @ForAll("listasDePermisos") List<Permiso> concedidos,
            @ForAll("permisos") Permiso requerido) {

        autenticarCon(concedidos.stream().map(Permiso::authority).collect(Collectors.toList()));

        Autorizador autorizador = autorizadorConPuerto(true);

        // Conjunto normalizado (recurso/operacion ya vienen en minusculas por el record).
        Set<String> concedidosSet = concedidos.stream()
                .map(Permiso::authority)
                .collect(Collectors.toSet());
        boolean esperado = concedidosSet.contains(requerido.authority());

        // si-y-solo-si: autoriza exactamente cuando el permiso requerido esta concedido.
        assertThat(autorizador.tiene(requerido.recurso(), requerido.operacion()))
                .as("tiene(recurso,operacion) autoriza sii el permiso esta concedido")
                .isEqualTo(esperado);
        assertThat(autorizador.tiene(requerido))
                .as("tiene(Permiso) autoriza sii el permiso esta concedido")
                .isEqualTo(esperado);

        // Denegacion por defecto: si NO esta concedido, siempre false.
        if (!esperado) {
            assertThat(autorizador.tiene(requerido.recurso(), requerido.operacion()))
                    .as("permiso ausente => denegacion por defecto")
                    .isFalse();
        }
    }

    // Feature: crm-anuncios-luminosos, Property 25: Para cualquier Usuario autenticado y para cualquier operación, la operación se autoriza si y solo si algún Rol del Usuario posee el Permiso atómico requerido; en ausencia de ese Permiso (o de Rol válido) la operación se deniega con 403, evaluada siempre dentro del `tenant_id` del Usuario.
    @Property(tries = 1000)
    void coincidenciaDeAuthorityInsensibleAMayusculas(
            @ForAll("permisos") Permiso requerido,
            @ForAll("listasDePermisos") List<Permiso> ruido) {

        // Se concede el permiso requerido pero con la authority en MAYUSCULAS.
        List<String> authorities = new ArrayList<>();
        authorities.add(requerido.authority().toUpperCase(Locale.ROOT));
        ruido.forEach(p -> authorities.add(p.authority()));
        autenticarCon(authorities);

        Autorizador autorizador = autorizadorConPuerto(true);

        // El Autorizador usa equalsIgnoreCase => debe autorizar aunque difiera el case.
        assertThat(autorizador.tiene(requerido.recurso(), requerido.operacion()))
                .as("la coincidencia de authority es insensible a mayusculas")
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 25: Para cualquier Usuario autenticado y para cualquier operación, la operación se autoriza si y solo si algún Rol del Usuario posee el Permiso atómico requerido; en ausencia de ese Permiso (o de Rol válido) la operación se deniega con 403, evaluada siempre dentro del `tenant_id` del Usuario.
    @Property(tries = 1000)
    void sinAutenticacionSiempreDeniega(
            @ForAll("permisos") Permiso requerido) {

        // NO se autentica ningun principal (contexto vacio == anonimo/ausente).
        Autorizador autorizador = autorizadorConPuerto(true);

        assertThat(autorizador.tiene(requerido.recurso(), requerido.operacion()))
                .as("sin autenticacion, tiene(recurso,operacion) siempre deniega")
                .isFalse();
        assertThat(autorizador.tiene(requerido))
                .as("sin autenticacion, tiene(Permiso) siempre deniega")
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 25: Para cualquier Usuario autenticado y para cualquier operación, la operación se autoriza si y solo si algún Rol del Usuario posee el Permiso atómico requerido; en ausencia de ese Permiso (o de Rol válido) la operación se deniega con 403, evaluada siempre dentro del `tenant_id` del Usuario.
    @Property(tries = 1000)
    void autenticacionAnonimaSiempreDeniega(
            @ForAll("permisos") Permiso requerido,
            @ForAll("listasDePermisos") List<Permiso> authoritiesAnonimo) {

        // Autenticacion anonima: aunque porte authorities y coincidan con el
        // permiso requerido, AutenticacionActual la descarta => denegacion (Req 3.6).
        List<SimpleGrantedAuthority> gas = new ArrayList<>();
        gas.add(new SimpleGrantedAuthority("ROLE_ANONYMOUS"));
        gas.add(new SimpleGrantedAuthority(requerido.authority()));
        authoritiesAnonimo.forEach(p -> gas.add(new SimpleGrantedAuthority(p.authority())));
        Authentication anon = new AnonymousAuthenticationToken("clave-anonima", "anonimo", gas);
        SecurityContextHolder.getContext().setAuthentication(anon);

        Autorizador autorizador = autorizadorConPuerto(true);

        assertThat(autorizador.tiene(requerido.recurso(), requerido.operacion()))
                .as("autenticacion anonima => tiene(recurso,operacion) siempre deniega")
                .isFalse();
        assertThat(autorizador.tiene(requerido))
                .as("autenticacion anonima => tiene(Permiso) siempre deniega")
                .isFalse();
        // El gating por Plan tambien deniega pese al puerto permisivo.
        TenantContext.set(UUID.randomUUID());
        assertThat(autorizador.moduloHabilitado("facturacion"))
                .as("autenticacion anonima => moduloHabilitado siempre deniega")
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 25: Para cualquier Usuario autenticado y para cualquier operación, la operación se autoriza si y solo si algún Rol del Usuario posee el Permiso atómico requerido; en ausencia de ese Permiso (o de Rol válido) la operación se deniega con 403, evaluada siempre dentro del `tenant_id` del Usuario.
    @Property(tries = 1000)
    void permisoMalFormadoDeniegaSinLanzar(
            @ForAll("componentesMalFormados") String recurso,
            @ForAll("componentesMalFormados") String operacion) {

        // Contexto plenamente autenticado con authorities arbitrarias: un permiso
        // requerido mal declarado (nulo, en blanco o con ':') debe tratarse como
        // denegacion por defecto y nunca propagar excepcion (Req 3.5).
        autenticarCon(List.of("cliente:crear", "factura:timbrar"));
        Autorizador autorizador = autorizadorConPuerto(true);

        assertThat(autorizador.tiene(recurso, operacion))
                .as("permiso mal formado => denegacion por defecto sin lanzar excepcion")
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 25: Para cualquier Usuario autenticado y para cualquier operación, la operación se autoriza si y solo si algún Rol del Usuario posee el Permiso atómico requerido; en ausencia de ese Permiso (o de Rol válido) la operación se deniega con 403, evaluada siempre dentro del `tenant_id` del Usuario.
    @Property(tries = 1000)
    void moduloHabilitadoRequiereAutenticacionTenantYPuerto(
            @ForAll("tokens") String modulo,
            @ForAll boolean autenticado,
            @ForAll boolean conTenant,
            @ForAll boolean puertoHabilita) {

        if (autenticado) {
            autenticarCon(List.of("cualquiera:leer"));
        }
        if (conTenant) {
            TenantContext.set(UUID.randomUUID());
        }

        Autorizador autorizador = autorizadorConPuerto(puertoHabilita);

        boolean resultado = autorizador.moduloHabilitado(modulo);

        // Solo puede ser true si hay autenticacion, tenant en contexto y el puerto habilita.
        boolean esperado = autenticado && conTenant && puertoHabilita;
        assertThat(resultado)
                .as("moduloHabilitado exige autenticacion + tenant + puerto (denegacion por defecto)")
                .isEqualTo(esperado);
    }

    // Feature: crm-anuncios-luminosos, Property 25: Para cualquier Usuario autenticado y para cualquier operación, la operación se autoriza si y solo si algún Rol del Usuario posee el Permiso atómico requerido; en ausencia de ese Permiso (o de Rol válido) la operación se deniega con 403, evaluada siempre dentro del `tenant_id` del Usuario.
    @Property(tries = 1000)
    void moduloEnBlancoSiempreDeniega(
            @ForAll("modulosEnBlanco") String moduloEnBlanco) {

        // Contexto plenamente habilitado: autenticado, con tenant y puerto permisivo.
        autenticarCon(List.of("cualquiera:leer"));
        TenantContext.set(UUID.randomUUID());
        Autorizador autorizador = autorizadorConPuerto(true);

        assertThat(autorizador.moduloHabilitado(moduloEnBlanco))
                .as("modulo nulo/en blanco => denegacion por defecto, aun con contexto habilitado")
                .isFalse();
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Token ASCII en minusculas sin ':' (recurso u operacion valido). */
    @Provide
    Arbitrary<String> tokens() {
        return Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(12)
                .withCharRange('a', 'z')
                .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '_');
    }

    /** Permiso atomico a partir de dos tokens (recurso, operacion). */
    @Provide
    Arbitrary<Permiso> permisos() {
        return Combinators.combine(tokens(), tokens()).as(Permiso::de);
    }

    /** Lista pequena de permisos atomicos (0..8), posiblemente con repeticiones. */
    @Provide
    Arbitrary<List<Permiso>> listasDePermisos() {
        return permisos().list().ofMaxSize(8);
    }

    /** Modulos nulos o en blanco (null, vacio, solo espacios). */
    @Provide
    Arbitrary<String> modulosEnBlanco() {
        return Arbitraries.of(null, "", " ", "   ", "\t", "\n");
    }

    /**
     * Componentes de permiso mal formados que {@link Permiso#de} rechaza: nulos,
     * en blanco (vacio/espacios) o con el separador {@code ':'} embebido. El
     * {@link Autorizador} debe traducir cualquiera de ellos a denegacion.
     */
    @Provide
    Arbitrary<String> componentesMalFormados() {
        return Arbitraries.of(null, "", " ", "   ", "\t", "a:b", ":", "cliente:", ":crear");
    }
}
