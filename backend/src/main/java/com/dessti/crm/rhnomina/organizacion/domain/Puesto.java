package com.dessti.crm.rhnomina.organizacion.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code puesto} de la Empresa dentro del organigrama (Req 61.1),
 * mapeada sobre la tabla {@code puesto} de la migracion V36.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * por tanto hereda {@code tenant_id} (asignado desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. Esas columnas no se redeclaran. El mapeo de columnas
 * coincide <em>exactamente</em> con V36 para validar con {@code ddl-auto=validate}.</p>
 *
 * <h2>Jerarquia (Req 61.1, 61.7)</h2>
 * <p>La jerarquia se modela con la auto-referencia {@link #puestoSuperiorId}: un
 * Puesto sin superior ({@code null}) es una raiz del organigrama. El auto-superior
 * <em>directo</em> se rechaza aqui y ademas por el CHECK {@code ck_puesto_no_auto_superior}
 * de V36. El ciclo <em>indirecto</em> (A-&gt;B-&gt;..-&gt;A) requiere conocer todo
 * el grafo y por eso se valida en la capa de aplicacion con
 * {@link GrafoOrganigrama#introduciriaCiclo}, no en la entidad.</p>
 *
 * <h2>Reglas de dominio (Req 61.1)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, UUID, String)} valida el nombre obligatorio
 *       y que el superior propuesto no sea el propio Puesto.</li>
 *   <li>{@link #cambiarSuperior(UUID, String)} mueve el Puesto en la jerarquia
 *       validando que no sea su propio superior directo (el ciclo indirecto lo
 *       comprueba la aplicacion antes de invocar este metodo).</li>
 *   <li>{@link #desactivar(String)} realiza el borrado logico conservando el
 *       historico.</li>
 * </ul>
 */
@Entity
@Table(name = "puesto")
public class Puesto extends TenantScopedEntity {

    /** Longitud maxima del nombre (coincide con VARCHAR(200) de V36). */
    public static final int LONGITUD_MAXIMA_NOMBRE = 200;

    /** Longitud maxima de la descripcion (coincide con VARCHAR(500) de V36). */
    public static final int LONGITUD_MAXIMA_DESCRIPCION = 500;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Nombre del Puesto; obligatorio (Req 61.1). */
    @Column(name = "nombre", nullable = false, length = LONGITUD_MAXIMA_NOMBRE)
    private String nombre;

    /** Descripcion opcional del Puesto. */
    @Column(name = "descripcion", length = LONGITUD_MAXIMA_DESCRIPCION)
    private String descripcion;

    /** Puesto superior directo en la jerarquia; {@code null} si es raiz (Req 61.1). */
    @Column(name = "puesto_superior_id")
    private UUID puestoSuperiorId;

    /** Bandera de borrado logico; {@code true} mientras el Puesto esta vigente. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Puesto() {
        // Requerido por JPA.
    }

    /**
     * Crea un Puesto nuevo y activo (Req 61.1). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} desde el contexto autenticado al persistir
     * (Req 23.4). La validacion aciclica completa (ciclo indirecto) la realiza la
     * capa de aplicacion con {@link GrafoOrganigrama} antes de invocar esta
     * fabrica; aqui solo se rechaza el auto-superior directo.
     *
     * @param nombre           nombre del Puesto; obligatorio (1..200).
     * @param descripcion      descripcion opcional (&lt;= 500).
     * @param puestoSuperiorId superior directo; {@code null} si es raiz.
     * @param actor            identificador de quien crea el Puesto, para las
     *                         columnas de auditoria.
     * @return el Puesto listo para persistir.
     * @throws ReglaNegocioException si el nombre falta/excede el maximo, la
     *         descripcion excede el maximo o el superior es el propio Puesto (422).
     */
    public static Puesto crear(String nombre, String descripcion, UUID puestoSuperiorId, String actor) {
        Puesto puesto = new Puesto();
        puesto.id = UUID.randomUUID();
        puesto.nombre = normalizarNombre(nombre);
        puesto.descripcion = normalizarDescripcion(descripcion);
        puesto.puestoSuperiorId = normalizarSuperior(puesto.id, puestoSuperiorId);
        puesto.activo = true;
        puesto.setCreatedBy(actor);
        puesto.setUpdatedBy(actor);
        return puesto;
    }

    /**
     * Cambia el Puesto superior directo (mueve el Puesto en la jerarquia, Req 61.1,
     * 61.7). Rechaza el auto-superior directo; la capa de aplicacion garantiza,
     * antes de llamar a este metodo, que el nuevo superior no genera un ciclo
     * indirecto (via {@link GrafoOrganigrama#introduciriaCiclo}).
     *
     * @param nuevoSuperiorId nuevo superior directo; {@code null} para dejarlo como
     *                        raiz del organigrama.
     * @param actor           identificador de quien realiza el cambio, para
     *                        {@code updated_by}.
     * @throws ReglaNegocioException si el nuevo superior es el propio Puesto (422).
     */
    public void cambiarSuperior(UUID nuevoSuperiorId, String actor) {
        this.puestoSuperiorId = normalizarSuperior(this.id, nuevoSuperiorId);
        this.setUpdatedBy(actor);
    }

    /**
     * Realiza el borrado logico del Puesto ({@code activo=false}) conservando su
     * historico. Es idempotente.
     *
     * @param actor identificador de quien realiza la baja, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.setUpdatedBy(actor);
    }

    private static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre del Puesto es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre del Puesto no puede exceder " + LONGITUD_MAXIMA_NOMBRE + " caracteres.");
        }
        return normalizado;
    }

    private static String normalizarDescripcion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_DESCRIPCION) {
            throw new ReglaNegocioException(
                    "La descripcion del Puesto no puede exceder "
                            + LONGITUD_MAXIMA_DESCRIPCION + " caracteres.");
        }
        return normalizado;
    }

    private static UUID normalizarSuperior(UUID propioId, UUID superiorId) {
        if (superiorId != null && superiorId.equals(propioId)) {
            throw new ReglaNegocioException(
                    "Un Puesto no puede ser su propio superior (jerarquia invalida).");
        }
        return superiorId;
    }

    /**
     * Indica si el Puesto esta activo (no dado de baja logica).
     *
     * @return {@code true} si el Puesto esta activo.
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

    public UUID getPuestoSuperiorId() {
        return puestoSuperiorId;
    }

    public boolean isActivo() {
        return activo;
    }
}
