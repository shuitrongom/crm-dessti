package com.dessti.crm.platform.security.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA para la <b>autenticacion</b>, mapeada sobre la tabla
 * {@code usuario} definida en la migracion V1.
 *
 * <p>Expone los campos necesarios para verificar credenciales y construir los
 * claims del token (id, tenant_id, identificador de acceso, hash de contrasena
 * y bandera {@code activo}) y, desde la tarea 9.2, el estado de <b>bloqueo por
 * intentos fallidos</b>: {@code intentos_fallidos}, {@code bloqueado_hasta},
 * {@code primer_intento_fallido} (ancla de la ventana deslizante, V7) y
 * {@code version} (bloqueo optimista).</p>
 *
 * <p><strong>Bloqueo por intentos fallidos (Req 2.1, 2.2):</strong> la logica
 * temporal vive en el dominio de esta entidad y opera siempre con un
 * {@link Clock} inyectado por el servicio, nunca con {@code Instant.now()}
 * directo, para que las pruebas sean deterministas. Reglas:</p>
 * <ul>
 *   <li>Tras {@value #MAX_INTENTOS} fallos consecutivos dentro de una ventana
 *       de {@value #MINUTOS_VENTANA} minutos, la cuenta queda bloqueada durante
 *       {@value #MINUTOS_BLOQUEO} minutos.</li>
 *   <li>Un fallo cuya distancia respecto al primer fallo de la racha supera la
 *       ventana reinicia el conteo (el contador vuelve a empezar en 1).</li>
 *   <li>Al expirar el bloqueo, el contador se reinicia a 0 (Req 2.1).</li>
 *   <li>Un inicio de sesion exitoso reinicia el contador a 0 y limpia el
 *       bloqueo.</li>
 * </ul>
 *
 * <p><strong>Ventana deslizante literal (Req 2.1):</strong> la ventana de
 * {@value #MINUTOS_VENTANA} minutos se ancla en el instante del <b>primer</b>
 * fallo de la racha en curso, persistido de forma independiente en la columna
 * {@code primer_intento_fallido} (migracion V7). Un fallo solo suma al bloqueo
 * si ocurre dentro de esa ventana: si llega despues de que la ventana anclada en
 * el primer fallo haya transcurrido, la racha se reinicia y ese fallo pasa a ser
 * el primero de una racha nueva (contador = 1). Cinco fallos <b>dentro</b> de la
 * ventana bloquean 15 minutos; al expirar el bloqueo el contador vuelve a 0.</p>
 *
 * <p><strong>Convencion del borde de la ventana (documentada):</strong> la
 * ventana se considera transcurrida cuando {@code ahora} es
 * <em>estrictamente posterior</em> a {@code primer_intento_fallido + 15 min};
 * es decir, el instante exacto del limite (justo 15 min) se trata como
 * <b>todavia dentro</b> de la ventana (borde superior inclusivo). Un fallo justo
 * por debajo del limite acumula; el primero que caiga estrictamente despues
 * reinicia la racha.</p>
 *
 * <p><strong>Nota multi-tenant:</strong> {@code usuario} NO es tenant-scoped
 * (su {@code tenant_id} puede ser nulo para el super_admin y el login ocurre
 * antes de resolver el tenant), por lo que esta entidad no hereda de
 * {@code TenantScopedEntity} ni activa el filtro de Hibernate por tenant.</p>
 */
@Entity
@Table(name = "usuario")
public class UsuarioAuth {

    /** Fallos consecutivos que disparan el bloqueo (Req 2.1). */
    public static final int MAX_INTENTOS = 5;

    /** Minutos de la ventana en que se cuentan los fallos consecutivos (Req 2.1). */
    public static final int MINUTOS_VENTANA = 15;

    /** Minutos que dura el bloqueo de la cuenta (Req 2.1). */
    public static final int MINUTOS_BLOQUEO = 15;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "identificador_acceso", nullable = false, updatable = false)
    private String identificadorAcceso;

    @Column(name = "hash_password", nullable = false)
    private String hashPassword;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    /**
     * {@code true} cuando la cuenta debe cambiar su contrasena en el proximo
     * inicio de sesion (contrasena temporal generada o fijada por el super_admin,
     * V69). El login lo expone al cliente para forzar el cambio; se limpia cuando
     * el Usuario cambia su propia contrasena.
     */
    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword;

    /** Contador de fallos de autenticacion consecutivos (Req 2.1). */
    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    /**
     * Instante (UTC) hasta el cual la cuenta esta bloqueada; {@code null} si no
     * hay bloqueo vigente (Req 2.1, 2.2).
     */
    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    /**
     * Instante (UTC) del primer fallo de la racha en curso; ancla la ventana
     * deslizante de {@value #MINUTOS_VENTANA} minutos (Req 2.1, migracion V7).
     * {@code null} cuando no hay racha activa.
     */
    @Column(name = "primer_intento_fallido")
    private Instant primerIntentoFallido;

    /** Version para bloqueo optimista (Req 49) en las escrituras del contador. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UsuarioAuth() {
        // Requerido por JPA.
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getIdentificadorAcceso() {
        return identificadorAcceso;
    }

    /**
     * @return el hash BCrypt de la contrasena. Nunca debe registrarse en logs
     *         ni exponerse en respuestas (Req 1.2, 11.3).
     */
    public String getHashPassword() {
        return hashPassword;
    }

    public boolean isActivo() {
        return activo;
    }

    /**
     * @return {@code true} si la cuenta debe cambiar su contrasena en el proximo
     *         inicio de sesion (V69). El servicio de autenticacion lo propaga al
     *         cliente para forzar el cambio.
     */
    public boolean isDebeCambiarPassword() {
        return debeCambiarPassword;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    /**
     * @return el instante (UTC) del primer fallo de la racha en curso, o
     *         {@code null} si no hay racha activa. Ancla la ventana deslizante
     *         de {@value #MINUTOS_VENTANA} minutos (Req 2.1).
     */
    public Instant getPrimerIntentoFallido() {
        return primerIntentoFallido;
    }

    public long getVersion() {
        return version;
    }

    // -----------------------------------------------------------------
    // Dominio del bloqueo por intentos fallidos (Req 2.1, 2.2)
    // -----------------------------------------------------------------

    /**
     * Indica si la cuenta esta bloqueada en el instante dado por {@code clock}.
     *
     * <p>El chequeo no modifica el estado; el reinicio del contador al expirar
     * el bloqueo lo realiza {@link #registrarExito()} o
     * {@link #registrarFallo(Clock)} en la siguiente interaccion (Req 2.1).</p>
     *
     * @param clock reloj (UTC) del servicio; obligatorio.
     * @return {@code true} si {@code bloqueado_hasta} es posterior al instante
     *         actual del reloj.
     */
    public boolean estaBloqueado(Clock clock) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(clock.instant());
    }

    /**
     * Minutos restantes (redondeados hacia arriba) hasta el desbloqueo, o 0 si
     * la cuenta no esta bloqueada. Se usa tanto para la auditoria interna
     * (Req 2.5) como para el mensaje al cliente en el caso de cuenta bloqueada,
     * que por el Req 2.2 debe indicar explicitamente el bloqueo y el tiempo
     * restante en minutos (a diferencia del resto de causas de fallo, que
     * permanecen genericas por el Req 1.3).
     *
     * @param clock reloj (UTC) del servicio; obligatorio.
     * @return minutos restantes de bloqueo (>= 0).
     */
    public long minutosRestantesBloqueo(Clock clock) {
        if (!estaBloqueado(clock)) {
            return 0;
        }
        Duration restante = Duration.between(clock.instant(), bloqueadoHasta);
        long minutos = restante.toMinutes();
        // Redondeo hacia arriba: cualquier fraccion de minuto cuenta como 1.
        return (restante.minusMinutes(minutos).isZero()) ? minutos : minutos + 1;
    }

    /**
     * Registra un intento fallido y aplica el bloqueo si corresponde (Req 2.1),
     * respetando la <b>ventana deslizante literal</b> anclada en el primer fallo
     * de la racha.
     *
     * <p>Semantica de la ventana de {@value #MINUTOS_VENTANA} minutos, en orden:</p>
     * <ol>
     *   <li>Si un bloqueo previo ya expiro
     *       ({@code bloqueado_hasta != null && !bloqueado_hasta.isAfter(ahora)}),
     *       se reinicia por completo la racha: contador a 0, sin bloqueo y sin
     *       ancla de ventana (Req 2.1).</li>
     *   <li>Si hay una racha activa ({@code intentosFallidos > 0} y
     *       {@code primerIntentoFallido != null}) y {@code ahora} es
     *       <em>estrictamente posterior</em> a
     *       {@code primerIntentoFallido + }{@value #MINUTOS_VENTANA}{@code  min},
     *       la ventana transcurrio: la racha se reinicia (contador a 0, sin
     *       ancla) y este fallo iniciara una racha nueva. El borde exacto (justo
     *       15 min) se trata como <b>dentro</b> de la ventana.</li>
     *   <li>Si el contador esta en 0 (racha nueva o recien reiniciada), se ancla
     *       la ventana en este fallo: {@code primerIntentoFallido = ahora}.</li>
     *   <li>Se incrementa el contador.</li>
     *   <li>Si el contador alcanza {@value #MAX_INTENTOS}, se bloquea:
     *       {@code bloqueado_hasta = ahora + }{@value #MINUTOS_BLOQUEO}{@code  min}.
     *       El ancla de la ventana se limpia porque la racha ya cumplio su
     *       proposito; asi, un fallo posterior a la expiracion del bloqueo
     *       arranca una racha limpia.</li>
     * </ol>
     *
     * @param clock reloj (UTC) del servicio; obligatorio.
     */
    public void registrarFallo(Clock clock) {
        Instant ahora = clock.instant();

        // (1) Si habia un bloqueo y ya expiro, la racha se reinicia (Req 2.1).
        if (bloqueadoHasta != null && !bloqueadoHasta.isAfter(ahora)) {
            intentosFallidos = 0;
            bloqueadoHasta = null;
            primerIntentoFallido = null;
        }

        // (2) Si hay una racha activa y la ventana anclada en el primer fallo ya
        //     transcurrio, la racha se reinicia (borde superior inclusivo: justo
        //     15 min aun cuenta como dentro de la ventana).
        if (intentosFallidos > 0 && primerIntentoFallido != null
                && ahora.isAfter(primerIntentoFallido.plus(Duration.ofMinutes(MINUTOS_VENTANA)))) {
            intentosFallidos = 0;
            primerIntentoFallido = null;
        }

        // (3) Fallo que inicia una racha (nueva o recien reiniciada): anclar la
        //     ventana en este primer fallo.
        if (intentosFallidos == 0) {
            primerIntentoFallido = ahora;
        }

        // (4) Contar el fallo dentro de la ventana vigente.
        intentosFallidos++;

        // (5) Al alcanzar el limite dentro de la ventana, bloquear 15 min. La
        //     racha ya cumplio su proposito: se limpia el ancla para que un
        //     fallo posterior a la expiracion arranque una racha nueva.
        if (intentosFallidos >= MAX_INTENTOS) {
            bloqueadoHasta = ahora.plus(Duration.ofMinutes(MINUTOS_BLOQUEO));
            primerIntentoFallido = null;
        }
    }

    /**
     * Reinicia por completo el estado de bloqueo tras un inicio de sesion
     * exitoso: contador a 0, {@code bloqueado_hasta} a {@code null} y ancla de la
     * ventana ({@code primer_intento_fallido}) a {@code null}.
     */
    public void registrarExito() {
        intentosFallidos = 0;
        bloqueadoHasta = null;
        primerIntentoFallido = null;
    }
}
