package com.dessti.crm.rhnomina.empleado.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code empleado} (persona fisica contratada por la Empresa),
 * mapeada sobre la tabla {@code empleado} de la migracion V32 (Req 40, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y por tanto <em>hereda</em> las columnas {@code tenant_id} (asignada
 * automaticamente desde el {@link com.dessti.crm.platform.tenant.TenantContext}
 * al persistir, nunca desde la peticion, Req 23.4), {@code version}
 * (concurrencia optimista, Req 49) y las marcas de auditoria
 * {@code created_at}/{@code updated_at}/{@code created_by}/{@code updated_by}.
 * Esas columnas <em>no</em> se redeclaran aqui. El filtro global de Hibernate
 * {@code tenantFilter} acota automaticamente las consultas al tenant vigente
 * (Capa 1), complementado por la Row-Level Security de la tabla (Capa 2, V32).</p>
 *
 * <p>El mapeo de columnas (nombres, nulabilidad y tipos) coincide
 * <em>exactamente</em> con V32 para que un arranque con {@code ddl-auto=validate}
 * valide sin conflictos.</p>
 *
 * <h2>Reglas de dominio (Req 40)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, String, String, LocalDate, String)} valida
 *       los datos obligatorios (nombre, RFC, CURP, NSS del IMSS y fecha de
 *       ingreso) con {@link ValidacionesEmpleado} (Req 40.1, 40.2).</li>
 *   <li>{@link #desactivar(String)} realiza el borrado logico ({@code activo=false})
 *       conservando el historico de Contrato_Laboral e Incidencia (Req 40.4).</li>
 * </ul>
 */
@Entity
@Table(name = "empleado")
public class Empleado extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** RFC (persona fisica), normalizado a mayusculas. Unico por tenant entre activos. */
    @Column(name = "rfc", nullable = false)
    private String rfc;

    /** CURP, normalizada a mayusculas (Req 40.1). */
    @Column(name = "curp", nullable = false)
    private String curp;

    /** Numero de Seguridad Social del IMSS (11 digitos) (Req 40.1). */
    @Column(name = "nss", nullable = false)
    private String nss;

    /** Fecha de ingreso del Empleado (Req 40.1). */
    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    /** Bandera de borrado logico (Req 40.4); {@code true} mientras el Empleado esta vigente. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Empleado() {
        // Requerido por JPA.
    }

    /**
     * Crea un Empleado nuevo y activo validando los datos obligatorios (Req 40.1,
     * 40.2). El {@code tenant_id} <strong>no</strong> se asigna aqui: lo fija
     * {@link TenantScopedEntity} desde el contexto autenticado al persistir
     * (Req 23.4).
     *
     * @param nombre       nombre completo; obligatorio (1..200).
     * @param rfc          RFC de persona fisica; obligatorio (13, formato valido;
     *                     se normaliza a mayusculas).
     * @param curp         CURP; obligatoria (18, formato valido; se normaliza).
     * @param nss          NSS del IMSS; obligatorio (11 digitos).
     * @param fechaIngreso fecha de ingreso; obligatoria.
     * @param actor        identificador de quien crea el Empleado, para las columnas
     *                     de auditoria {@code created_by}/{@code updated_by}.
     * @return el Empleado listo para persistir.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun dato
     *         obligatorio falta o su formato es invalido (Req 40.2).
     */
    public static Empleado crear(String nombre, String rfc, String curp, String nss,
                                 LocalDate fechaIngreso, String actor) {
        Empleado empleado = new Empleado();
        empleado.id = UUID.randomUUID();
        empleado.nombre = ValidacionesEmpleado.normalizarNombre(nombre);
        empleado.rfc = ValidacionesEmpleado.normalizarRfc(rfc);
        empleado.curp = ValidacionesEmpleado.normalizarCurp(curp);
        empleado.nss = ValidacionesEmpleado.normalizarNss(nss);
        empleado.fechaIngreso = ValidacionesEmpleado.exigirFechaIngreso(fechaIngreso);
        empleado.activo = true;
        empleado.setCreatedBy(actor);
        empleado.setUpdatedBy(actor);
        return empleado;
    }

    /**
     * Realiza el borrado logico del Empleado: marca {@code activo=false}
     * conservando sus datos historicos y los de su Contrato_Laboral e Incidencia
     * (Req 40.4). Es idempotente: si ya estaba inactivo, no cambia el estado
     * (solo actualiza {@code updated_by}).
     *
     * <p>Al quedar inactivo, el Empleado libera su RFC frente al indice unico
     * parcial {@code uq_empleado_rfc_activo_por_tenant} (V32), de modo que un
     * nuevo Empleado activo puede reutilizarlo.</p>
     *
     * @param actor identificador de quien realiza la baja, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si el Empleado esta activo (no dado de baja logica, Req 40.4).
     *
     * @return {@code true} si el Empleado esta activo.
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

    public String getCurp() {
        return curp;
    }

    public String getNss() {
        return nss;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public boolean isActivo() {
        return activo;
    }
}
