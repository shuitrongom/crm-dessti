package com.dessti.crm.estrategia.domain;

import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code esencia_empresa}: mision, vision y valores
 * de la Empresa (Req 58.1), mapeada sobre la tabla {@code esencia_empresa} de la
 * migracion V38 (Req 58, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V38.</p>
 *
 * <h2>Reglas de dominio (Req 58.1)</h2>
 * <ul>
 *   <li>Existe <strong>una sola</strong> esencia por Empresa (unicidad
 *       {@code (tenant_id)} en V38); la capa de aplicacion la crea o actualiza
 *       (upsert).</li>
 *   <li>Los tres campos (mision, vision, valores) son <strong>opcionales</strong>
 *       (nullables), de modo que la Empresa puede completarlos progresivamente.</li>
 *   <li>{@link #actualizar(String, String, String, String)} reemplaza el contenido
 *       actual y sella el actor de la modificacion; la auditoria la registra la
 *       capa de aplicacion (Req 58.7).</li>
 * </ul>
 */
@Entity
@Table(name = "esencia_empresa")
public class EsenciaEmpresa extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Mision de la Empresa (Req 58.1); opcional (se completa progresivamente). */
    @Column(name = "mision")
    private String mision;

    /** Vision de la Empresa (Req 58.1); opcional. */
    @Column(name = "vision")
    private String vision;

    /** Valores de la Empresa (Req 58.1); opcional. */
    @Column(name = "valores")
    private String valores;

    protected EsenciaEmpresa() {
        // Requerido por JPA.
    }

    /**
     * Crea la esencia de la Empresa con la mision, vision y valores dados (Req 58.1).
     * Cualquiera de los tres puede ser {@code null}. El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param mision  mision; opcional.
     * @param vision  vision; opcional.
     * @param valores valores; opcional.
     * @param actor   identificador de quien registra, para {@code created_by}/
     *                {@code updated_by}.
     * @return la esencia lista para persistir.
     */
    public static EsenciaEmpresa crear(String mision, String vision, String valores, String actor) {
        EsenciaEmpresa esencia = new EsenciaEmpresa();
        esencia.id = UUID.randomUUID();
        esencia.mision = normalizar(mision);
        esencia.vision = normalizar(vision);
        esencia.valores = normalizar(valores);
        esencia.setCreatedBy(actor);
        esencia.setUpdatedBy(actor);
        return esencia;
    }

    /**
     * Reemplaza la mision, vision y valores de la esencia (Req 58.1) y sella el
     * actor de la modificacion. Cualquiera de los tres puede ser {@code null} para
     * limpiar el campo correspondiente.
     *
     * @param mision  nueva mision; opcional.
     * @param vision  nueva vision; opcional.
     * @param valores nuevos valores; opcional.
     * @param actor   identificador de quien modifica, para {@code updated_by}.
     */
    public void actualizar(String mision, String vision, String valores, String actor) {
        this.mision = normalizar(mision);
        this.vision = normalizar(vision);
        this.valores = normalizar(valores);
        this.setUpdatedBy(actor);
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String recortado = valor.strip();
        return recortado.isEmpty() ? null : recortado;
    }

    public UUID getId() {
        return id;
    }

    public String getMision() {
        return mision;
    }

    public String getVision() {
        return vision;
    }

    public String getValores() {
        return valores;
    }
}
