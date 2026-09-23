package com.dessti.crm.platform.monetizacion.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA de {@code moneda}: catalogo de monedas soportadas (ISO 4217),
 * mapeada sobre la tabla {@code moneda} de la migracion V22. Tabla de
 * <strong>plataforma</strong> (sin tenant, sin RLS), administrada por el
 * {@code super_admin}.
 *
 * <p>La clave primaria es el {@code codigo} de 3 letras en mayusculas (PK
 * natural). {@code activo} permite retirar una moneda sin borrar su historial.</p>
 */
@Entity
@Table(name = "moneda")
public class Moneda {

    @Id
    @Column(name = "codigo", nullable = false, updatable = false, length = 3)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private boolean activo;

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

    protected Moneda() {
        // Requerido por JPA.
    }

    /**
     * Crea una moneda activa con codigo ISO 4217 (normalizado a mayusculas) y
     * nombre.
     *
     * @param codigo codigo ISO 4217 (3 letras); obligatorio.
     * @param nombre nombre de la moneda; obligatorio.
     * @param actor  identificador de quien la crea (super_admin).
     * @return la moneda lista para persistir.
     */
    public static Moneda crear(String codigo, String nombre, String actor) {
        Moneda moneda = new Moneda();
        moneda.codigo = MonetizacionValidaciones.normalizarCodigoMoneda(codigo);
        moneda.nombre = MonetizacionValidaciones.normalizarNombre(nombre, 60, "nombre de la moneda");
        moneda.activo = true;
        moneda.createdBy = actor;
        moneda.updatedBy = actor;
        return moneda;
    }

    /** Activa la moneda. */
    public void activar(String actor) {
        this.activo = true;
        this.updatedBy = actor;
    }

    /** Desactiva la moneda (deja de ofrecerse para nuevos precios). */
    public void desactivar(String actor) {
        this.activo = false;
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

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActivo() {
        return activo;
    }

    public long getVersion() {
        return version;
    }
}