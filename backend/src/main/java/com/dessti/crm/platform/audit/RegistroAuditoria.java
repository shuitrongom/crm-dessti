package com.dessti.crm.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entidad JPA de un registro de la bitacora de auditoria inmutable (Req 10),
 * mapeada a la tabla {@code registro_auditoria} (migracion {@code V3}).
 *
 * <p><strong>Append-only (Req 10.4):</strong> esta entidad esta disenada para
 * <em>solo insercion</em>. No declara {@code @Version} ni callbacks de
 * actualizacion, y ni el servicio ni el repositorio exponen operaciones de
 * modificacion o borrado. La base de datos refuerza la inmutabilidad con un
 * trigger {@code BEFORE UPDATE OR DELETE} y con permisos restringidos del rol
 * de aplicacion (INSERT/SELECT).</p>
 *
 * <p><strong>Multi-tenant:</strong> a diferencia de las entidades de negocio,
 * NO extiende {@code TenantScopedEntity}: el {@code tenantId} es
 * <em>nullable</em> ({@code null} = ambito de plataforma, Req 24.3) y la tabla
 * no aplica el filtro/RLS por tenant, para poder registrar eventos de ambos
 * ambitos. El aislamiento de lectura por tenant se aplica en la consulta.</p>
 *
 * <p>El {@code id} es una identidad secuencial generada por la BD que
 * proporciona el orden total estable requerido por el encadenamiento por hash.
 * Los campos {@code hashPrevio}/{@code hashActual} son cadenas hexadecimales
 * SHA-256 (64 caracteres).</p>
 */
@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "actor", nullable = false, updatable = false)
    private String actor;

    @Column(name = "accion", nullable = false, updatable = false)
    private String accion;

    @Column(name = "recurso", nullable = false, updatable = false)
    private String recurso;

    @Column(name = "detalle", updatable = false)
    private String detalle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_anterior", updatable = false)
    private String valorAnterior;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_nuevo", updatable = false)
    private String valorNuevo;

    @Column(name = "trace_id", updatable = false)
    private String traceId;

    @Column(name = "timestamp_utc", nullable = false, updatable = false)
    private Instant timestampUtc;

    @Column(name = "hash_previo", nullable = false, updatable = false, length = 64)
    private String hashPrevio;

    @Column(name = "hash_actual", nullable = false, updatable = false, length = 64)
    private String hashActual;

    /** Constructor requerido por JPA. */
    protected RegistroAuditoria() {
    }

    /**
     * Crea un registro de auditoria listo para persistir. El {@code id} lo
     * asigna la base de datos al insertar.
     *
     * @param tenantId       empresa del evento; {@code null} para plataforma.
     * @param actor          actor de la accion.
     * @param accion         accion ejecutada.
     * @param recurso        recurso afectado.
     * @param detalle        detalle legible (sin secretos).
     * @param valorAnterior  JSON del estado previo (sin secretos).
     * @param valorNuevo     JSON del estado nuevo (sin secretos).
     * @param traceId        identificador de correlacion (Req 10.12).
     * @param timestampUtc   marca temporal UTC.
     * @param hashPrevio     hash del registro anterior de la cadena.
     * @param hashActual     hash de este registro.
     */
    public RegistroAuditoria(
            UUID tenantId, String actor, String accion, String recurso,
            String detalle, String valorAnterior, String valorNuevo,
            String traceId, Instant timestampUtc, String hashPrevio, String hashActual) {
        this.tenantId = tenantId;
        this.actor = actor;
        this.accion = accion;
        this.recurso = recurso;
        this.detalle = detalle;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.traceId = traceId;
        this.timestampUtc = timestampUtc;
        this.hashPrevio = hashPrevio;
        this.hashActual = hashActual;
    }

    public Long getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getActor() {
        return actor;
    }

    public String getAccion() {
        return accion;
    }

    public String getRecurso() {
        return recurso;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getTimestampUtc() {
        return timestampUtc;
    }

    public String getHashPrevio() {
        return hashPrevio;
    }

    public String getHashActual() {
        return hashActual;
    }
}
