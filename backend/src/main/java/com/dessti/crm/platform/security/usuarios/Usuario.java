package com.dessti.crm.platform.security.usuarios;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.roles.Rol;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA de <strong>gestion</strong> de {@code usuario} (Req 4), mapeada
 * sobre la tabla {@code usuario} definida en la migracion V1.
 *
 * <p>Es el lado de <em>escritura</em> de una cuenta de acceso: creacion,
 * desactivacion y asignacion de Roles. A diferencia de
 * {@code com.dessti.crm.platform.security.auth.UsuarioAuth} (entidad de solo
 * autenticacion que modela el login y el bloqueo por intentos fallidos), esta
 * entidad modela la relacion N:M con {@link Rol} a traves del join
 * {@code usuario_rol}, ademas de {@code activo}, {@code tenant_id},
 * {@code identificador_acceso}, {@code hash_password} y las columnas de
 * auditoria.</p>
 *
 * <p><strong>Dos mapeos sobre la misma tabla (ver {@code package-info}):</strong>
 * ambas entidades conviven porque se usan en casos de uso separados (login vs
 * administracion). Los nombres y la nulabilidad de las columnas coinciden
 * exactamente con V1 para que un arranque con {@code ddl-auto=validate} valide
 * sin conflictos.</p>
 *
 * <p><strong>Secretos (Req 11.3):</strong> {@link #hashPassword} guarda el hash
 * BCrypt de la contrasena; nunca debe registrarse en logs, exponerse en DTO ni
 * incluirse en eventos de auditoria.</p>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> {@code tenant_id} es nullable en la
 * tabla (el {@code super_admin} es de plataforma), por lo que esta entidad no
 * es tenant-scoped a nivel de Hibernate; el aislamiento por Empresa lo aplica
 * {@code ServicioUsuarios} derivando el {@code tenant_id} del contexto
 * autenticado (Req 23.4).</p>
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Empresa propietaria; {@code null} solo para el {@code super_admin} de plataforma. */
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    /** Identificador de acceso (login). UNICO GLOBAL segun la decision de V1. */
    @Column(name = "identificador_acceso", nullable = false, updatable = false)
    private String identificadorAcceso;

    /** Hash BCrypt de la contrasena; secreto, nunca se expone ni se audita (Req 11.3). */
    @Column(name = "hash_password", nullable = false)
    private String hashPassword;

    /**
     * Nombre PARA MOSTRAR del Usuario (Req 4), separado del
     * {@link #identificadorAcceso} (correo/login). Opcional (NULLABLE, V57): las
     * cuentas existentes conservan {@code null}. Se normaliza recortando espacios
     * y no debe exceder {@value #LONGITUD_MAXIMA_NOMBRE_VISIBLE} caracteres.
     */
    @Column(name = "nombre_visible")
    private String nombreVisible;

    /** Cuenta habilitada para iniciar sesion; {@code false} tras la desactivacion (Req 4.2). */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    /**
     * {@code true} cuando la cuenta debe cambiar su contrasena en el proximo
     * inicio de sesion (contrasena temporal generada o fijada por el super_admin,
     * V69). Se limpia cuando el Usuario cambia su propia contrasena.
     */
    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword;

    /**
     * Contador de fallos de autenticacion (Req 2.1). Lo gobierna la ruta de
     * login ({@code UsuarioAuth}); aqui se mapea solo para que el INSERT de una
     * cuenta nueva fije su valor inicial y para respetar la nulabilidad de V1.
     */
    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    /** Instante (UTC) hasta el cual la cuenta esta bloqueada; gestionado por el login. */
    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    /** Version para bloqueo optimista (Req 49) en las escrituras de gestion. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Roles asignados al Usuario (join {@code usuario_rol}, Req 4.1, 4.3).
     *
     * <p>Se reutiliza la entidad {@link Rol} del paquete {@code roles} (no se
     * crea una entidad competidora). La relacion se mantiene sin cascada: los
     * roles ya existen y solo se asocian/desasocian; nunca se crean ni borran
     * roles a traves de esta relacion.</p>
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<Rol> roles = new HashSet<>();

    protected Usuario() {
        // Requerido por JPA.
    }

    /**
     * Crea una nueva cuenta de Usuario activa para una Empresa (Req 4.1).
     *
     * @param tenantId            Empresa propietaria; no {@code null} (una cuenta
     *                            de empresa siempre pertenece a un tenant).
     * @param identificadorAcceso identificador de acceso (login); obligatorio.
     * @param hashPassword        hash BCrypt ya calculado de la contrasena; nunca
     *                            la contrasena en claro.
     * @param nombreVisible       nombre para mostrar (Req 4); opcional
     *                            ({@code null}/blanco lo deja sin definir). Se
     *                            normaliza (recorte) y se acota a
     *                            {@value #LONGITUD_MAXIMA_NOMBRE_VISIBLE} caracteres.
     * @param roles               roles iniciales a asignar (al menos uno, Req 4.1).
     * @param actor               identificador de quien crea la cuenta (para las
     *                            columnas {@code created_by}/{@code updated_by}).
     * @return la cuenta lista para persistir.
     * @throws ReglaNegocioException si el nombre visible excede su longitud maxima.
     */
    public static Usuario crear(UUID tenantId, String identificadorAcceso, String hashPassword,
                                String nombreVisible, Set<Rol> roles, String actor) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Un Usuario de empresa requiere tenant_id");
        }
        if (identificadorAcceso == null || identificadorAcceso.isBlank()) {
            throw new IllegalArgumentException("El identificador de acceso es obligatorio");
        }
        if (hashPassword == null || hashPassword.isBlank()) {
            throw new IllegalArgumentException("El hash de la contrasena es obligatorio");
        }
        Usuario usuario = new Usuario();
        usuario.id = UUID.randomUUID();
        usuario.tenantId = tenantId;
        usuario.identificadorAcceso = identificadorAcceso.strip();
        usuario.hashPassword = hashPassword;
        usuario.nombreVisible = normalizarNombreVisible(nombreVisible);
        usuario.activo = true;
        usuario.debeCambiarPassword = false;
        usuario.intentosFallidos = 0;
        usuario.bloqueadoHasta = null;
        usuario.roles = new HashSet<>(roles != null ? roles : Set.of());
        usuario.createdBy = actor;
        usuario.updatedBy = actor;
        return usuario;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Desactiva la cuenta para impedir su inicio de sesion (Req 4.2). El login
     * ya rechaza las cuentas {@code activo = false}, por lo que no requiere
     * ningun cambio adicional en la ruta de autenticacion.
     *
     * @param actor identificador de quien desactiva la cuenta.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.updatedBy = actor;
    }

    /**
     * Reemplaza el conjunto completo de Roles del Usuario (Req 4.3).
     *
     * <p>Los Permisos del nuevo conjunto de Roles aplican en la
     * <em>siguiente</em> evaluacion de autorizacion: como las authorities se
     * leen del JWT en cada peticion y se reemiten al refrescar el token, el
     * cambio surte efecto al emitirse/refrescarse el proximo token. La
     * revocacion inmediata de sesiones vigentes corresponde a la tarea 11.2.</p>
     *
     * @param nuevos roles a asignar (al menos uno; lo valida el servicio).
     * @param actor  identificador de quien realiza el cambio.
     */
    public void reemplazarRoles(Set<Rol> nuevos, String actor) {
        this.roles = new HashSet<>(nuevos != null ? nuevos : Set.of());
        this.updatedBy = actor;
    }

    /**
     * Reemplaza el hash BCrypt de la contrasena de la cuenta (Req 4, 68.4). Lo
     * usan tanto el cambio de contrasena propio (perfil) como el restablecimiento
     * por parte del {@code super_admin} sobre el {@code admin_empresa} de una
     * Empresa.
     *
     * <p><strong>Secreto (Req 11.3):</strong> recibe el hash <em>ya calculado</em>
     * por {@code PasswordEncoder}; nunca la contrasena en claro. El hash no se
     * registra en logs ni se audita.</p>
     *
     * @param nuevoHash hash BCrypt de la nueva contrasena; obligatorio.
     * @param actor     identificador de quien realiza el cambio (columna
     *                  {@code updated_by}).
     */
    public void cambiarPassword(String nuevoHash, String actor) {
        if (nuevoHash == null || nuevoHash.isBlank()) {
            throw new IllegalArgumentException("El hash de la contrasena es obligatorio");
        }
        this.hashPassword = nuevoHash;
        // El cambio de contrasena limpia la obligacion de cambio (V69): si el
        // Usuario esta cambiando su propia temporal, deja de estar forzado.
        this.debeCambiarPassword = false;
        this.updatedBy = actor;
    }

    /**
     * Reemplaza el hash de la contrasena y MARCA que la cuenta debe cambiarla en
     * el proximo inicio de sesion (V69). Lo usan el alta de Empresa y el
     * restablecimiento por el super_admin, ya que en ambos casos la contrasena la
     * fija alguien distinto del propio Usuario (temporal o explicita), por lo que
     * este debe cambiarla al entrar.
     *
     * @param nuevoHash hash BCrypt de la nueva contrasena; obligatorio.
     * @param actor     identificador de quien realiza el cambio.
     */
    public void cambiarPasswordYForzarCambio(String nuevoHash, String actor) {
        if (nuevoHash == null || nuevoHash.isBlank()) {
            throw new IllegalArgumentException("El hash de la contrasena es obligatorio");
        }
        this.hashPassword = nuevoHash;
        this.debeCambiarPassword = true;
        this.updatedBy = actor;
    }

    /** @return {@code true} si la cuenta debe cambiar su contrasena al iniciar sesion (V69). */
    public boolean isDebeCambiarPassword() {
        return debeCambiarPassword;
    }

    /**
     * Longitud maxima del nombre para mostrar, alineada con la columna
     * {@code usuario.nombre_visible VARCHAR(200)} de V57 (Req 4).
     */
    static final int LONGITUD_MAXIMA_NOMBRE_VISIBLE = 200;

    /**
     * Actualiza el nombre PARA MOSTRAR del Usuario (Req 4). Es un dato
     * descriptivo, independiente del identificador de acceso (login): un valor
     * {@code null} o en blanco lo deja "sin definir" ({@code null}), permitiendo
     * tanto establecer como limpiar el nombre. Se normaliza recortando espacios y
     * se acota a {@value #LONGITUD_MAXIMA_NOMBRE_VISIBLE} caracteres. Actualiza
     * {@code updated_by} para la trazabilidad de auditoria.
     *
     * @param nombreVisible nuevo nombre para mostrar; {@code null}/blanco lo limpia.
     * @param actor         identificador de quien realiza el cambio.
     * @throws ReglaNegocioException si el nombre visible excede su longitud maxima (HTTP 422).
     */
    public void actualizarNombreVisible(String nombreVisible, String actor) {
        this.nombreVisible = normalizarNombreVisible(nombreVisible);
        this.updatedBy = actor;
    }

    private static String normalizarNombreVisible(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE_VISIBLE) {
            throw new ReglaNegocioException(
                    "El nombre para mostrar no puede exceder "
                            + LONGITUD_MAXIMA_NOMBRE_VISIBLE + " caracteres.");
        }
        return normalizado;
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
     * @return el nombre para mostrar del Usuario (Req 4), o {@code null} si no se
     *         ha definido. Es independiente del identificador de acceso (login).
     */
    public String getNombreVisible() {
        return nombreVisible;
    }

    /**
     * @return el hash BCrypt de la contrasena. Nunca debe registrarse en logs ni
     *         exponerse en respuestas (Req 11.3).
     */
    public String getHashPassword() {
        return hashPassword;
    }

    public boolean isActivo() {
        return activo;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Set<Rol> getRoles() {
        return roles;
    }
}
