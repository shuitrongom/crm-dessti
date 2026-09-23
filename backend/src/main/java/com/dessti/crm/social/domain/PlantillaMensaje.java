package com.dessti.crm.social.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code plantilla_mensaje}: una Plantilla_Mensaje aprobada por el
 * proveedor del {@link CanalSocial}, requerida para comunicar FUERA de la
 * Ventana_Servicio (Req 64.7), mapeada sobre la tabla {@code plantilla_mensaje} de
 * la migracion V41 (Req 64, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}.
 * El mapeo de columnas coincide <em>exactamente</em> con V41.</p>
 *
 * <p>Solo una plantilla {@link #aprobada aprobada} habilita el envio fuera de la
 * Ventana_Servicio (Req 64.7). La aprobacion la otorga el proveedor (Meta); aqui se
 * refleja como bandera.</p>
 */
@Entity
@Table(name = "plantilla_mensaje")
public class PlantillaMensaje extends TenantScopedEntity {

    /** Longitud maxima del nombre (coincide con VARCHAR(120) de V41). */
    static final int MAX_NOMBRE = 120;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Canal_Social de la plantilla; inmutable (Req 64.7). */
    @Convert(converter = CanalSocialConverter.class)
    @Column(name = "canal", nullable = false, length = 12, updatable = false)
    private CanalSocial canal;

    /** Nombre de la plantilla; unico por canal en el tenant (Req 64.7). */
    @Column(name = "nombre", nullable = false, length = MAX_NOMBRE)
    private String nombre;

    /** Contenido de la plantilla; no vacio. */
    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    /** Aprobada por el proveedor; solo aprobada habilita el envio fuera de ventana (Req 64.7). */
    @Column(name = "aprobada", nullable = false)
    private boolean aprobada;

    protected PlantillaMensaje() {
        // Requerido por JPA.
    }

    /**
     * Crea una Plantilla_Mensaje validando los datos obligatorios (Req 64.7). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param canal     Canal_Social; obligatorio.
     * @param nombre    nombre de la plantilla; obligatorio (1..120).
     * @param contenido contenido; obligatorio (no vacio).
     * @param aprobada  {@code true} si la plantilla ya esta aprobada por el proveedor.
     * @param actor     identificador de quien crea (auditoria).
     * @return la Plantilla_Mensaje lista para persistir.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static PlantillaMensaje crear(CanalSocial canal, String nombre, String contenido,
                                         boolean aprobada, String actor) {
        if (canal == null) {
            throw new ReglaNegocioException("La Plantilla_Mensaje debe indicar el Canal_Social.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("La Plantilla_Mensaje debe indicar el nombre.");
        }
        String nombreLimpio = nombre.strip();
        if (nombreLimpio.length() > MAX_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre de la Plantilla_Mensaje no puede exceder " + MAX_NOMBRE + " caracteres.");
        }
        if (contenido == null || contenido.isBlank()) {
            throw new ReglaNegocioException("La Plantilla_Mensaje debe indicar el contenido.");
        }

        PlantillaMensaje plantilla = new PlantillaMensaje();
        plantilla.id = UUID.randomUUID();
        plantilla.canal = canal;
        plantilla.nombre = nombreLimpio;
        plantilla.contenido = contenido.strip();
        plantilla.aprobada = aprobada;
        plantilla.setCreatedBy(actor);
        plantilla.setUpdatedBy(actor);
        return plantilla;
    }

    /**
     * Marca la plantilla como aprobada por el proveedor (Req 64.7).
     *
     * @param actor identificador de quien actualiza, para {@code updated_by}.
     */
    public void aprobar(String actor) {
        this.aprobada = true;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public CanalSocial getCanal() {
        return canal;
    }

    public String getNombre() {
        return nombre;
    }

    public String getContenido() {
        return contenido;
    }

    public boolean isAprobada() {
        return aprobada;
    }
}
