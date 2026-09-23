package com.dessti.crm.rhnomina.empleado.domain;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la {@code incidencia} de un {@link Empleado} en un
 * Periodo_Nomina (Req 40.3), mapeada sobre la tabla {@code incidencia} de la
 * migracion V32.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity};
 * el mapeo de columnas coincide <em>exactamente</em> con V32. El {@code tenant_id},
 * la {@code version} y las marcas de auditoria se heredan y no se redeclaran.</p>
 *
 * <h2>Periodo_Nomina (Req 40.3)</h2>
 * <p>El vinculo con el Periodo_Nomina se guarda como codigo de periodo en formato
 * {@code 'AAAA-MM'} (por ejemplo {@code '2026-01'}) en {@link #periodoNomina}. La
 * tabla materializada de periodos pertenece al modulo de calculo de nomina
 * (bloque 35); aqui el vinculo se mantiene por codigo.</p>
 *
 * <h2>Reglas de dominio (Req 40.3)</h2>
 * <ul>
 *   <li>{@link #registrar(UUID, String, TipoIncidencia, BigDecimal, String, String)}
 *       exige el Empleado, un periodo con formato valido y el tipo; la cantidad y
 *       la descripcion son opcionales.</li>
 *   <li>Se conserva como historico (Req 40.4): la baja del Empleado no elimina
 *       sus incidencias.</li>
 * </ul>
 */
@Entity
@Table(name = "incidencia")
public class Incidencia extends TenantScopedEntity {

    /** Escala decimal de la cantidad (coincide con NUMERIC(18,2) de V32). */
    public static final int ESCALA_CANTIDAD = 2;

    /** Longitud maxima de la descripcion (coincide con VARCHAR(500) de V32). */
    public static final int LONGITUD_MAXIMA_DESCRIPCION = 500;

    /** Formato del codigo de Periodo_Nomina: {@code AAAA-MM} (por ejemplo 2026-01). */
    private static final Pattern PATRON_PERIODO = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])$");

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Empleado al que pertenece la incidencia (Req 40.3). */
    @Column(name = "empleado_id", nullable = false, updatable = false)
    private UUID empleadoId;

    /** Codigo del Periodo_Nomina en formato {@code AAAA-MM} (Req 40.3). */
    @Column(name = "periodo_nomina", nullable = false, updatable = false, length = 7)
    private String periodoNomina;

    /** Tipo de incidencia; se persiste como etiqueta ASCII (Req 40.3). */
    @Convert(converter = TipoIncidenciaConverter.class)
    @Column(name = "tipo", nullable = false, updatable = false, length = 14)
    private TipoIncidencia tipo;

    /** Cantidad asociada (por ejemplo horas de tiempo extra o dias); opcional. */
    @Column(name = "cantidad", precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal cantidad;

    /** Descripcion/nota opcional de la incidencia. */
    @Column(name = "descripcion", length = LONGITUD_MAXIMA_DESCRIPCION)
    private String descripcion;

    protected Incidencia() {
        // Requerido por JPA.
    }

    /**
     * Registra una Incidencia de un Empleado en un Periodo_Nomina (Req 40.3). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param empleadoId    Empleado al que se vincula; obligatorio.
     * @param periodoNomina codigo del periodo {@code AAAA-MM}; obligatorio y valido.
     * @param tipo          tipo de incidencia; obligatorio.
     * @param cantidad      cantidad (horas/dias); opcional, no negativa si se indica.
     * @param descripcion   nota opcional (<= 500 caracteres).
     * @param actor         identificador de quien registra, para {@code created_by}/{@code updated_by}.
     * @return la incidencia lista para persistir.
     * @throws ReglaNegocioException si falta el Empleado, el periodo es invalido,
     *         falta el tipo, la cantidad es negativa o la descripcion excede el maximo (422).
     */
    public static Incidencia registrar(UUID empleadoId, String periodoNomina, TipoIncidencia tipo,
                                       BigDecimal cantidad, String descripcion, String actor) {
        if (empleadoId == null) {
            throw new ReglaNegocioException("La Incidencia debe referirse a un Empleado.");
        }
        String periodoNormalizado = normalizarPeriodo(periodoNomina);
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de la Incidencia es obligatorio.");
        }
        if (cantidad != null && cantidad.signum() < 0) {
            throw new ReglaNegocioException("La cantidad de la Incidencia no puede ser negativa.");
        }
        String descripcionNormalizada = normalizarDescripcion(descripcion);

        Incidencia incidencia = new Incidencia();
        incidencia.id = UUID.randomUUID();
        incidencia.empleadoId = empleadoId;
        incidencia.periodoNomina = periodoNormalizado;
        incidencia.tipo = tipo;
        incidencia.cantidad = cantidad;
        incidencia.descripcion = descripcionNormalizada;
        incidencia.setCreatedBy(actor);
        incidencia.setUpdatedBy(actor);
        return incidencia;
    }

    private static String normalizarPeriodo(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El Periodo_Nomina de la Incidencia es obligatorio.");
        }
        String normalizado = valor.strip();
        if (!PATRON_PERIODO.matcher(normalizado).matches()) {
            throw new ReglaNegocioException(
                    "El Periodo_Nomina debe tener el formato AAAA-MM (por ejemplo 2026-01).");
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
                    "La descripcion no puede exceder " + LONGITUD_MAXIMA_DESCRIPCION + " caracteres.");
        }
        return normalizado;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpleadoId() {
        return empleadoId;
    }

    public String getPeriodoNomina() {
        return periodoNomina;
    }

    public TipoIncidencia getTipo() {
        return tipo;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
