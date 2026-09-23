package com.dessti.crm.calidad.domain;

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
 * Entidad JPA y raiz del agregado {@code queja_cliente}: una reclamacion del Cliente
 * registrada en el Sistema de Gestion de Calidad, mapeada sobre la tabla
 * {@code queja_cliente} de la migracion V47 (Req 70.1, 70.8, clausula 10.2, Req 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}.
 * El mapeo de columnas coincide <em>exactamente</em> con V47.</p>
 *
 * <h2>Origen social sin ciclo de modulos (Req 70.1, 70.8)</h2>
 * <p>Una Queja_Cliente originada en una Conversacion social se crea pasando
 * {@code origen = social}, el {@code clienteId} ya resuelto por el modulo
 * {@code social} y un {@link #canalSocialId} <em>opcional</em> como referencia debil
 * (sin FK ni dependencia de codigo hacia {@code social}). Asi {@code calidad} no
 * importa internals de {@code social} ni introduce un ciclo.</p>
 *
 * <h2>Vinculo opcional a Accion_Correctiva (Req 70.1, clausula 10.2)</h2>
 * <p>La Queja_Cliente puede ser entrada de una {@link AccionCorrectiva}, pero NO se
 * obliga. {@link #vincularAccionCorrectiva(UUID, String)} fija el
 * {@link #accionCorrectivaId} y transita el estado a
 * {@link EstadoQuejaCliente#VINCULADA}.</p>
 */
@Entity
@Table(name = "queja_cliente")
public class QuejaCliente extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cliente asociado a la queja (Req 70.1); obligatorio. */
    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    /** Origen de la queja (portal/social/correo/telefono/otro) (Req 70.1, 70.8). */
    @Convert(converter = OrigenQuejaConverter.class)
    @Column(name = "origen", nullable = false, length = 12)
    private OrigenQueja origen;

    /**
     * Referencia debil al canal social de origen (Conversacion/Cuenta), sin FK a
     * tablas de {@code social} para no acoplar modulos; {@code null} salvo origen
     * social (Req 70.8).
     */
    @Column(name = "canal_social_id")
    private UUID canalSocialId;

    /** Descripcion de la queja (Req 70.1). */
    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    /** Estado de la queja (registrada/vinculada/atendida) (Req 70.1). */
    @Convert(converter = EstadoQuejaClienteConverter.class)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoQuejaCliente estado;

    /** Accion_Correctiva vinculada; {@code null} si no se vinculo (Req 70.1). */
    @Column(name = "accion_correctiva_id")
    private UUID accionCorrectivaId;

    /** Instante de registro de la queja (UTC). */
    @Column(name = "registrada_en", nullable = false)
    private Instant registradaEn;

    protected QuejaCliente() {
        // Requerido por JPA.
    }

    /**
     * Registra una Queja_Cliente nueva en estado {@link EstadoQuejaCliente#REGISTRADA}
     * (Req 70.1, 70.8). El {@code tenant_id} lo fija {@link TenantScopedEntity} al
     * persistir (Req 23.4). El {@code canalSocialId} es opcional (referencia debil) y
     * solo aporta trazabilidad cuando el origen es social.
     *
     * @param clienteId     Cliente asociado; obligatorio.
     * @param origen        origen de la queja; obligatorio.
     * @param canalSocialId referencia al canal social de origen; opcional.
     * @param descripcion   descripcion de la queja; obligatoria.
     * @param registradaEn  instante de registro (UTC); obligatorio.
     * @param actor         identificador de origen (auditoria).
     * @return la Queja_Cliente lista para persistir, en estado {@code registrada}.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static QuejaCliente registrar(UUID clienteId, OrigenQueja origen, UUID canalSocialId,
                                         String descripcion, Instant registradaEn, String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException("La Queja_Cliente debe asociarse a un Cliente.");
        }
        if (origen == null) {
            throw new ReglaNegocioException("La Queja_Cliente debe indicar su origen.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new ReglaNegocioException("La Queja_Cliente debe indicar una descripcion.");
        }
        if (registradaEn == null) {
            throw new ReglaNegocioException("La Queja_Cliente debe indicar su marca temporal de registro.");
        }

        QuejaCliente queja = new QuejaCliente();
        queja.id = UUID.randomUUID();
        queja.clienteId = clienteId;
        queja.origen = origen;
        queja.canalSocialId = canalSocialId;
        queja.descripcion = descripcion.strip();
        queja.estado = EstadoQuejaCliente.REGISTRADA;
        queja.accionCorrectivaId = null;
        queja.registradaEn = registradaEn;
        queja.setCreatedBy(actor);
        queja.setUpdatedBy(actor);
        return queja;
    }

    /**
     * Vincula -sin obligar- la Queja_Cliente como entrada a una Accion_Correctiva,
     * transitando a {@link EstadoQuejaCliente#VINCULADA} (Req 70.1, clausula 10.2).
     *
     * @param accionCorrectivaId Accion_Correctiva a vincular; obligatorio.
     * @param actor              identificador de quien vincula, para {@code updated_by}.
     * @throws ReglaNegocioException       si {@code accionCorrectivaId} es nulo (422).
     * @throws TransicionInvalidaException si la queja ya esta atendida (409).
     */
    public void vincularAccionCorrectiva(UUID accionCorrectivaId, String actor) {
        if (accionCorrectivaId == null) {
            throw new ReglaNegocioException("El vinculo debe indicar la Accion_Correctiva.");
        }
        if (!this.estado.puedeTransicionarA(EstadoQuejaCliente.VINCULADA)) {
            throw new TransicionInvalidaException(
                    "La Queja_Cliente en estado '" + this.estado.valorBd()
                            + "' no admite vinculacion a una Accion_Correctiva.");
        }
        this.accionCorrectivaId = accionCorrectivaId;
        this.estado = EstadoQuejaCliente.VINCULADA;
        this.setUpdatedBy(actor);
    }

    /**
     * Marca la Queja_Cliente como atendida, transitando a
     * {@link EstadoQuejaCliente#ATENDIDA} (Req 70.1).
     *
     * @param actor identificador de quien atiende, para {@code updated_by}.
     * @throws TransicionInvalidaException si la queja ya esta atendida (409).
     */
    public void atender(String actor) {
        if (!this.estado.puedeTransicionarA(EstadoQuejaCliente.ATENDIDA)) {
            throw new TransicionInvalidaException(
                    "La Queja_Cliente ya esta atendida; no admite una nueva atencion.");
        }
        this.estado = EstadoQuejaCliente.ATENDIDA;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public OrigenQueja getOrigen() {
        return origen;
    }

    public UUID getCanalSocialId() {
        return canalSocialId;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public EstadoQuejaCliente getEstado() {
        return estado;
    }

    public UUID getAccionCorrectivaId() {
        return accionCorrectivaId;
    }

    public Instant getRegistradaEn() {
        return registradaEn;
    }
}
