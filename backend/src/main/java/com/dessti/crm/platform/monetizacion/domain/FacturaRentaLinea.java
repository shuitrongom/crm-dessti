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
 * Linea de una {@link FacturaRenta}: un modulo habilitado facturado, con su
 * clave, su nombre visible y el precio aplicado (especial o de lista) en la
 * moneda de la factura (V23). Tabla de plataforma (sin RLS).
 */
@Entity
@Table(name = "factura_renta_linea")
public class FacturaRentaLinea {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "factura_renta_id", nullable = false, updatable = false)
    private UUID facturaRentaId;

    @Column(name = "modulo_clave", nullable = false, updatable = false, length = 60)
    private String moduloClave;

    @Column(name = "modulo_nombre", nullable = false, length = 120)
    private String moduloNombre;

    @Column(name = "precio_aplicado", nullable = false, precision = 18, scale = 2)
    private BigDecimal precioAplicado;

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

    protected FacturaRentaLinea() {
        // Requerido por JPA.
    }

    static FacturaRentaLinea crear(UUID facturaRentaId, String moduloClave, String moduloNombre,
                                   BigDecimal precioAplicado, String actor) {
        FacturaRentaLinea l = new FacturaRentaLinea();
        l.id = UUID.randomUUID();
        l.facturaRentaId = facturaRentaId;
        l.moduloClave = moduloClave;
        l.moduloNombre = moduloNombre;
        l.precioAplicado = MonetizacionValidaciones.validarPrecio(precioAplicado);
        l.createdBy = actor;
        l.updatedBy = actor;
        return l;
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

    public UUID getFacturaRentaId() {
        return facturaRentaId;
    }

    public String getModuloClave() {
        return moduloClave;
    }

    public String getModuloNombre() {
        return moduloNombre;
    }

    public BigDecimal getPrecioAplicado() {
        return precioAplicado;
    }

    public long getVersion() {
        return version;
    }
}