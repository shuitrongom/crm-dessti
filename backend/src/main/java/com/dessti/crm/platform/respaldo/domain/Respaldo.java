package com.dessti.crm.platform.respaldo.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA de {@code respaldo}: bitacora de ejecuciones de respaldo y
 * restauracion (Req 50), mapeada sobre la tabla {@code respaldo} de la
 * migracion V46. Es una tabla de <strong>plataforma</strong> (sin tenant, sin
 * RLS), administrada por el {@code super_admin}.
 *
 * <p>Guarda unicamente <strong>metadatos</strong>: nunca el contenido de los
 * datos respaldados ni el material de la {@code Llave_Cifrado} (Req 67.2). El
 * campo {@link #aliasLlave} referencia la <em>version</em> de llave usada para
 * cifrar el artefacto, de modo que la restauracion pueda descifrarlo con la
 * llave correcta tras una rotacion (Req 67).</p>
 *
 * <p>El ciclo de estado es: {@code EN_PROCESO} al iniciar y luego
 * {@code COMPLETADO} o {@code FALLIDO}. La marca {@link #instante} y las de
 * auditoria se fijan en UTC (Req 50.4).</p>
 */
@Entity
@Table(name = "respaldo")
public class Respaldo {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Convert(converter = TipoRespaldoConverter.class)
    @Column(name = "tipo", nullable = false, updatable = false, length = 20)
    private TipoRespaldo tipo;

    @Convert(converter = EstadoRespaldoConverter.class)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoRespaldo estado;

    @Column(name = "alcance", nullable = false, length = 60)
    private String alcance;

    @Column(name = "instante", nullable = false, updatable = false)
    private Instant instante;

    @Column(name = "ubicacion", length = 1000)
    private String ubicacion;

    @Column(name = "alias_llave", length = 60)
    private String aliasLlave;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "detalle_error", length = 2000)
    private String detalleError;

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

    protected Respaldo() {
        // Requerido por JPA.
    }

    /**
     * Crea una ejecucion en estado {@code EN_PROCESO}. La ubicacion, el alias de
     * llave, el checksum y el tamano se completan al finalizar con exito.
     *
     * @param tipo    tipo de operacion (respaldo o restauracion); obligatorio.
     * @param alcance descripcion del alcance (por defecto {@code completo}).
     * @param actor   identificador de quien dispara la operacion; obligatorio.
     * @return la ejecucion lista para persistir.
     */
    public static Respaldo iniciar(TipoRespaldo tipo, String alcance, String actor) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de respaldo es obligatorio");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("El actor de la operacion de respaldo es obligatorio");
        }
        Respaldo r = new Respaldo();
        r.id = UUID.randomUUID();
        r.tipo = tipo;
        r.estado = EstadoRespaldo.EN_PROCESO;
        r.alcance = (alcance == null || alcance.isBlank()) ? "completo" : alcance.strip();
        r.instante = Instant.now();
        r.actor = actor;
        r.createdBy = actor;
        r.updatedBy = actor;
        return r;
    }

    /**
     * Marca la ejecucion como completada, registrando la referencia del
     * artefacto cifrado y sus metadatos de integridad.
     *
     * @param ubicacion   ruta/referencia del artefacto cifrado (sin contenido).
     * @param aliasLlave  alias/version de la Llave_Cifrado usada (sin material).
     * @param checksum    hash SHA-256 (hex) del artefacto cifrado.
     * @param tamanoBytes tamano del artefacto cifrado en bytes; puede ser {@code null}
     *                    para restauraciones.
     */
    public void completar(String ubicacion, String aliasLlave, String checksum, Long tamanoBytes) {
        this.estado = EstadoRespaldo.COMPLETADO;
        this.ubicacion = ubicacion;
        this.aliasLlave = aliasLlave;
        this.checksum = checksum;
        this.tamanoBytes = tamanoBytes;
    }

    /**
     * Marca la ejecucion como fallida con un detalle legible del error (sin
     * secretos ni material de llave, Req 67.2).
     *
     * @param detalleError descripcion del fallo; se recorta a 2000 caracteres.
     */
    public void fallar(String detalleError) {
        this.estado = EstadoRespaldo.FALLIDO;
        if (detalleError != null) {
            this.detalleError = detalleError.length() > 2000
                    ? detalleError.substring(0, 2000)
                    : detalleError;
        }
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (instante == null) {
            instante = now;
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

    public TipoRespaldo getTipo() {
        return tipo;
    }

    public EstadoRespaldo getEstado() {
        return estado;
    }

    public String getAlcance() {
        return alcance;
    }

    public Instant getInstante() {
        return instante;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public String getAliasLlave() {
        return aliasLlave;
    }

    public String getChecksum() {
        return checksum;
    }

    public Long getTamanoBytes() {
        return tamanoBytes;
    }

    public String getActor() {
        return actor;
    }

    public String getDetalleError() {
        return detalleError;
    }

    public long getVersion() {
        return version;
    }
}
