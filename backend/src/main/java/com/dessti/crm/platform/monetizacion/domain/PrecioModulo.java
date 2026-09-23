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
 * Entidad JPA de {@code precio_modulo}: precio EXPLICITO (de lista) de un modulo
 * del catalogo en una moneda, sin conversion (V22). Tabla de plataforma (sin
 * tenant, sin RLS). Unico por (modulo, moneda). Es el precio base que usa la
 * factura de renta cuando la Empresa no tiene un precio negociado propio.
 */
@Entity
@Table(name = "precio_modulo")
public class PrecioModulo {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

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

    protected PrecioModulo() {
        // Requerido por JPA.
    }

    /**
     * Crea el precio de lista de un modulo en una moneda.
     *
     * @param catalogoModuloId modulo del catalogo; obligatorio.
     * @param monedaCodigo     codigo de moneda ISO 4217 (normalizado a mayusculas).
     * @param precio           precio en [0.00, 999,999,999.99] (escala 2).
     * @param actor            identificador de quien lo define (super_admin).
     * @return el precio listo para persistir.
     */
    public static PrecioModulo crear(UUID catalogoModuloId, String monedaCodigo,
                                     BigDecimal precio, String actor) {
        if (catalogoModuloId == null) {
            throw new com.dessti.crm.platform.error.ReglaNegocioException(
                    "El modulo del catalogo es obligatorio.");
        }
        PrecioModulo p = new PrecioModulo();
        p.id = UUID.randomUUID();
        p.catalogoModuloId = catalogoModuloId;
        p.monedaCodigo = MonetizacionValidaciones.normalizarCodigoMoneda(monedaCodigo);
        p.precio = MonetizacionValidaciones.validarPrecio(precio);
        p.createdBy = actor;
        p.updatedBy = actor;
        return p;
    }

    /** Actualiza el precio (revalida el rango). */
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