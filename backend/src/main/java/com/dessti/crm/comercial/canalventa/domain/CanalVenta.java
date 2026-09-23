package com.dessti.crm.comercial.canalventa.domain;

import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code canal_venta} (canal comercial por el que se origina o
 * gestiona una operacion, por ejemplo: directo, referido o en linea), mapeada
 * sobre la tabla {@code canal_venta} de la migracion V15 (Req 63, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y por tanto <em>hereda</em> {@code tenant_id} (asignada automaticamente desde
 * el {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca
 * desde la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49)
 * y las marcas de auditoria {@code created_at}/{@code updated_at}/
 * {@code created_by}/{@code updated_by}. Esas columnas <em>no</em> se redeclaran
 * aqui. El mapeo de columnas coincide <em>exactamente</em> con V15.</p>
 *
 * <h2>Reglas de dominio (Req 63.1)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, String)} valida el nombre obligatorio
 *       (1..100) y la descripcion opcional, y deja el canal activo.</li>
 *   <li>{@link #actualizar(String, String, String)} revalida y persiste los
 *       cambios.</li>
 *   <li>{@link #desactivar(String)} realiza el borrado logico
 *       ({@code activo=false}) conservando el historico, lo que preserva la
 *       clasificacion de Oportunidades/Cotizaciones ya asignadas para los
 *       reportes (Req 63.2).</li>
 * </ul>
 */
@Entity
@Table(name = "canal_venta")
public class CanalVenta extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Descripcion del canal; {@code null} si no se proporciono (Req 63.1). */
    @Column(name = "descripcion")
    private String descripcion;

    /** Bandera de borrado logico (Req 63.1); {@code true} mientras esta vigente. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected CanalVenta() {
        // Requerido por JPA.
    }

    /**
     * Crea un Canal_Venta nuevo y activo validando el nombre obligatorio (1..100)
     * y normalizando la descripcion opcional (Req 63.1). El {@code tenant_id}
     * <strong>no</strong> se asigna aqui: lo fija {@link TenantScopedEntity} desde
     * el contexto autenticado al persistir (Req 23.4).
     *
     * @param nombre      nombre del canal; obligatorio (1..100).
     * @param descripcion descripcion; opcional.
     * @param actor       identificador de quien crea, para las columnas de
     *                    auditoria {@code created_by}/{@code updated_by}.
     * @return el Canal_Venta listo para persistir.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si el nombre
     *         falta/es invalido o la descripcion excede el maximo (422).
     */
    public static CanalVenta crear(String nombre, String descripcion, String actor) {
        CanalVenta canal = new CanalVenta();
        canal.id = UUID.randomUUID();
        canal.nombre = CanalVentaValidaciones.normalizarNombre(nombre);
        canal.descripcion = CanalVentaValidaciones.normalizarDescripcion(descripcion);
        canal.activo = true;
        canal.setCreatedBy(actor);
        canal.setUpdatedBy(actor);
        return canal;
    }

    /**
     * Actualiza los datos del Canal_Venta revalidando las reglas (Req 63.1). No
     * modifica el estado {@code activo} (para eso esta {@link #desactivar(String)}).
     *
     * @param nombre      nuevo nombre; obligatorio (1..100).
     * @param descripcion nueva descripcion; opcional.
     * @param actor       identificador de quien actualiza, para {@code updated_by}.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si el nombre
     *         falta/es invalido o la descripcion excede el maximo (422).
     */
    public void actualizar(String nombre, String descripcion, String actor) {
        String nuevoNombre = CanalVentaValidaciones.normalizarNombre(nombre);
        String nuevaDescripcion = CanalVentaValidaciones.normalizarDescripcion(descripcion);
        this.nombre = nuevoNombre;
        this.descripcion = nuevaDescripcion;
        this.setUpdatedBy(actor);
    }

    /**
     * Realiza el borrado logico del Canal_Venta: marca {@code activo=false}
     * conservando sus datos historicos (Req 63.1). Es idempotente.
     *
     * <p>Al quedar inactivo, el canal libera su nombre frente al indice unico
     * parcial {@code uq_canal_venta_nombre_activo_por_tenant} (V15). Las
     * Oportunidades/Cotizaciones que lo referencian conservan su clasificacion
     * historica para los reportes (Req 63.2).</p>
     *
     * @param actor identificador de quien realiza la baja, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si el Canal_Venta esta activo (no dado de baja logica, Req 63.1).
     *
     * @return {@code true} si el canal esta activo.
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

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isActivo() {
        return activo;
    }
}
