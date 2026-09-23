package com.dessti.crm.operacion.inventario.avanzado.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code almacen}: un Almacen fisico o logico del
 * tenant (sucursal o bodega) del inventario avanzado del Req 60, mapeada sobre la
 * tabla {@code almacen} de la migracion V26.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde la
 * peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las marcas
 * de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V26.</p>
 *
 * <h2>Reglas de dominio (Req 60)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, String)} da de alta un Almacen con su nombre
 *       (1..200) y tipo ({@code sucursal}/{@code bodega}) validados; queda activo.</li>
 *   <li>{@link #actualizar(String, String, String)} renombra y/o reclasifica el
 *       Almacen (edicion), revalidando nombre y tipo.</li>
 *   <li>{@link #desactivar(String)} realiza la baja logica del Almacen.</li>
 * </ul>
 */
@Entity
@Table(name = "almacen")
public class Almacen extends TenantScopedEntity {

    /** Longitud maxima del nombre (Req 60), coherente con VARCHAR(200) de V26. */
    private static final int NOMBRE_MAX = 200;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Nombre del Almacen, entre 1 y 200 caracteres (Req 60). */
    @Column(name = "nombre", nullable = false, length = NOMBRE_MAX)
    private String nombre;

    /** Tipo del Almacen ({@code sucursal}/{@code bodega}); se persiste como etiqueta ASCII. */
    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    /** Indicador de baja logica; {@code true} mientras el Almacen esta activo. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Almacen() {
        // Requerido por JPA.
    }

    /**
     * Da de alta un Almacen con su nombre y tipo validados (Req 60). El {@code tenant_id}
     * lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param nombre nombre del Almacen; obligatorio, 1..200 caracteres (se recorta).
     * @param tipo   tipo del Almacen ({@code sucursal} o {@code bodega}); obligatorio.
     * @param actor  identificador de quien da de alta, para {@code created_by}/{@code updated_by}.
     * @return el Almacen listo para persistir, activo.
     * @throws ReglaNegocioException si el nombre o el tipo son invalidos (422).
     */
    public static Almacen crear(String nombre, String tipo, String actor) {
        Almacen almacen = new Almacen();
        almacen.id = UUID.randomUUID();
        almacen.nombre = normalizarNombre(nombre);
        almacen.tipo = normalizarTipo(tipo);
        almacen.activo = true;
        almacen.setCreatedBy(actor);
        almacen.setUpdatedBy(actor);
        return almacen;
    }

    /**
     * Renombra y/o reclasifica el Almacen (edicion, Req 60), revalidando nombre y tipo.
     *
     * @param nombre nuevo nombre; obligatorio, 1..200 caracteres (se recorta).
     * @param tipo   nuevo tipo ({@code sucursal} o {@code bodega}); obligatorio.
     * @param actor  identificador de quien edita, para {@code updated_by}.
     * @throws ReglaNegocioException si el nombre o el tipo son invalidos (422).
     */
    public void actualizar(String nombre, String tipo, String actor) {
        this.nombre = normalizarNombre(nombre);
        this.tipo = normalizarTipo(tipo);
        this.setUpdatedBy(actor);
    }

    /**
     * Da de baja logica el Almacen (Req 60). Idempotente: desactivar un Almacen ya
     * inactivo no tiene efecto adicional.
     *
     * @param actor identificador de quien realiza la baja, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.setUpdatedBy(actor);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private static String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre del Almacen es obligatorio.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > NOMBRE_MAX) {
            throw new ReglaNegocioException(
                    "El nombre del Almacen no puede exceder " + NOMBRE_MAX + " caracteres.");
        }
        return limpio;
    }

    private static String normalizarTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new ReglaNegocioException("El tipo del Almacen es obligatorio.");
        }
        String limpio = tipo.trim().toLowerCase();
        if (!limpio.equals("sucursal") && !limpio.equals("bodega")) {
            throw new ReglaNegocioException(
                    "El tipo del Almacen debe ser 'sucursal' o 'bodega'.");
        }
        return limpio;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public boolean isActivo() {
        return activo;
    }
}
