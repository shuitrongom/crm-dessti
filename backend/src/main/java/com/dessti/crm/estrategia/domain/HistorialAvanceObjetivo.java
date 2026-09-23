package com.dessti.crm.estrategia.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code historial_avance_objetivo}: registro
 * <strong>inmutable</strong> (append-only) de cada avance registrado para un
 * {@link ObjetivoEstrategico} (Req 58.4), mapeada sobre la tabla
 * {@code historial_avance_objetivo} de la migracion V38 (Req 58, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (Req 23.4), {@code version} (Req 49) y las marcas de
 * auditoria. El mapeo de columnas coincide <em>exactamente</em> con V38.</p>
 *
 * <p><strong>Historial append-only (Req 58.4):</strong> cada actualizacion del
 * avance â€”manual o derivada del recalculo por resultados claveâ€” inserta una nueva
 * fila; nunca se modifican ni eliminan filas historicas. El {@code objetivo_id} se
 * guarda por identificador (no por asociacion JPA) porque el historial solo se
 * agrega, no navega el agregado.</p>
 */
@Entity
@Table(name = "historial_avance_objetivo")
public class HistorialAvanceObjetivo extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Objetivo al que corresponde el registro de avance (FK en V38, ON DELETE CASCADE). */
    @Column(name = "objetivo_estrategico_id", nullable = false, updatable = false)
    private UUID objetivoEstrategicoId;

    /** Avance registrado en [0, 100] (Req 58.4, 58.9). */
    @Column(name = "avance", nullable = false, updatable = false)
    private BigDecimal avance;

    /** Instante UTC del registro (Req 58.4). */
    @Column(name = "registrado_en", nullable = false, updatable = false)
    private Instant registradoEn;

    /** Actor que provoco el registro; opcional. */
    @Column(name = "actor", updatable = false)
    private String actor;

    protected HistorialAvanceObjetivo() {
        // Requerido por JPA.
    }

    /**
     * Crea un registro de historial de avance (Req 58.4). El {@code tenant_id} lo
     * fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param objetivoEstrategicoId identificador del Objetivo; obligatorio.
     * @param avance                avance registrado en [0, 100]; obligatorio.
     * @param registradoEn          instante UTC del registro; obligatorio.
     * @param actor                 identificador de quien provoco el registro; opcional.
     * @return el registro de historial listo para persistir.
     */
    public static HistorialAvanceObjetivo registrar(UUID objetivoEstrategicoId, BigDecimal avance,
                                                    Instant registradoEn, String actor) {
        HistorialAvanceObjetivo historial = new HistorialAvanceObjetivo();
        historial.id = UUID.randomUUID();
        historial.objetivoEstrategicoId = objetivoEstrategicoId;
        historial.avance = EstrategiaValidaciones.acotarAvance(avance);
        historial.registradoEn = registradoEn;
        historial.actor = actor;
        historial.setCreatedBy(actor);
        historial.setUpdatedBy(actor);
        return historial;
    }

    public UUID getId() {
        return id;
    }

    public UUID getObjetivoEstrategicoId() {
        return objetivoEstrategicoId;
    }

    public BigDecimal getAvance() {
        return avance;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }

    public String getActor() {
        return actor;
    }
}
