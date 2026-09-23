package com.dessti.crm.reportesbi.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code tablero_personalizado}: un tablero analitico
 * definido y guardado por un Usuario dentro de su Empresa, que combina metricas de
 * distintas areas mediante {@link WidgetTablero widgets} (Req 48.3), mapeada sobre la
 * tabla {@code tablero_personalizado} de la migracion V44.
 *
 * <p><strong>Multi-tenant (Req 23, 48.5):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente al persistir, nunca desde la
 * peticion, Req 23.4), {@code version} (Req 49) y las marcas de auditoria. El mapeo de
 * columnas coincide exactamente con V44. Un tablero solo es visible/gestionable dentro
 * de la Empresa que lo creo.</p>
 *
 * <h2>Composicion (Req 48.3)</h2>
 * <p>El tablero es la raiz del agregado y contiene sus widgets como composicion
 * ({@code cascade = ALL}, {@code orphanRemoval = true}): al persistir el tablero se
 * persisten sus widgets, y al quitarlos de la coleccion o eliminar el tablero se
 * eliminan (reforzado por la FK {@code ON DELETE CASCADE} de V44). Las mutaciones de la
 * definicion pasan por los metodos del dominio; el modulo NO modifica dato de otras
 * areas (solo lectura de indicadores, Req 48.2).</p>
 */
@Entity
@Table(name = "tablero_personalizado")
public class TableroPersonalizado extends TenantScopedEntity {

    /** Longitud maxima del nombre, coherente con VARCHAR(200) de V44. */
    private static final int MAX_NOMBRE = 200;

    /** Longitud maxima de la descripcion, coherente con VARCHAR(500) de V44. */
    private static final int MAX_DESCRIPCION = 500;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = MAX_NOMBRE)
    private String nombre;

    @Column(name = "descripcion", length = MAX_DESCRIPCION)
    private String descripcion;

    /** Usuario que creo el tablero, orientativo (V44 DECISION 4); opcional. */
    @Column(name = "propietario_usuario_id")
    private UUID propietarioUsuarioId;

    @OneToMany(mappedBy = "tablero", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<WidgetTablero> widgets = new ArrayList<>();

    protected TableroPersonalizado() {
        // Requerido por JPA.
    }

    /**
     * Crea un tablero personalizado con su nombre y descripcion validados (Req 48.3).
     * El {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param nombre               nombre del tablero; obligatorio (1..200).
     * @param descripcion          descripcion; opcional (<=500).
     * @param propietarioUsuarioId Usuario creador; opcional.
     * @param actor                actor para {@code created_by}/{@code updated_by}.
     * @return el tablero listo para persistir (sin widgets aun).
     * @throws ReglaNegocioException si el nombre es invalido (422).
     */
    public static TableroPersonalizado crear(String nombre, String descripcion,
                                             UUID propietarioUsuarioId, String actor) {
        TableroPersonalizado tablero = new TableroPersonalizado();
        tablero.id = UUID.randomUUID();
        tablero.aplicarNombre(nombre);
        tablero.aplicarDescripcion(descripcion);
        tablero.propietarioUsuarioId = propietarioUsuarioId;
        tablero.setCreatedBy(actor);
        tablero.setUpdatedBy(actor);
        return tablero;
    }

    /**
     * Actualiza el nombre y la descripcion del tablero (Req 48.3).
     *
     * @param nombre      nuevo nombre; obligatorio (1..200).
     * @param descripcion nueva descripcion; opcional (<=500).
     * @param actor       actor para {@code updated_by}.
     * @throws ReglaNegocioException si el nombre es invalido (422).
     */
    public void actualizar(String nombre, String descripcion, String actor) {
        aplicarNombre(nombre);
        aplicarDescripcion(descripcion);
        setUpdatedBy(actor);
    }

    /**
     * Reemplaza por completo el conjunto de widgets del tablero por la definicion
     * indicada (Req 48.3). Es idempotente respecto al resultado: limpia los widgets
     * actuales (orphan removal los elimina) y agrega los nuevos en el orden dado.
     *
     * @param definiciones definiciones de los widgets a establecer; nunca {@code null}.
     * @param actor        actor para las marcas de auditoria de los widgets.
     * @throws ReglaNegocioException si alguna definicion es invalida (422).
     */
    public void reemplazarWidgets(List<DefinicionWidget> definiciones, String actor) {
        if (definiciones == null) {
            throw new ReglaNegocioException("La definicion de widgets es obligatoria.");
        }
        this.widgets.clear();
        for (DefinicionWidget definicion : definiciones) {
            if (definicion == null) {
                throw new ReglaNegocioException("Los widgets del tablero no pueden ser nulos.");
            }
            this.widgets.add(WidgetTablero.crear(this, definicion.area(), definicion.metrica(),
                    definicion.orden(), definicion.configuracionJson(), actor));
        }
        setUpdatedBy(actor);
    }

    private void aplicarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre del tablero es obligatorio.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > MAX_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre del tablero no puede exceder " + MAX_NOMBRE + " caracteres.");
        }
        this.nombre = limpio;
    }

    private void aplicarDescripcion(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            this.descripcion = null;
            return;
        }
        String limpia = descripcion.trim();
        if (limpia.length() > MAX_DESCRIPCION) {
            throw new ReglaNegocioException(
                    "La descripcion del tablero no puede exceder " + MAX_DESCRIPCION + " caracteres.");
        }
        this.descripcion = limpia;
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

    public UUID getPropietarioUsuarioId() {
        return propietarioUsuarioId;
    }

    /**
     * Vista de solo lectura de los widgets del tablero, ordenada por {@code orden}.
     *
     * @return lista inmutable de los widgets.
     */
    public List<WidgetTablero> getWidgets() {
        return Collections.unmodifiableList(widgets);
    }

    /**
     * Definicion inmutable de un widget usada para crear/reemplazar los widgets de un
     * tablero (Req 48.3), sin exponer la entidad JPA a la capa de aplicacion.
     *
     * @param area              etiqueta del area; obligatoria.
     * @param metrica           clave de la metrica; obligatoria.
     * @param orden             orden de presentacion; no negativo.
     * @param configuracionJson parametros de presentacion como JSON; opcional.
     */
    public record DefinicionWidget(String area, String metrica, int orden, String configuracionJson) {
    }
}
