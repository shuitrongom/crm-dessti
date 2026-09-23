package com.dessti.crm.vertical.anuncios.levantamiento.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de una fotografia adjunta a un {@link LevantamientoSitio} (Req 16.3),
 * mapeada sobre la tabla {@code levantamiento_foto} de la migracion V19. Guarda una
 * <em>referencia</em> a la fotografia (URL o clave de objeto en el almacen), no el
 * binario.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado automaticamente al persistir), {@code version}
 * y las marcas de auditoria; no se redeclaran aqui. El {@code levantamiento_id}
 * referencia al Levantamiento_Sitio propietario dentro del mismo tenant.</p>
 *
 * <p>Sigue el patron de {@code Contacto} respecto de {@code Cliente}: es una
 * entidad hija independiente (una fila por foto), no una coleccion embebida, lo
 * que mantiene la limpieza relacional y el aislamiento por tenant + RLS de forma
 * uniforme.</p>
 */
@Entity
@Table(name = "levantamiento_foto")
public class LevantamientoFoto extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Levantamiento_Sitio propietario (mismo tenant). No modificable tras crear. */
    @Column(name = "levantamiento_id", nullable = false, updatable = false)
    private UUID levantamientoId;

    /** Referencia a la fotografia (URL o clave del objeto en el almacen); obligatoria. */
    @Column(name = "referencia", nullable = false)
    private String referencia;

    protected LevantamientoFoto() {
        // Requerido por JPA.
    }

    /**
     * Crea una fotografia asociada a un Levantamiento_Sitio (Req 16.3), validando
     * la referencia. El {@code levantamiento_id} se deriva del Levantamiento
     * indicado y el {@code tenant_id} lo fija {@link TenantScopedEntity} al
     * persistir (Req 23.4).
     *
     * @param levantamiento Levantamiento_Sitio propietario; obligatorio.
     * @param referencia    URL o clave de la fotografia; obligatoria (no vacia).
     * @param actor         identificador de quien adjunta, para auditoria.
     * @return la fotografia lista para persistir.
     * @throws ReglaNegocioException si el Levantamiento es {@code null} o la
     *         referencia esta vacia (422, Req 16.3).
     */
    public static LevantamientoFoto paraLevantamiento(LevantamientoSitio levantamiento,
                                                      String referencia, String actor) {
        if (levantamiento == null) {
            throw new ReglaNegocioException("La fotografia debe asociarse a un Levantamiento_Sitio.");
        }
        if (referencia == null || referencia.isBlank()) {
            throw new ReglaNegocioException("La referencia de la fotografia no puede estar vacia.");
        }
        LevantamientoFoto foto = new LevantamientoFoto();
        foto.id = UUID.randomUUID();
        foto.levantamientoId = levantamiento.getId();
        foto.referencia = referencia.strip();
        foto.setCreatedBy(actor);
        foto.setUpdatedBy(actor);
        return foto;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLevantamientoId() {
        return levantamientoId;
    }

    public String getReferencia() {
        return referencia;
    }
}
