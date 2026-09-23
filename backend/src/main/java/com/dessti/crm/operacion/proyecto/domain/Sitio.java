package com.dessti.crm.operacion.proyecto.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code sitio}: una ubicacion fisica concreta que pertenece a un
 * {@link Proyecto} y en la que se ejecutan las 4 fases de instalacion (Req 21.2,
 * 21.3), mapeada sobre la tabla {@code sitio} de la migracion V25 (Req 21, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V25.</p>
 *
 * <p>El Sitio es un <strong>registro operativo</strong>: acumula el avance en las
 * fases Levantamiento_Sitio, Permiso_Instalacion, Orden_Fabricacion y
 * Orden_Trabajo_Instalacion (Req 21.3). Ese avance NO se materializa como columnas
 * de esta entidad: se deriva en tiempo de consulta a partir de los otros modulos a
 * traves del {@code AvanceSitioPort} (ver V25, DECISION 3). Por ello el Sitio solo
 * conserva su identidad, su vinculo al Proyecto y sus datos descriptivos.</p>
 *
 * <h2>Reglas de dominio (Req 21.2)</h2>
 * <ul>
 *   <li>{@link #paraProyecto(UUID, String, String, String)} valida el Proyecto
 *       asociado y el nombre entre 1 y 200 caracteres tras recortar espacios; la
 *       direccion es opcional.</li>
 * </ul>
 */
@Entity
@Table(name = "sitio")
public class Sitio extends TenantScopedEntity {

    /** Longitud maxima del nombre (coincide con VARCHAR(200) de V25). */
    public static final int LONGITUD_MAXIMA_NOMBRE = 200;

    /** Longitud maxima de la direccion (coincide con VARCHAR(500) de V25). */
    public static final int LONGITUD_MAXIMA_DIRECCION = 500;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Proyecto al que pertenece el Sitio (Req 21.2). Inmutable; se fija al crear el
     * Sitio. Referencia con FK a {@code proyecto} (V25), sin cascada (el Sitio es
     * un registro operativo).
     */
    @Column(name = "proyecto_id", nullable = false, updatable = false)
    private UUID proyectoId;

    /** Nombre del Sitio (Req 21.2), 1..200 tras recortar espacios. */
    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Direccion fisica del Sitio; {@code null} si no se proporciono (opcional). */
    @Column(name = "direccion")
    private String direccion;

    protected Sitio() {
        // Requerido por JPA.
    }

    /**
     * Crea un Sitio nuevo vinculado a un Proyecto existente, validando los datos
     * obligatorios (Req 21.2). El {@code tenant_id} <strong>no</strong> se asigna
     * aqui: lo fija {@link TenantScopedEntity} desde el contexto autenticado al
     * persistir (Req 23.4).
     *
     * <p>La existencia del Proyecto en el tenant NO se comprueba aqui: la aplicacion
     * la verifica (404 si el Proyecto no es accesible) antes de invocar esta
     * fabrica, y la FK {@code fk_sitio_proyecto} de V25 la refuerza.</p>
     *
     * @param proyectoId identificador del Proyecto al que se agrega; obligatorio.
     * @param nombre     nombre del Sitio; obligatorio (1..200, Req 21.2).
     * @param direccion  direccion fisica; opcional (hasta 500 caracteres).
     * @param actor      identificador de quien crea, para {@code created_by}/
     *                   {@code updated_by} (Req 21.6).
     * @return el Sitio listo para persistir.
     * @throws ReglaNegocioException si falta el Proyecto o los datos son invalidos (422).
     */
    public static Sitio paraProyecto(UUID proyectoId, String nombre, String direccion, String actor) {
        if (proyectoId == null) {
            throw new ReglaNegocioException(
                    "El Sitio debe asociarse a un Proyecto existente.");
        }
        Sitio sitio = new Sitio();
        sitio.id = UUID.randomUUID();
        sitio.proyectoId = proyectoId;
        sitio.nombre = normalizarNombre(nombre);
        sitio.direccion = normalizarDireccion(direccion);
        sitio.setCreatedBy(actor);
        sitio.setUpdatedBy(actor);
        return sitio;
    }

    /**
     * Valida y normaliza el nombre del Sitio (Req 21.2): obligatorio y entre 1 y
     * 200 caracteres tras recortar espacios, coherente con el CHECK
     * {@code ck_sitio_nombre_longitud} de V25.
     *
     * @param valor nombre a normalizar.
     * @return el nombre recortado.
     * @throws ReglaNegocioException si es nulo/vacio o excede el maximo.
     */
    private static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre del Sitio es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre del Sitio no puede exceder " + LONGITUD_MAXIMA_NOMBRE
                            + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza la direccion opcional del Sitio. Un valor nulo/en blanco
     * se interpreta como ausencia de direccion y devuelve {@code null}.
     *
     * @param valor direccion a normalizar; puede ser {@code null}.
     * @return la direccion recortada, o {@code null} si no se proporciono.
     * @throws ReglaNegocioException si excede el maximo permitido.
     */
    private static String normalizarDireccion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_DIRECCION) {
            throw new ReglaNegocioException(
                    "La direccion del Sitio no puede exceder " + LONGITUD_MAXIMA_DIRECCION
                            + " caracteres.");
        }
        return normalizado;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProyectoId() {
        return proyectoId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDireccion() {
        return direccion;
    }
}
