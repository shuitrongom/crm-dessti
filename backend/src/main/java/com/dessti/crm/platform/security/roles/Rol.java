package com.dessti.crm.platform.security.roles;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
 * Entidad JPA de {@code rol} (Req 27, 28), mapeada sobre la tabla {@code rol}
 * definida en la migracion V1.
 *
 * <p>Un {@code Rol} es un conjunto nombrado de {@link PermisoEntity}. Existen
 * dos naturalezas, discriminadas por {@link #predefinido} y {@link #tenantId}:</p>
 * <ul>
 *   <li><strong>Rol predefinido de Sistema</strong> ({@code predefinido = true},
 *       {@code tenantId = null}): roles comunes a todas las Empresas sembrados
 *       por la migracion V5 (p. ej. {@code super_admin}, {@code ventas}). Son
 *       <em>inmutables</em>: no pueden modificarse ni eliminarse (Req 28.6).</li>
 *   <li><strong>Rol_Personalizado</strong> ({@code predefinido = false},
 *       {@code tenantId} de la Empresa): definido por el Administrador_Empresa
 *       combinando permisos existentes dentro del ambito de su Empresa
 *       (Req 28.2, 28.4).</li>
 * </ul>
 *
 * <p><strong>Multi-tenant (nota de modelado):</strong> a diferencia de las
 * entidades de negocio, {@code rol} NO hereda de {@code TenantScopedEntity}
 * porque su {@code tenant_id} es <em>nullable</em> (los roles predefinidos son
 * de plataforma, sin empresa). Por ello el aislamiento por tenant de los roles
 * personalizados se aplica explicitamente en la capa de aplicacion
 * ({@code ServicioRoles}), no mediante el filtro global de Hibernate.</p>
 */
@Entity
@Table(name = "rol")
public class Rol {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Empresa propietaria; {@code null} para los roles predefinidos de Sistema. */
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "predefinido", nullable = false, updatable = false)
    private boolean predefinido;

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rol_permiso",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<PermisoEntity> permisos = new HashSet<>();

    protected Rol() {
        // Requerido por JPA.
    }

    /**
     * Crea un {@code Rol_Personalizado} para una Empresa (Req 28.2).
     *
     * @param tenantId Empresa propietaria; no {@code null} (un rol de empresa
     *                 siempre pertenece a un tenant).
     * @param nombre   nombre del rol dentro de la Empresa.
     * @param permisos permisos existentes que combina el rol.
     * @param actor    identificador de quien crea el rol (para auditoria de
     *                 columnas {@code created_by}/{@code updated_by}).
     * @return el rol personalizado listo para persistir.
     */
    public static Rol personalizado(UUID tenantId, String nombre,
                                     Set<PermisoEntity> permisos, String actor) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Un Rol_Personalizado requiere tenant_id");
        }
        Rol rol = new Rol();
        rol.id = UUID.randomUUID();
        rol.tenantId = tenantId;
        rol.nombre = normalizarNombre(nombre);
        rol.predefinido = false;
        rol.permisos = new HashSet<>(permisos != null ? permisos : Set.of());
        rol.createdBy = actor;
        rol.updatedBy = actor;
        return rol;
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

    private static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El nombre del rol no puede estar vacio");
        }
        return valor.strip();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isPredefinido() {
        return predefinido;
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

    public Set<PermisoEntity> getPermisos() {
        return permisos;
    }

    /**
     * Reemplaza el conjunto de permisos del rol (solo aplicable a roles
     * personalizados; la inmutabilidad de los predefinidos la garantiza
     * {@code ServicioRoles} antes de invocar este metodo).
     *
     * @param nuevos permisos que reemplazan a los actuales.
     * @param actor  identificador de quien realiza la modificacion.
     */
    public void reemplazarPermisos(Set<PermisoEntity> nuevos, String actor) {
        this.permisos = new HashSet<>(nuevos != null ? nuevos : Set.of());
        this.updatedBy = actor;
    }

    /**
     * Renombra el rol (solo aplicable a roles personalizados).
     *
     * @param nuevoNombre nuevo nombre.
     * @param actor       identificador de quien realiza la modificacion.
     */
    public void renombrar(String nuevoNombre, String actor) {
        this.nombre = normalizarNombre(nuevoNombre);
        this.updatedBy = actor;
    }
}
