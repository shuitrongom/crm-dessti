package com.dessti.crm.social.domain;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code publicacion_social}: una
 * Publicacion_Social programada hacia un {@link CanalSocial} de Meta a traves de
 * una {@link CuentaCanalSocial}, mapeada sobre la tabla {@code publicacion_social}
 * de la migracion V43 (Req 65.1-65.6, 23, 49).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado desde el {@code TenantContext} al persistir,
 * Req 23.4), {@code version} (concurrencia optimista, Req 49) y las marcas de
 * auditoria. El mapeo de columnas coincide <em>exactamente</em> con V43.</p>
 *
 * <h2>Maquina de estados (Req 65.3, 65.4)</h2>
 * <p>El {@link EstadoPublicacion} gobierna el ciclo de vida:
 * {@code borrador -> programada -> {publicada|fallida}}. {@code publicada} y
 * {@code fallida} son finales. Toda transicion no permitida se rechaza con
 * {@link TransicionInvalidaException} (409) conservando el estado. La aplicacion
 * ({@code ServicioPublicaciones}) invoca {@link #programar(String)},
 * {@link #marcarPublicada(String, String)} y {@link #marcarFallida(String, String)}
 * segun el resultado del adaptador {@code PublicacionSocialPort} (Req 65.5, 65.6).</p>
 *
 * <h2>Canal aplicable (nota de diseno)</h2>
 * <p>El Req 65.1 menciona Facebook/Instagram para la Publicacion_Social. El CHECK
 * de V43 reutiliza el dominio de {@code canal} de V41 ({@code whatsapp}/
 * {@code messenger}/{@code instagram}) por coherencia; en la practica la
 * Publicacion_Social aplica a {@code messenger} (paginas de Facebook) e
 * {@code instagram}. La restriccion de subconjunto se documenta pero no se fuerza
 * en BD para no divergir del dominio de canal ya establecido.</p>
 */
@Entity
@Table(name = "publicacion_social")
public class PublicacionSocial extends TenantScopedEntity {

    /** Longitud maxima del identificador externo (coincide con VARCHAR(120) de V43). */
    static final int MAX_EXTERNO_ID = 120;

    /** Longitud maxima del motivo de fallo (coincide con VARCHAR(500) de V43). */
    static final int MAX_MOTIVO_FALLO = 500;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cuenta_Canal_Social por la que se publica; inmutable (Req 65.1). */
    @Column(name = "cuenta_canal_social_id", nullable = false, updatable = false)
    private UUID cuentaCanalSocialId;

    /** Canal_Social de la publicacion; inmutable (Req 65.1). */
    @Convert(converter = CanalSocialConverter.class)
    @Column(name = "canal", nullable = false, length = 12, updatable = false)
    private CanalSocial canal;

    /** Contenido de la publicacion; no vacio; inmutable (Req 65.1, 65.2). */
    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String contenido;

    /** Fecha/hora programada de publicacion (UTC); inmutable (Req 65.1, 65.5). */
    @Column(name = "fecha_programada", nullable = false, updatable = false)
    private Instant fechaProgramada;

    /** Estado de la Publicacion_Social (maquina de estados, Req 65.3). */
    @Convert(converter = EstadoPublicacionConverter.class)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoPublicacion estado;

    /** Identificador externo devuelto por el proveedor al publicar; {@code null} si no aplica. */
    @Column(name = "externo_id", length = MAX_EXTERNO_ID)
    private String externoId;

    /** Instante de publicacion confirmada (UTC); {@code null} mientras no se publica. */
    @Column(name = "publicada_en")
    private Instant publicadaEn;

    /** Motivo del fallo definitivo; {@code null} salvo en estado {@code fallida}. */
    @Column(name = "motivo_fallo", length = MAX_MOTIVO_FALLO)
    private String motivoFallo;

    protected PublicacionSocial() {
        // Requerido por JPA.
    }

    /**
     * Crea una Publicacion_Social en estado inicial {@link EstadoPublicacion#BORRADOR}
     * validando el contenido y la fecha programada (Req 65.1, 65.2). La fecha
     * programada no puede ser anterior al momento actual ({@code ahora}), que la
     * aplicacion obtiene del {@code Clock} del servicio. El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param cuentaCanalSocialId Cuenta_Canal_Social por la que se publicara; obligatorio.
     * @param canal               Canal_Social; obligatorio.
     * @param contenido           contenido de la publicacion; obligatorio (no vacio).
     * @param fechaProgramada     instante programado (UTC); obligatorio y no pasado.
     * @param ahora               instante actual (UTC) del reloj del servicio; obligatorio.
     * @param actor               identificador de quien crea (auditoria).
     * @return la Publicacion_Social lista para persistir, en {@code borrador}.
     * @throws ReglaNegocioException si algun dato obligatorio falta o la fecha
     *         programada es anterior al momento actual (422, Req 65.2).
     */
    public static PublicacionSocial crear(UUID cuentaCanalSocialId, CanalSocial canal, String contenido,
                                          Instant fechaProgramada, Instant ahora, String actor) {
        if (cuentaCanalSocialId == null) {
            throw new ReglaNegocioException(
                    "La Publicacion_Social debe asociarse a una Cuenta_Canal_Social.");
        }
        if (canal == null) {
            throw new ReglaNegocioException("La Publicacion_Social debe indicar el Canal_Social.");
        }
        if (contenido == null || contenido.isBlank()) {
            throw new ReglaNegocioException("La Publicacion_Social debe indicar el contenido.");
        }
        if (fechaProgramada == null) {
            throw new ReglaNegocioException("La Publicacion_Social debe indicar la fecha programada.");
        }
        if (ahora == null) {
            throw new ReglaNegocioException("Falta la marca temporal actual para validar la fecha programada.");
        }
        if (fechaProgramada.isBefore(ahora)) {
            throw new ReglaNegocioException(
                    "La fecha programada no puede ser anterior al momento actual.");
        }

        PublicacionSocial publicacion = new PublicacionSocial();
        publicacion.id = UUID.randomUUID();
        publicacion.cuentaCanalSocialId = cuentaCanalSocialId;
        publicacion.canal = canal;
        publicacion.contenido = contenido.strip();
        publicacion.fechaProgramada = fechaProgramada;
        publicacion.estado = EstadoPublicacion.BORRADOR;
        publicacion.setCreatedBy(actor);
        publicacion.setUpdatedBy(actor);
        return publicacion;
    }

    /**
     * Programa la Publicacion_Social: transicion {@code borrador -> programada}
     * (Req 65.3).
     *
     * @param actor identificador de quien programa, para {@code updated_by}.
     * @throws TransicionInvalidaException si la publicacion no esta en
     *         {@code borrador} (409, Req 65.4).
     */
    public void programar(String actor) {
        transicionarA(EstadoPublicacion.PROGRAMADA, actor);
    }

    /**
     * Marca la Publicacion_Social como publicada tras la confirmacion del adaptador:
     * transicion {@code programada -> publicada} (Req 65.5). Registra el id externo
     * del proveedor y el instante de publicacion.
     *
     * @param externoId   id de la publicacion en el proveedor; opcional.
     * @param publicadaEn instante de publicacion (UTC); obligatorio.
     * @param actor       identificador de quien publica, para {@code updated_by}.
     * @throws TransicionInvalidaException si la publicacion no esta {@code programada}
     *         (409, Req 65.4).
     */
    public void marcarPublicada(String externoId, Instant publicadaEn, String actor) {
        transicionarA(EstadoPublicacion.PUBLICADA, actor);
        this.externoId = normalizarExternoId(externoId);
        this.publicadaEn = publicadaEn;
        this.motivoFallo = null;
    }

    /**
     * Marca la Publicacion_Social como fallida tras agotar la politica de reintentos:
     * transicion {@code programada -> fallida} (Req 65.5, 65.6). Registra el motivo.
     *
     * @param motivo motivo del fallo definitivo; obligatorio.
     * @param actor  identificador de quien registra el fallo, para {@code updated_by}.
     * @throws TransicionInvalidaException si la publicacion no esta {@code programada}
     *         (409, Req 65.4).
     */
    public void marcarFallida(String motivo, String actor) {
        transicionarA(EstadoPublicacion.FALLIDA, actor);
        this.motivoFallo = normalizarMotivo(motivo);
    }

    /**
     * Aplica una transicion de estado por la maquina de estados pura (Req 65.3).
     * Toda transicion no permitida —incluida cualquiera que parta de un estado
     * final— se rechaza con {@link TransicionInvalidaException} (409, Req 65.4)
     * conservando el estado actual.
     *
     * @param destino estado destino; obligatorio.
     * @param actor   identificador de quien transiciona, para {@code updated_by}.
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    private void transicionarA(EstadoPublicacion destino, String actor) {
        if (destino == null) {
            throw new ReglaNegocioException("El estado destino de la Publicacion_Social es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + destino.valorBd() + "'.");
        }
        this.estado = destino;
        this.setUpdatedBy(actor);
    }

    private static String normalizarExternoId(String externoId) {
        if (externoId == null || externoId.isBlank()) {
            return null;
        }
        String limpio = externoId.strip();
        return (limpio.length() > MAX_EXTERNO_ID) ? limpio.substring(0, MAX_EXTERNO_ID) : limpio;
    }

    private static String normalizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return null;
        }
        String limpio = motivo.strip();
        return (limpio.length() > MAX_MOTIVO_FALLO) ? limpio.substring(0, MAX_MOTIVO_FALLO) : limpio;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCuentaCanalSocialId() {
        return cuentaCanalSocialId;
    }

    public CanalSocial getCanal() {
        return canal;
    }

    public String getContenido() {
        return contenido;
    }

    public Instant getFechaProgramada() {
        return fechaProgramada;
    }

    public EstadoPublicacion getEstado() {
        return estado;
    }

    public String getExternoId() {
        return externoId;
    }

    public Instant getPublicadaEn() {
        return publicadaEn;
    }

    public String getMotivoFallo() {
        return motivoFallo;
    }
}
