package com.dessti.crm.platform.monetizacion.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA de {@code empresa_modulo_precio}: precio ESPECIAL negociado
 * (override/descuento) de una Empresa para un modulo en una moneda (V22).
 *
 * <p>Aunque referencia un {@code tenant_id}, es una tabla administrada por el
 * {@code super_admin} a nivel plataforma (sin RLS, ver cabecera de V22). Unico
 * por (empresa, modulo, moneda). La factura de renta prefiere este precio sobre
 * el de lista de {@link PrecioModulo}.</p>
 */
@Entity
@Table(name = "empresa_modulo_precio")
public class EmpresaModuloPrecio {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "catalogo_modulo_id", nullable = false, updatable = false)
    private UUID catalogoModuloId;

    @Column(name = "moneda_codigo", nullable = false, updatable = false, length = 3)
    private String monedaCodigo;

    @Column(name = "precio", nullable = false, precision = 18, scale = 2)
    private BigDecimal precio;

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

    protected EmpresaModuloPrecio() {
        // Requerido por JPA.
    }

    /**
     * Crea el precio especial de una Empresa para un modulo en una moneda.
     *
     * @param tenantId         Empresa titular; obligatorio.
     * @param catalogoModuloId modulo del catalogo; obligatorio.
     * @param monedaCodigo     codigo ISO 4217 (normalizado a mayusculas).
     * @param precio           precio negociado en [0.00, 999,999,999.99].
     * @param actor            identificador de quien lo define (super_admin).
     * @return el precio especial listo para persistir.
     */
    public static EmpresaModuloPrecio crear(UUID tenantId, UUID catalogoModuloId,
                                            String monedaCodigo, BigDecimal precio, String actor) {
        if (tenantId == null) {
            throw new com.dessti.crm.platform.error.ReglaNegocioException(
                    "La Empresa es obligatoria.");
        }
        if (catalogoModuloId == null) {
            throw new com.dessti.crm.platform.error.ReglaNegocioException(
                    "El modulo del catalogo es obligatorio.");
        }
        EmpresaModuloPrecio p = new EmpresaModuloPrecio();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId;
        p.catalogoModuloId = catalogoModuloId;
        p.monedaCodigo = MonetizacionValidaciones.normalizarCodigoMoneda(monedaCodigo);
        p.precio = MonetizacionValidaciones.validarPrecio(precio);
        p.createdBy = actor;
        p.updatedBy = actor;
        return p;
    }

    /** Actualiza el precio negociado (revalida el rango). */
    public void actualizarPrecio(BigDecimal precio, String actor) {
        this.precio = MonetizacionValidaciones.validarPrecio(precio);
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

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCatalogoModuloId() {
        return catalogoModuloId;
    }

    public String getMonedaCodigo() {
        return monedaCodigo;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public long getVersion() {
        return version;
    }
}