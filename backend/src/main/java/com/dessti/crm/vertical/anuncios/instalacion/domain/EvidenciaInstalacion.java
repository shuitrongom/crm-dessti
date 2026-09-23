package com.dessti.crm.vertical.anuncios.instalacion.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de una evidencia fotografica adjunta a una
 * {@link OrdenTrabajoInstalacion} (Req 19.4), mapeada sobre la tabla
 * {@code evidencia_instalacion} de la migracion V24. Guarda una <em>referencia</em>
 * a la fotografia (URL o clave de objeto en el almacen), no el binario.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado automaticamente al persistir), {@code version}
 * y las marcas de auditoria; no se redeclaran aqui. El
 * {@code orden_trabajo_instalacion_id} referencia a la OTI propietaria dentro del
 * mismo tenant.</p>
 *
 * <p>Sigue el patron de {@code LevantamientoFoto}: es una entidad hija
 * independiente (una fila por evidencia), no una coleccion embebida, lo que
 * mantiene la limpieza relacional y el aislamiento por tenant + RLS de forma
 * uniforme.</p>
 */
@Entity
@Table(name = "evidencia_instalacion")
public class EvidenciaInstalacion extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Orden_Trabajo_Instalacion propietaria (mismo tenant). No modificable tras crear. */
    @Column(name = "orden_trabajo_instalacion_id", nullable = false, updatable = false)
    private UUID ordenTrabajoInstalacionId;

    /** Referencia a la fotografia (URL o clave del objeto en el almacen); obligatoria. */
    @Column(name = "url", nullable = false)
    private String url;

    protected EvidenciaInstalacion() {
        // Requerido por JPA.
    }

    /**
     * Crea una evidencia fotografica asociada a una Orden_Trabajo_Instalacion
     * (Req 19.4), validando la referencia. El {@code orden_trabajo_instalacion_id}
     * se deriva de la OTI indicada y el {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param orden Orden_Trabajo_Instalacion propietaria; obligatoria.
     * @param url   URL o clave de la fotografia; obligatoria (no vacia).
     * @param actor identificador de quien adjunta, para auditoria.
     * @return la evidencia lista para persistir.
     * @throws ReglaNegocioException si la OTI es {@code null} o la referencia esta
     *         vacia (422, Req 19.4).
     */
    public static EvidenciaInstalacion paraOrden(OrdenTrabajoInstalacion orden,
                                                 String url, String actor) {
        if (orden == null) {
            throw new ReglaNegocioException(
                    "La evidencia debe asociarse a una Orden_Trabajo_Instalacion.");
        }
        if (url == null || url.isBlank()) {
            throw new ReglaNegocioException("La referencia de la evidencia no puede estar vacia.");
        }
        EvidenciaInstalacion evidencia = new EvidenciaInstalacion();
        evidencia.id = UUID.randomUUID();
        evidencia.ordenTrabajoInstalacionId = orden.getId();
        evidencia.url = url.strip();
        evidencia.setCreatedBy(actor);
        evidencia.setUpdatedBy(actor);
        return evidencia;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrdenTrabajoInstalacionId() {
        return ordenTrabajoInstalacionId;
    }

    public String getUrl() {
        return url;
    }
}
