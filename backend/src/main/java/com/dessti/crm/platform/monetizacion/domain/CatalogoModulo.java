package com.dessti.crm.platform.monetizacion.domain;

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
 * Entidad JPA de {@code catalogo_modulo}: catalogo maestro de modulos
 * facturables, mapeada sobre la tabla {@code catalogo_modulo} de la migracion
 * V22. Tabla de <strong>plataforma</strong> (sin tenant, sin RLS), administrada
 * por el {@code super_admin}.
 *
 * <p>La {@code clave} es la clave canonica del modulo (minusculas), la MISMA que
 * aparece en {@code plan.modulos_habilitados} / {@code suscripcion.modulos_habilitados},
 * de modo que la factura de renta pueda emparejar los modulos habilitados de una
 * Empresa con su precio. {@code activo} permite retirar un modulo del catalogo
 * sin borrar su historial de precios.</p>
 */
@Entity
@Table(name = "catalogo_modulo")
public class CatalogoModulo {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clave", nullable = false, updatable = false, length = 60)
    private String clave;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

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

    protected CatalogoModulo() {
        // Requerido por JPA.
    }

    /**
     * Crea un modulo del catalogo, activo, con clave canonica normalizada.
     *
     * @param clave       clave canonica (minusculas); obligatoria y unica.
     * @param nombre      nombre visible; obligatorio.
     * @param descripcion descripcion; opcional.
     * @param actor       identificador de quien lo crea (super_admin).
     * @return el modulo listo para persistir.
     */
    public static CatalogoModulo crear(String clave, String nombre, String descripcion, String actor) {
        CatalogoModulo modulo = new CatalogoModulo();
        modulo.id = UUID.randomUUID();
        modulo.clave = MonetizacionValidaciones.normalizarClaveModulo(clave);
        modulo.nombre = MonetizacionValidaciones.normalizarNombre(nombre, 120, "nombre del modulo");
        modulo.descripcion = MonetizacionValidaciones.normalizarDescripcion(descripcion);
        modulo.activo = true;
        modulo.createdBy = actor;
        modulo.updatedBy = actor;
        return modulo;
    }

    /** Actualiza el nombre y la descripcion del modulo (la clave es inmutable). */
    public void actualizar(String nombre, String descripcion, String actor) {
        this.nombre = MonetizacionValidaciones.normalizarNombre(nombre, 120, "nombre del modulo");
        this.descripcion = MonetizacionValidaciones.normalizarDescripcion(descripcion);
        this.updatedBy = actor;
    }

    /** Desactiva el modulo del catalogo (baja logica). */
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

    public UUID getId() {
        return id;
    }

    public String getClave() {
        return clave;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isActivo() {
        return activo;
    }

    public long getVersion() {
        return version;
    }
}