package com.dessti.crm.operacion.proyecto.domain.evidencia;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.operacion.proyecto.domain.FaseSitioGenerica;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Evidencia documental (archivo real) que respalda el avance de un Sitio en una
 * {@link FaseSitioGenerica} concreta, con flujo de aprobacion (deber-ser
 * enterprise, Req 3.2). Cada evidencia referencia el <strong>archivo</strong>
 * guardado en el almacen de objetos (clave opaca, no el binario) y su metadato
 * (nombre original, MIME, tamano), mas el estado de aprobacion
 * ({@link EstadoEvidencia}) y el rastro de quien la subio y quien la decidio.
 *
 * <p>Multi-tenant (Req 23): extiende {@link TenantScopedEntity}; hereda
 * {@code tenant_id} (asignado al persistir, nunca desde la peticion),
 * {@code version} y las marcas de auditoria. El mapeo coincide con la migracion
 * V84 (tabla {@code evidencia_avance_sitio}, con RLS).</p>
 *
 * <p>Ciclo de vida: se crea {@link EstadoEvidencia#PENDIENTE} con
 * {@link #subir}; un supervisor la {@link #aprobar} o {@link #rechazar} (registra
 * decididaPor/decididaEn y, en rechazo, el motivo). Decidir una evidencia que no
 * esta pendiente lanza {@link TransicionInvalidaException} (409).</p>
 */
@Entity
@Table(name = "evidencia_avance_sitio")
public class EvidenciaAvanceSitio extends TenantScopedEntity {

    /** Longitud maxima del nombre original del archivo (coincide con V84). */
    public static final int LONGITUD_MAXIMA_NOMBRE = 255;

    /** Longitud maxima del motivo de rechazo (coincide con V84). */
    public static final int LONGITUD_MAXIMA_MOTIVO = 500;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Avance de Sitio al que respalda esta evidencia. Inmutable; FK a {@code avance_sitio}. */
    @Column(name = "avance_sitio_id", nullable = false, updatable = false)
    private UUID avanceSitioId;

    /**
     * Fase que la evidencia respalda al momento de subirla (Req 3.2). Se congela
     * en el alta para que una evidencia siga vinculada a la etapa que documento,
     * aunque el Sitio avance despues.
     */
    @Convert(converter = com.dessti.crm.operacion.proyecto.domain.FaseSitioGenericaConverter.class)
    @Column(name = "fase", nullable = false, updatable = false)
    private FaseSitioGenerica fase;

    /** Clave opaca del archivo en el almacen de objetos (no el binario). Inmutable. */
    @Column(name = "clave_almacen", nullable = false, updatable = false)
    private String claveAlmacen;

    /** Nombre original del archivo subido (para mostrar/descargar). Inmutable. */
    @Column(name = "nombre_original", nullable = false, updatable = false)
    private String nombreOriginal;

    /** Tipo MIME del archivo (image/jpeg, application/pdf, ...). Inmutable. */
    @Column(name = "tipo_mime", nullable = false, updatable = false)
    private String tipoMime;

    /** Tamano del archivo en bytes. Inmutable. */
    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private long tamanoBytes;

    /** Estado de aprobacion (Req 3.2). */
    @Convert(converter = EstadoEvidenciaConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoEvidencia estado;

    /** Motivo del rechazo; {@code null} salvo cuando el estado es RECHAZADA. */
    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    /** Instante UTC en que se subio la evidencia. */
    @Column(name = "subida_en", nullable = false, updatable = false)
    private Instant subidaEn;

    /** Actor que decidio (aprobo/rechazo); {@code null} mientras esta pendiente. */
    @Column(name = "decidida_por")
    private String decididaPor;

    /** Instante UTC de la decision; {@code null} mientras esta pendiente. */
    @Column(name = "decidida_en")
    private Instant decididaEn;

    protected EvidenciaAvanceSitio() {
        // Requerido por JPA.
    }

    /**
     * Registra una evidencia recien subida en estado {@link EstadoEvidencia#PENDIENTE}
     * (Req 3.2). El archivo ya fue persistido en el almacen; aqui solo se guarda su
     * referencia y metadatos. El {@code tenant_id} lo fija {@link TenantScopedEntity}.
     *
     * @param avanceSitioId  avance de Sitio respaldado; obligatorio.
     * @param fase           fase que documenta la evidencia; obligatoria.
     * @param claveAlmacen   clave del archivo en el almacen; obligatoria.
     * @param nombreOriginal nombre original del archivo; obligatorio (1..255).
     * @param tipoMime       tipo MIME; obligatorio.
     * @param tamanoBytes    tamano en bytes; debe ser positivo.
     * @param actor          identificador de quien sube; obligatorio.
     * @param clock          reloj UTC para fijar {@code subida_en}; obligatorio.
     * @return la evidencia lista para persistir, en estado pendiente.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static EvidenciaAvanceSitio subir(UUID avanceSitioId, FaseSitioGenerica fase,
                                             String claveAlmacen, String nombreOriginal,
                                             String tipoMime, long tamanoBytes,
                                             String actor, Clock clock) {
        if (avanceSitioId == null) {
            throw new ReglaNegocioException("La evidencia debe asociarse a un avance de Sitio.");
        }
        if (fase == null) {
            throw new ReglaNegocioException("La evidencia debe indicar la fase que respalda.");
        }
        if (claveAlmacen == null || claveAlmacen.isBlank()) {
            throw new ReglaNegocioException("La evidencia debe referenciar un archivo almacenado.");
        }
        if (tipoMime == null || tipoMime.isBlank()) {
            throw new ReglaNegocioException("El tipo de archivo de la evidencia es obligatorio.");
        }
        if (tamanoBytes <= 0) {
            throw new ReglaNegocioException("El archivo de la evidencia esta vacio.");
        }
        if (clock == null) {
            throw new ReglaNegocioException("El reloj para fijar la fecha de la evidencia es obligatorio.");
        }
        EvidenciaAvanceSitio evidencia = new EvidenciaAvanceSitio();
        evidencia.id = UUID.randomUUID();
        evidencia.avanceSitioId = avanceSitioId;
        evidencia.fase = fase;
        evidencia.claveAlmacen = claveAlmacen.strip();
        evidencia.nombreOriginal = normalizarNombre(nombreOriginal);
        evidencia.tipoMime = tipoMime.strip().toLowerCase();
        evidencia.tamanoBytes = tamanoBytes;
        evidencia.estado = EstadoEvidencia.PENDIENTE;
        evidencia.subidaEn = clock.instant();
        evidencia.setCreatedBy(actor);
        evidencia.setUpdatedBy(actor);
        return evidencia;
    }

    /**
     * Aprueba la evidencia (transicion {@code pendiente -> aprobada}), registrando
     * el actor y el instante UTC de la decision (Req 3.2). Aprobar una evidencia
     * que no esta pendiente se rechaza con {@link TransicionInvalidaException} (409).
     *
     * @param actor identificador de quien aprueba; obligatorio.
     * @param clock reloj UTC para fijar {@code decidida_en}; obligatorio.
     */
    public void aprobar(String actor, Clock clock) {
        transicionar(EstadoEvidencia.APROBADA, null, actor, clock);
    }

    /**
     * Rechaza la evidencia (transicion {@code pendiente -> rechazada}) con un motivo
     * obligatorio, registrando el actor y el instante UTC de la decision (Req 3.2).
     * Rechazar una evidencia que no esta pendiente se rechaza con
     * {@link TransicionInvalidaException} (409).
     *
     * @param motivo motivo del rechazo; obligatorio (1..500).
     * @param actor  identificador de quien rechaza; obligatorio.
     * @param clock  reloj UTC para fijar {@code decidida_en}; obligatorio.
     */
    public void rechazar(String motivo, String actor, Clock clock) {
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("El motivo del rechazo es obligatorio.");
        }
        transicionar(EstadoEvidencia.RECHAZADA, normalizarMotivo(motivo), actor, clock);
    }

    private void transicionar(EstadoEvidencia destino, String motivo, String actor, Clock clock) {
        if (clock == null) {
            throw new ReglaNegocioException("El reloj para fijar la decision es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + destino.valorBd() + "' para la evidencia de avance.");
        }
        this.estado = destino;
        this.motivoRechazo = motivo;
        this.decididaPor = actor;
        this.decididaEn = clock.instant();
        setUpdatedBy(actor);
    }

    /** Indica si la evidencia esta aprobada (respalda el avance de la fase). */
    public boolean estaAprobada() {
        return this.estado == EstadoEvidencia.APROBADA;
    }

    private static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            return "evidencia";
        }
        String limpio = valor.strip();
        return limpio.length() > LONGITUD_MAXIMA_NOMBRE
                ? limpio.substring(0, LONGITUD_MAXIMA_NOMBRE)
                : limpio;
    }

    private static String normalizarMotivo(String valor) {
        String limpio = valor.strip();
        if (limpio.length() > LONGITUD_MAXIMA_MOTIVO) {
            throw new ReglaNegocioException(
                    "El motivo del rechazo no puede exceder " + LONGITUD_MAXIMA_MOTIVO + " caracteres.");
        }
        return limpio;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAvanceSitioId() {
        return avanceSitioId;
    }

    public FaseSitioGenerica getFase() {
        return fase;
    }

    public String getClaveAlmacen() {
        return claveAlmacen;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public long getTamanoBytes() {
        return tamanoBytes;
    }

    public EstadoEvidencia getEstado() {
        return estado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public Instant getSubidaEn() {
        return subidaEn;
    }

    public String getDecididaPor() {
        return decididaPor;
    }

    public Instant getDecididaEn() {
        return decididaEn;
    }
}
