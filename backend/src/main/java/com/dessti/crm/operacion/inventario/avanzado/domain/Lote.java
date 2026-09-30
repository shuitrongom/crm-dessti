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

    /** Longitud maxima de las notas, coherente con VARCHAR(500) de V86. */
    private static final int NOTAS_MAX = 500;

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

    /** Fecha de fabricacion/recepcion del Lote (Req 60, V86); opcional. */
    @Column(name = "fecha_fabricacion")
    private LocalDate fechaFabricacion;

    /** Notas/observaciones libres del Lote (Req 60, V86); opcional, <= 500. */
    @Column(name = "notas", length = NOTAS_MAX)
    private String notas;

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
    public static Lote crear(UUID materialId, String codigo, LocalDate fechaCaducidad,
                             LocalDate fechaFabricacion, String notas, String actor) {
        if (materialId == null) {
            throw new ReglaNegocioException("El Lote debe referirse a un Material.");
        }
        exigirCoherenciaFechas(fechaFabricacion, fechaCaducidad);
        Lote lote = new Lote();
        lote.id = UUID.randomUUID();
        lote.materialId = materialId;
        lote.codigo = normalizarCodigo(codigo);
        lote.fechaCaducidad = fechaCaducidad;
        lote.fechaFabricacion = fechaFabricacion;
        lote.notas = normalizarNotas(notas);
        lote.setCreatedBy(actor);
        lote.setUpdatedBy(actor);
        return lote;
    }

    /**
     * Actualiza la fecha de caducidad del Lote (Req 60). El {@code codigo} y el
     * {@code material_id} son inmutables (el codigo es unico por Material y sirve de
     * identidad de negocio del Lote); solo la caducidad puede corregirse.
     *
     * @param fechaCaducidad nueva fecha de caducidad; {@code null} = sin caducidad.
     * @param actor          identificador de quien edita, para {@code updated_by}.
     */
    public void actualizarCaducidad(LocalDate fechaCaducidad, String actor) {
        exigirCoherenciaFechas(this.fechaFabricacion, fechaCaducidad);
        this.fechaCaducidad = fechaCaducidad;
        this.setUpdatedBy(actor);
    }

    /**
     * Actualiza los datos EDITABLES del Lote (Req 60, V86): caducidad, fecha de
     * fabricacion y notas. El {@code codigo} y el {@code material_id} SIGUEN siendo
     * inmutables (identidad de negocio). Si ambas fechas se informan, la fabricacion
     * no puede ser posterior a la caducidad (422).
     *
     * @param fechaCaducidad   nueva fecha de caducidad; {@code null} = sin caducidad.
     * @param fechaFabricacion nueva fecha de fabricacion; {@code null} = sin dato.
     * @param notas            nuevas notas; {@code null}/vacio = sin notas.
     * @param actor            identificador de quien edita, para {@code updated_by}.
     */
    public void actualizarDatos(LocalDate fechaCaducidad, LocalDate fechaFabricacion,
                                String notas, String actor) {
        exigirCoherenciaFechas(fechaFabricacion, fechaCaducidad);
        this.fechaCaducidad = fechaCaducidad;
        this.fechaFabricacion = fechaFabricacion;
        this.notas = normalizarNotas(notas);
        this.setUpdatedBy(actor);
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

    /**
     * Normaliza las notas: recorta espacios y convierte una cadena vacia o de solo
     * espacios en {@code null} (sin notas). Rechaza (422) si excede 500 caracteres.
     */
    private static String normalizarNotas(String notas) {
        if (notas == null) {
            return null;
        }
        String limpio = notas.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > NOTAS_MAX) {
            throw new ReglaNegocioException(
                    "Las notas del Lote no pueden exceder " + NOTAS_MAX + " caracteres.");
        }
        return limpio;
    }

    /**
     * Exige que, si ambas fechas se informan, la fabricacion no sea posterior a la
     * caducidad (un Lote no puede caducar antes de fabricarse). Con alguna nula, no
     * hay restriccion.
     */
    private static void exigirCoherenciaFechas(LocalDate fabricacion, LocalDate caducidad) {
        if (fabricacion != null && caducidad != null && fabricacion.isAfter(caducidad)) {
            throw new ReglaNegocioException(
                    "La fecha de fabricacion del Lote no puede ser posterior a su caducidad.");
        }
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

    public LocalDate getFechaFabricacion() {
        return fechaFabricacion;
    }

    public String getNotas() {
        return notas;
    }

    /**
     * Indica si el Lote esta CADUCADO respecto a la fecha dada (Req 60). Un Lote SIN
     * fecha de caducidad ({@code fechaCaducidad == null}) NUNCA caduca. Con fecha, se
     * considera caducado a partir del dia SIGUIENTE al de caducidad: el propio dia de
     * caducidad sigue siendo utilizable (frontera inclusiva), coherente con
     * {@code PermisoInstalacion.estaVigente} donde el dia de vencimiento aun es valido.
     *
     * <p>La comparacion es de dominio puro sobre {@link LocalDate}; la capa de aplicacion
     * calcula {@code hoy} con el {@link java.time.Clock} inyectado (UTC), para ser
     * determinista en pruebas.</p>
     *
     * @param hoy fecha de referencia (normalmente hoy en UTC); obligatoria.
     * @return {@code true} si el Lote tiene caducidad y esta ya vencida antes de {@code hoy}.
     * @throws ReglaNegocioException si {@code hoy} es nulo (422).
     */
    public boolean estaCaducado(LocalDate hoy) {
        if (hoy == null) {
            throw new ReglaNegocioException(
                    "La fecha de referencia para evaluar la caducidad es obligatoria.");
        }
        return fechaCaducidad != null && fechaCaducidad.isBefore(hoy);
    }
}
