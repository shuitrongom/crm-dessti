package com.dessti.crm.platform.giros.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA del {@code giro}: catalogo de Giros (verticales de negocio) de la
 * plataforma multigiro, mapeada sobre la tabla {@code giro} de la migracion V50
 * (Req 1.2). Tabla de <strong>plataforma</strong> (sin {@code tenant_id}, sin
 * RLS, Req 8.4), administrada por el {@code super_admin}: un Giro no pertenece a
 * ninguna Empresa, es compartido por todas. El Giro es un <em>atributo</em> de
 * la Empresa, nunca un eje de aislamiento (Req 8.1).
 *
 * <p>El mapeo de columnas (nombres, nulabilidad, longitudes, {@link Version} y
 * columnas de auditoria) coincide <em>exactamente</em> con V50 para que un
 * arranque con {@code ddl-auto=validate} valide sin conflictos, replicando el
 * patron de {@code platform.empresas.Empresa} y
 * {@code platform.monetizacion.domain.CatalogoModulo}.</p>
 *
 * <p>La {@link #getClave() clave} es la clave canonica del vertical (p. ej.
 * {@code anuncios-luminosos}): unica en toda la plataforma e <strong>inmutable</strong>
 * tras el alta. Se persiste normalizada a minusculas en formato kebab mediante
 * {@link #normalizarClave(String)} (Req 1.2); la unicidad la garantiza la
 * restriccion {@code uq_giro_clave} de V50.</p>
 *
 * <p><strong>Alcance (Tarea 2.2):</strong> esta entidad modela el alta
 * ({@link #crear(String, String, String, String)}) y los cambios de estado
 * ({@link #activar(String)} / {@link #desactivar(String)}). El repositorio, el
 * servicio de aplicacion y el controlador REST corresponden a las tareas
 * 2.4/2.5/2.6 y no se implementan aqui.</p>
 */
@Entity
@Table(name = "giro")
public class Giro {

    /**
     * Longitud maxima de la clave canonica, alineada con la columna
     * {@code clave VARCHAR(60)} de V50 (Req 1.2).
     */
    static final int LONGITUD_MAXIMA_CLAVE = 60;

    /**
     * Longitud maxima del nombre visible, alineada con la columna
     * {@code nombre_visible VARCHAR(150)} de V50.
     */
    static final int LONGITUD_MAXIMA_NOMBRE_VISIBLE = 150;

    /**
     * Caracteres que NO pertenecen al alfabeto kebab (letras {@code a-z},
     * digitos {@code 0-9}). Cualquier secuencia de estos caracteres actua como
     * separador de palabras y se colapsa en un unico guion (Req 1.2).
     */
    private static final Pattern SEPARADORES = Pattern.compile("[^a-z0-9]+");

    /** Guiones sobrantes al inicio o al final, que se recortan tras el colapso. */
    private static final Pattern GUIONES_EXTREMOS = Pattern.compile("^-+|-+$");

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Clave canonica normalizada (minusculas, kebab); unica e inmutable (Req 1.2). */
    @Column(name = "clave", nullable = false, updatable = false, length = 60)
    private String clave;

    @Column(name = "nombre_visible", nullable = false, length = 150)
    private String nombreVisible;

    /** Descripcion opcional del vertical de negocio. */
    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "activo", nullable = false)
    private boolean activo;

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

    protected Giro() {
        // Requerido por JPA.
    }

    /**
     * Crea un Giro nuevo en estado activo con un identificador unico y su clave
     * canonica normalizada a minusculas/kebab (Req 1.2).
     *
     * @param clave         clave canonica del vertical (p. ej.
     *                      {@code anuncios-luminosos}); obligatoria. Se normaliza
     *                      mediante {@link #normalizarClave(String)} y no puede
     *                      quedar vacia tras normalizar.
     * @param nombreVisible nombre visible del Giro; obligatorio.
     * @param descripcion   descripcion del vertical; opcional ({@code null}/vacia
     *                      se almacena como {@code null}).
     * @param actor         identificador de quien crea el Giro ({@code super_admin}),
     *                      para las columnas de auditoria {@code created_by}/{@code updated_by}.
     * @return el Giro listo para persistir.
     * @throws ReglaNegocioException si la clave o el nombre visible son vacios,
     *                               si la clave normalizada queda vacia, o si
     *                               exceden su longitud maxima permitida.
     */
    public static Giro crear(String clave, String nombreVisible, String descripcion, String actor) {
        Giro giro = new Giro();
        giro.id = UUID.randomUUID();
        giro.clave = normalizarClave(clave);
        giro.nombreVisible = normalizarNombreVisible(nombreVisible);
        giro.descripcion = normalizarDescripcion(descripcion);
        giro.activo = true;
        giro.createdBy = actor;
        giro.updatedBy = actor;
        return giro;
    }

    /**
     * Normaliza una clave de Giro a su forma canonica: minusculas, sin espacios
     * envolventes y en formato kebab (palabras separadas por un unico guion, sin
     * guiones en los extremos), coherente con la clave sembrada
     * {@code anuncios-luminosos} de V50 (Req 1.2).
     *
     * <p>El algoritmo es <strong>idempotente</strong>: normalizar el resultado de
     * una normalizacion produce exactamente la misma clave, porque tras la
     * primera pasada la cadena ya esta en minusculas, sin caracteres ajenos al
     * alfabeto kebab y sin guiones repetidos ni extremos. Pasos:</p>
     * <ol>
     *   <li>recorte de espacios envolventes y paso a minusculas;</li>
     *   <li>colapso de toda secuencia de caracteres no {@code [a-z0-9]} (espacios,
     *       guiones bajos, guiones repetidos, signos) en un unico guion;</li>
     *   <li>recorte de los guiones sobrantes al inicio y al final.</li>
     * </ol>
     *
     * @param valor clave a normalizar; obligatoria.
     * @return la clave canonica normalizada (minusculas, kebab, no vacia).
     * @throws ReglaNegocioException si es nula/vacia, si al normalizar no queda
     *                               ningun caracter valido, o si excede
     *                               {@value #LONGITUD_MAXIMA_CLAVE} caracteres.
     */
    public static String normalizarClave(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("La clave del Giro es obligatoria.");
        }
        String minusculas = valor.strip().toLowerCase(Locale.ROOT);
        String kebab = SEPARADORES.matcher(minusculas).replaceAll("-");
        String normalizado = GUIONES_EXTREMOS.matcher(kebab).replaceAll("");
        if (normalizado.isEmpty()) {
            throw new ReglaNegocioException(
                    "La clave del Giro debe contener al menos un caracter alfanumerico.");
        }
        if (normalizado.length() > LONGITUD_MAXIMA_CLAVE) {
            throw new ReglaNegocioException(
                    "La clave del Giro no puede exceder " + LONGITUD_MAXIMA_CLAVE + " caracteres.");
        }
        return normalizado;
    }

    private static String normalizarNombreVisible(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre visible del Giro es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE_VISIBLE) {
            throw new ReglaNegocioException(
                    "El nombre visible del Giro no puede exceder "
                            + LONGITUD_MAXIMA_NOMBRE_VISIBLE + " caracteres.");
        }
        return normalizado;
    }

    private static String normalizarDescripcion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.strip();
    }

    /**
     * Activa el Giro (Req 1.1): queda disponible para asignarse a Empresas. Es
     * idempotente respecto al estado activo.
     *
     * @param actor identificador de quien realiza la operacion ({@code super_admin}),
     *              para {@code updated_by}.
     */
    public void activar(String actor) {
        this.activo = true;
        this.updatedBy = actor;
    }

    /**
     * Desactiva el Giro (baja logica, Req 1.1): deja de ofrecerse en el alta de
     * nuevas Empresas. Es idempotente respecto al estado inactivo. La regla que
     * impide desactivar un Giro en uso por alguna Empresa (Req 1.5) la aplica el
     * servicio de aplicacion (Tarea 2.5), no el dominio.
     *
     * @param actor identificador de quien realiza la operacion ({@code super_admin}),
     *              para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.updatedBy = actor;
    }

    /**
     * Actualiza los datos EDITABLES del Giro: el nombre visible (obligatorio) y
     * la descripcion (opcional). La clave canonica es INMUTABLE (se usa como
     * identificador estable en todo el sistema) y no se toca aqui. Reutiliza las
     * mismas normalizaciones/validaciones que el alta.
     *
     * @param nombreVisible nuevo nombre visible; obligatorio (1..longitud maxima).
     * @param descripcion   nueva descripcion; opcional (null/blanco la limpia).
     * @param actor         identificador de quien edita (columna updated_by).
     * @throws ReglaNegocioException si el nombre visible es vacio o excede su
     *                               longitud maxima.
     */
    public void actualizar(String nombreVisible, String descripcion, String actor) {
        this.nombreVisible = normalizarNombreVisible(nombreVisible);
        this.descripcion = normalizarDescripcion(descripcion);
        this.updatedBy = actor;
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

    public UUID getId() {
        return id;
    }

    public String getClave() {
        return clave;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }

    public String getDescripcion() {
        return descripcion;
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
}
