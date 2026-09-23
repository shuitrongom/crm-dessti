package com.dessti.crm.operacion.inventario.avanzado.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de un Lote de un Material con caducidad opcional (Req 60), mapeada sobre
 * la tabla {@code lote} de la migracion V26. El codigo del Lote es unico por Material
 * dentro del tenant.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}; el
 * mapeo de columnas coincide <em>exactamente</em> con V26.</p>
 *
 * <p><strong>Alcance:</strong> la tarea 23.1 define la entidad y su alta basica
 * ({@link #crear(UUID, String, LocalDate, String)}); el uso efectivo del Lote en los
 * movimientos por Almacen y en las capas de costo PEPS llega en la tarea 23.2.</p>
 */
@Entity
@Table(name = "lote")
public class Lote extends TenantScopedEntity {

    /** Longitud maxima del codigo (Req 60), coherente con VARCHAR(100) de V26. */
    private static final int CODIGO_MAX = 100;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Material al que pertenece el Lote (Req 60). */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Codigo del Lote, entre 1 y 100 caracteres (Req 60). */
    @Column(name = "codigo", nullable = false, length = CODIGO_MAX)
    private String codigo;

    /** Fecha de caducidad del Lote (Req 60); opcional. */
    @Column(name = "fecha_caducidad")
    private LocalDate fechaCaducidad;

    protected Lote() {
        // Requerido por JPA.
    }

    /**
     * Da de alta un Lote de un Material con su codigo validado y caducidad opcional
     * (Req 60). El {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir
     * (Req 23.4).
     *
     * @param materialId     Material del Lote; obligatorio.
     * @param codigo         codigo del Lote; obligatorio, 1..100 caracteres (se recorta).
     * @param fechaCaducidad fecha de caducidad; opcional ({@code null} = sin caducidad).
     * @param actor          identificador de quien da de alta, para {@code created_by}/{@code updated_by}.
     * @return el Lote listo para persistir.
     * @throws ReglaNegocioException si falta el Material o el codigo es invalido (422).
     */
    public static Lote crear(UUID materialId, String codigo, LocalDate fechaCaducidad, String actor) {
        if (materialId == null) {
            throw new ReglaNegocioException("El Lote debe referirse a un Material.");
        }
        Lote lote = new Lote();
        lote.id = UUID.randomUUID();
        lote.materialId = materialId;
        lote.codigo = normalizarCodigo(codigo);
        lote.fechaCaducidad = fechaCaducidad;
        lote.setCreatedBy(actor);
        lote.setUpdatedBy(actor);
        return lote;
    }

    private static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaNegocioException("El codigo del Lote es obligatorio.");
        }
        String limpio = codigo.trim();
        if (limpio.length() > CODIGO_MAX) {
            throw new ReglaNegocioException(
                    "El codigo del Lote no puede exceder " + CODIGO_MAX + " caracteres.");
        }
        return limpio;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public String getCodigo() {
        return codigo;
    }

    public LocalDate getFechaCaducidad() {
        return fechaCaducidad;
    }
}
