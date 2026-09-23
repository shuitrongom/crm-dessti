package com.dessti.crm.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 20: Integridad
 * de la cadena de auditoria</strong> (Req 10.4, 10.7).
 *
 * <p>Esta property valida la <em>invariante criptografica</em> del
 * encadenamiento por hash de la bitacora, reutilizando la clase de produccion
 * {@link CalculadoraHashCadena} (SHA-256 real, semilla de 64 ceros) sobre una
 * cadena modelada <strong>en memoria</strong>, sin base de datos ni contexto de
 * Spring. La verificacion de la cadena contra la BD real y su exposicion como
 * servicio ({@link VerificadorCadenaAuditoria}) ya la cubre la tarea 7.2 a
 * nivel de servicio/integracion; aqui se comprueba universalmente que:</p>
 *
 * <ol>
 *   <li><strong>Integridad:</strong> una cadena construida correctamente se
 *       verifica como integra (sin ruptura).</li>
 *   <li><strong>Deteccion de manipulacion:</strong> alterar cualquier campo de
 *       cualquier registro (o su {@code hash_actual}/{@code hash_previo})
 *       despues de construida la cadena rompe la verificacion, y la ruptura se
 *       reporta en la posicion del primer registro afectado (o su enlace).</li>
 *   <li><strong>Reordenar/eliminar</strong> un registro tambien rompe la
 *       verificacion.</li>
 * </ol>
 *
 * <p>La logica de verificacion se replica aqui como funcion pura sobre la lista
 * en memoria, espejo de {@link VerificadorCadenaAuditoria} pero sin repositorio,
 * para no tocar produccion.</p>
 */
class IntegridadCadenaAuditoriaPropertyTest {

    // ----------------------------------------------------------------------
    // Modelo en memoria de un evento y de un registro encadenado
    // ----------------------------------------------------------------------

    /** Evento de auditoria (los campos que alimentan el hash). */
    private record Evento(
            UUID tenantId, String actor, String accion, String recurso,
            String detalle, String valorAnterior, String valorNuevo,
            String traceId, Instant timestampUtc) {
    }

    /** Registro encadenado: evento + su hash previo y su hash actual. */
    private record Registro(long id, Evento evento, String hashPrevio, String hashActual) {

        String recalcular() {
            return CalculadoraHashCadena.calcular(
                    evento.tenantId(), evento.actor(), evento.accion(), evento.recurso(),
                    evento.detalle(), evento.valorAnterior(), evento.valorNuevo(),
                    evento.traceId(), evento.timestampUtc(), hashPrevio);
        }
    }

    /** Resultado de la verificacion en memoria (espejo del de produccion). */
    private record Resultado(boolean intacta, long idRuptura) {
        static Resultado ok() {
            return new Resultado(true, -1L);
        }

        static Resultado rota(long idRuptura) {
            return new Resultado(false, idRuptura);
        }
    }

    // ----------------------------------------------------------------------
    // Construccion y verificacion de la cadena en memoria (usa produccion)
    // ----------------------------------------------------------------------

    /** Construye la cadena correcta a partir de una lista de eventos. */
    private static List<Registro> construirCadena(List<Evento> eventos) {
        List<Registro> cadena = new ArrayList<>(eventos.size());
        String hashPrevio = CalculadoraHashCadena.HASH_SEMILLA;
        long id = 1;
        for (Evento e : eventos) {
            String hashActual = CalculadoraHashCadena.calcular(
                    e.tenantId(), e.actor(), e.accion(), e.recurso(),
                    e.detalle(), e.valorAnterior(), e.valorNuevo(),
                    e.traceId(), e.timestampUtc(), hashPrevio);
            cadena.add(new Registro(id++, e, hashPrevio, hashActual));
            hashPrevio = hashActual;
        }
        return cadena;
    }

    /**
     * Verifica la cadena en memoria replicando la logica de
     * {@link VerificadorCadenaAuditoria}: enlace ({@code hash_previo} coincide
     * con el {@code hash_actual} del anterior, o la semilla para el primero) y
     * contenido ({@code hash_actual} == recalculo). Reporta la primera ruptura.
     */
    private static Resultado verificar(List<Registro> cadena) {
        String hashPrevioEsperado = CalculadoraHashCadena.HASH_SEMILLA;
        for (Registro r : cadena) {
            if (!hashPrevioEsperado.equals(r.hashPrevio())) {
                return Resultado.rota(r.id());
            }
            if (!r.recalcular().equals(r.hashActual())) {
                return Resultado.rota(r.id());
            }
            hashPrevioEsperado = r.hashActual();
        }
        return Resultado.ok();
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    private Arbitrary<String> textoCorto() {
        return Arbitraries.strings().ofMinLength(0).ofMaxLength(20).ascii();
    }

    private Arbitrary<String> nullable(Arbitrary<String> base) {
        return Arbitraries.oneOf(Arbitraries.just(null), base);
    }

    private Arbitrary<Evento> evento() {
        Arbitrary<UUID> tenant = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.create(UUID::randomUUID));
        Arbitrary<Instant> ts = Arbitraries.longs()
                .between(0L, 4_000_000_000L)
                .map(Instant::ofEpochSecond);
        return Combinators.combine(
                        tenant,
                        Arbitraries.strings().ofMinLength(1).ofMaxLength(20).ascii(), // actor
                        Arbitraries.strings().ofMinLength(1).ofMaxLength(20).ascii(), // accion
                        Arbitraries.strings().ofMinLength(1).ofMaxLength(20).ascii(), // recurso
                        nullable(textoCorto()),  // detalle
                        nullable(textoCorto()),  // valorAnterior
                        nullable(textoCorto()),  // valorNuevo
                        nullable(textoCorto()))  // traceId
                .as((tenantId, actor, accion, recurso, detalle, va, vn, trace) -> new Object[] {
                        tenantId, actor, accion, recurso, detalle, va, vn, trace })
                .flatMap(base -> ts.map(t -> new Evento(
                        (UUID) base[0], (String) base[1], (String) base[2], (String) base[3],
                        (String) base[4], (String) base[5], (String) base[6], (String) base[7], t)));
    }

    @Provide
    Arbitrary<List<Evento>> secuenciasDeEventos() {
        return evento().list().ofMinSize(1).ofMaxSize(12);
    }

    // ----------------------------------------------------------------------
    // Property 20 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 20: Para cualquier secuencia de Registro_Auditoria, el hash de cada entrada se calcula a partir de su contenido y del hash de la entrada anterior; cualquier manipulacion posterior de cualquier entrada rompe la verificacion de la cadena y es detectable.
    @Property(tries = 1000)
    void cadenaConstruidaCorrectamenteEsIntegra(@ForAll("secuenciasDeEventos") List<Evento> eventos) {
        List<Registro> cadena = construirCadena(eventos);

        Resultado resultado = verificar(cadena);

        assertThat(resultado.intacta())
                .as("una cadena construida correctamente debe verificarse como integra")
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 20: Para cualquier secuencia de Registro_Auditoria, el hash de cada entrada se calcula a partir de su contenido y del hash de la entrada anterior; cualquier manipulacion posterior de cualquier entrada rompe la verificacion de la cadena y es detectable.
    @Property(tries = 1000)
    void manipularCualquierCampoRompeLaCadenaYSeReportaLaPosicion(
            @ForAll("secuenciasDeEventos") List<Evento> eventos,
            @ForAll @IntRange(min = 0, max = 11) int indiceCrudo,
            @ForAll @IntRange(min = 0, max = 10) int tipoCampo) {

        List<Registro> cadena = construirCadena(eventos);
        int indice = indiceCrudo % cadena.size();
        Registro original = cadena.get(indice);

        Registro manipulado = manipular(original, tipoCampo);
        // Si la manipulacion resulto ser un no-op (mismo valor generado), no aplica.
        if (mismoRegistro(original, manipulado)) {
            return;
        }
        cadena.set(indice, manipulado);

        Resultado resultado = verificar(cadena);

        assertThat(resultado.intacta())
                .as("alterar un campo del registro %d debe romper la verificacion", original.id())
                .isFalse();

        // La ruptura se reporta en el propio registro alterado (por contenido o
        // por enlace roto en ese mismo registro) o en el registro siguiente
        // (cuando se altera el hash_actual del registro, el enlace del siguiente
        // deja de coincidir). En cualquier caso, es el primer registro afectado.
        long idAfectado = original.id();
        long idSiguiente = idAfectado + 1;
        assertThat(resultado.idRuptura())
                .as("la ruptura debe reportarse en la posicion del primer registro afectado")
                .isIn(idAfectado, idSiguiente);
    }

    // Feature: crm-anuncios-luminosos, Property 20: Para cualquier secuencia de Registro_Auditoria, el hash de cada entrada se calcula a partir de su contenido y del hash de la entrada anterior; cualquier manipulacion posterior de cualquier entrada rompe la verificacion de la cadena y es detectable.
    @Property(tries = 1000)
    void eliminarUnRegistroRompeLaCadena(
            @ForAll("secuenciasDeEventos") List<Evento> eventos,
            @ForAll @IntRange(min = 0, max = 11) int indiceCrudo) {

        List<Registro> cadena = construirCadena(eventos);
        // Eliminar el ULTIMO registro deja un prefijo aun valido (una cadena mas
        // corta pero integra), por lo que no es detectable como ruptura: es un
        // truncado, no una manipulacion interna. En cambio, eliminar un registro
        // INTERMEDIO deja "huerfano" al siguiente, cuyo hash_previo ya no enlaza
        // con el hash_actual del nuevo antecesor: eso si rompe la verificacion.
        // Por ello requerimos al menos 3 registros y eliminamos uno intermedio
        // (indices 1..size-2), garantizando que siempre queda un sucesor.
        if (cadena.size() < 3) {
            return;
        }
        int indice = 1 + (indiceCrudo % (cadena.size() - 2)); // 1..size-2
        Registro eliminado = cadena.get(indice);
        Registro sucesor = cadena.get(indice + 1);
        cadena.remove(indice);

        Resultado resultado = verificar(cadena);

        assertThat(resultado.intacta())
                .as("eliminar el registro intermedio %d debe romper el encadenamiento", eliminado.id())
                .isFalse();
        // La ruptura se detecta en el sucesor, cuyo enlace queda huerfano.
        assertThat(resultado.idRuptura())
                .as("la ruptura por eliminacion se reporta en el registro sucesor huerfano")
                .isEqualTo(sucesor.id());
    }

    // Feature: crm-anuncios-luminosos, Property 20: Para cualquier secuencia de Registro_Auditoria, el hash de cada entrada se calcula a partir de su contenido y del hash de la entrada anterior; cualquier manipulacion posterior de cualquier entrada rompe la verificacion de la cadena y es detectable.
    @Property(tries = 1000)
    void reordenarDosRegistrosRompeLaCadena(
            @ForAll("secuenciasDeEventos") List<Evento> eventos,
            @ForAll @IntRange(min = 0, max = 11) int indiceCrudo) {

        List<Registro> cadena = construirCadena(eventos);
        if (cadena.size() < 2) {
            return;
        }
        int i = indiceCrudo % (cadena.size() - 1);
        // Intercambiar dos registros adyacentes con contenido distinto rompe el
        // encadenamiento; si su contenido fuese identico (raro), no aplica.
        if (mismoRegistro(cadena.get(i), cadena.get(i + 1))) {
            return;
        }
        Collections.swap(cadena, i, i + 1);

        Resultado resultado = verificar(cadena);

        assertThat(resultado.intacta())
                .as("reordenar registros distintos debe romper el encadenamiento")
                .isFalse();
    }

    // ----------------------------------------------------------------------
    // Manipulacion de un registro (altera un campo segun el tipo)
    // ----------------------------------------------------------------------

    private static Registro manipular(Registro r, int tipoCampo) {
        Evento e = r.evento();
        return switch (tipoCampo % 11) {
            case 0 -> new Registro(r.id(),
                    new Evento(alterarTenant(e.tenantId()), e.actor(), e.accion(), e.recurso(),
                            e.detalle(), e.valorAnterior(), e.valorNuevo(), e.traceId(), e.timestampUtc()),
                    r.hashPrevio(), r.hashActual());
            case 1 -> new Registro(r.id(),
                    new Evento(e.tenantId(), e.actor() + "X", e.accion(), e.recurso(),
                            e.detalle(), e.valorAnterior(), e.valorNuevo(), e.traceId(), e.timestampUtc()),
                    r.hashPrevio(), r.hashActual());
            case 2 -> new Registro(r.id(),
                    new Evento(e.tenantId(), e.actor(), e.accion() + "X", e.recurso(),
                            e.detalle(), e.valorAnterior(), e.valorNuevo(), e.traceId(), e.timestampUtc()),
                    r.hashPrevio(), r.hashActual());
            case 3 -> new Registro(r.id(),
                    new Evento(e.tenantId(), e.actor(), e.accion(), e.recurso() + "X",
                            e.detalle(), e.valorAnterior(), e.valorNuevo(), e.traceId(), e.timestampUtc()),
                    r.hashPrevio(), r.hashActual());
            case 4 -> new Registro(r.id(),
                    new Evento(e.tenantId(), e.actor(), e.accion(), e.recurso(),
                            alterarTexto(e.detalle()), e.valorAnterior(), e.valorNuevo(), e.traceId(), e.timestampUtc()),
                    r.hashPrevio(), r.hashActual());
            case 5 -> new Registro(r.id(),
                    new Evento(e.tenantId(), e.actor(), e.accion(), e.recurso(),
                            e.detalle(), alterarTexto(e.valorAnterior()), e.valorNuevo(), e.traceId(), e.timestampUtc()),
                    r.hashPrevio(), r.hashActual());
            case 6 -> new Registro(r.id(),
                    new Evento(e.tenantId(), e.actor(), e.accion(), e.recurso(),
                            e.detalle(), e.valorAnterior(), alterarTexto(e.valorNuevo()), e.traceId(), e.timestampUtc()),
                    r.hashPrevio(), r.hashActual());
            case 7 -> new Registro(r.id(),
                    new Evento(e.tenantId(), e.actor(), e.accion(), e.recurso(),
                            e.detalle(), e.valorAnterior(), e.valorNuevo(), alterarTexto(e.traceId()), e.timestampUtc()),
                    r.hashPrevio(), r.hashActual());
            case 8 -> new Registro(r.id(),
                    new Evento(e.tenantId(), e.actor(), e.accion(), e.recurso(),
                            e.detalle(), e.valorAnterior(), e.valorNuevo(), e.traceId(),
                            e.timestampUtc().plusSeconds(1)),
                    r.hashPrevio(), r.hashActual());
            case 9 -> new Registro(r.id(), e, alterarHash(r.hashPrevio()), r.hashActual());
            default -> new Registro(r.id(), e, r.hashPrevio(), alterarHash(r.hashActual()));
        };
    }

    private static boolean mismoRegistro(Registro a, Registro b) {
        return a.evento().equals(b.evento())
                && a.hashPrevio().equals(b.hashPrevio())
                && a.hashActual().equals(b.hashActual());
    }

    private static UUID alterarTenant(UUID t) {
        if (t == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        // Un UUID distinto y determinista.
        return new UUID(t.getMostSignificantBits() ^ 1L, t.getLeastSignificantBits());
    }

    private static String alterarTexto(String s) {
        return s == null ? "\u0001manipulado" : s + "\u0001";
    }

    private static String alterarHash(String h) {
        char primero = h.charAt(0);
        char nuevo = (primero == '0') ? '1' : '0';
        return nuevo + h.substring(1);
    }
}
