package com.dessti.crm.compras.proveedor.domain;

import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code proveedor} (origen de abastecimiento de Materiales),
 * mapeada sobre la tabla {@code proveedor} de la migracion V28 (Req 29, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y por tanto <em>hereda</em> las columnas {@code tenant_id} (asignada
 * automaticamente desde el {@link com.dessti.crm.platform.tenant.TenantContext}
 * al persistir, nunca desde la peticion, Req 23.4), {@code version} (concurrencia
 * optimista, Req 49) y las marcas de auditoria
 * {@code created_at}/{@code updated_at}/{@code created_by}/{@code updated_by}.
 * Esas columnas <em>no</em> se redeclaran aqui. El filtro global de Hibernate
 * {@code tenantFilter} acota automaticamente las consultas al tenant vigente
 * (Capa 1), complementado por la Row-Level Security de la tabla (Capa 2, V28).</p>
 *
 * <p>El mapeo de columnas (nombres, nulabilidad y tipos) coincide
 * <em>exactamente</em> con V28 para que un arranque con {@code ddl-auto=validate}
 * valide sin conflictos. Es analoga a {@code Cliente} del submodulo comercial-crm
 * (mismo patron de {@code activo} para el borrado logico y de RFC unico por tenant
 * entre activos).</p>
 *
 * <h2>Reglas de dominio (Req 29)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, String, String, String)} valida los datos
 *       obligatorios (nombre 1..200, RFC 12..13 con formato, y al menos un dato de
 *       contacto: email valido o telefono de 10..15 digitos) segun Req 29.1.</li>
 *   <li>{@link #actualizar(String, String, String, String, String)} revalida y
 *       persiste los cambios (Req 29.4).</li>
 *   <li>{@link #desactivar(String)} realiza el borrado logico ({@code activo=false})
 *       conservando el historico (Req 29.5).</li>
 * </ul>
 */
@Entity
@Table(name = "proveedor")
public class Proveedor extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Identificador fiscal (RFC), normalizado a mayusculas. Unico por tenant entre activos. */
    @Column(name = "rfc", nullable = false)
    private String rfc;

    /** Correo electronico; {@code null} si no se proporciono (Req 29.1). */
    @Column(name = "email")
    private String email;

    /** Telefono (10..15 digitos); {@code null} si no se proporciono (Req 29.1). */
    @Column(name = "telefono")
    private String telefono;

    /** Bandera de borrado logico (Req 29.5); {@code true} mientras el Proveedor esta vigente. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Proveedor() {
        // Requerido por JPA.
    }

    /**
     * Crea un Proveedor nuevo y activo validando los datos obligatorios (Req 29.1).
     * El {@code tenant_id} <strong>no</strong> se asigna aqui: lo fija
     * {@link TenantScopedEntity} desde el contexto autenticado al persistir
     * (Req 23.4).
     *
     * @param nombre   razon social o nombre; obligatorio (1..200).
     * @param rfc      identificador fiscal; obligatorio (12..13, formato valido;
     *                 se normaliza a mayusculas).
     * @param email    correo electronico; opcional (valido si se proporciona).
     * @param telefono telefono; opcional (10..15 digitos si se proporciona).
     * @param actor    identificador de quien crea el Proveedor, para las columnas
     *                 de auditoria {@code created_by}/{@code updated_by}.
     * @return el Proveedor listo para persistir.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun dato
     *         obligatorio falta o es invalido, o si no hay al menos un dato de
     *         contacto (Req 29.1, 422).
     */
    public static Proveedor crear(String nombre, String rfc, String email, String telefono, String actor) {
        Proveedor proveedor = new Proveedor();
        proveedor.id = UUID.randomUUID();
        proveedor.nombre = ProveedorValidaciones.normalizarNombre(nombre);
        proveedor.rfc = ProveedorValidaciones.normalizarRfc(rfc);
        proveedor.email = ProveedorValidaciones.normalizarEmail(email);
        proveedor.telefono = ProveedorValidaciones.normalizarTelefono(telefono);
        ProveedorValidaciones.exigirAlMenosUnContacto(proveedor.email, proveedor.telefono);
        proveedor.activo = true;
        proveedor.setCreatedBy(actor);
        proveedor.setUpdatedBy(actor);
        return proveedor;
    }

    /**
     * Actualiza los datos del Proveedor revalidando las reglas obligatorias
     * (Req 29.4, 29.1). No modifica el estado {@code activo} (para eso esta
     * {@link #desactivar(String)}).
     *
     * @param nombre   nuevo nombre; obligatorio (1..200).
     * @param rfc      nuevo RFC; obligatorio (12..13, formato valido).
     * @param email    nuevo email; opcional (valido si se proporciona).
     * @param telefono nuevo telefono; opcional (10..15 digitos si se proporciona).
     * @param actor    identificador de quien actualiza, para {@code updated_by}.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun dato
     *         es invalido o falta al menos un dato de contacto (Req 29.1, 422).
     */
    public void actualizar(String nombre, String rfc, String email, String telefono, String actor) {
        String nuevoNombre = ProveedorValidaciones.normalizarNombre(nombre);
        String nuevoRfc = ProveedorValidaciones.normalizarRfc(rfc);
        String nuevoEmail = ProveedorValidaciones.normalizarEmail(email);
        String nuevoTelefono = ProveedorValidaciones.normalizarTelefono(telefono);
        ProveedorValidaciones.exigirAlMenosUnContacto(nuevoEmail, nuevoTelefono);
        this.nombre = nuevoNombre;
        this.rfc = nuevoRfc;
        this.email = nuevoEmail;
        this.telefono = nuevoTelefono;
        this.setUpdatedBy(actor);
    }

    /**
     * Realiza el borrado logico del Proveedor: marca {@code activo=false}
     * conservando sus datos historicos (Req 29.5). Al quedar inactivo, el
     * Proveedor libera su RFC frente al indice unico parcial
     * {@code uq_proveedor_rfc_activo_por_tenant} (V28), de modo que un nuevo
     * Proveedor activo puede reutilizarlo (Req 29.2).
     *
     * @param actor identificador de quien realiza la baja, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si el Proveedor esta activo (no dado de baja logica, Req 29.5).
     *
     * @return {@code true} si el Proveedor esta activo.
     */
    public boolean estaActivo() {
        return activo;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRfc() {
        return rfc;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public boolean isActivo() {
        return activo;
    }
}
