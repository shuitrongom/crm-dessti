package com.dessti.crm.rhnomina.organizacion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la {@code evaluacion_desempeno} de un Empleado en un periodo
 * (Req 61.3, 61.8), mapeada sobre la tabla {@code evaluacion_desempeno} de la
 * migracion V36.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity};
 * el mapeo de columnas coincide <em>exactamente</em> con V36. El {@code tenant_id},
 * la {@code version} y las marcas de auditoria se heredan y no se redeclaran.</p>
 *
 * <h2>Escala e historial (Req 61.3, 61.8)</h2>
 * <p>La calificacion se acota a la ESCALA {@value #CALIFICACION_MINIMA}..{@value
 * #CALIFICACION_MAXIMA} (con dos decimales, coherente con {@code NUMERIC(4,2)} de
 * V36) y se validan los comentarios. Cada evaluacion es una fila independiente:
 * el HISTORIAL se conserva sin sobrescribir (puede haber varias evaluaciones por
 * Empleado/periodo).</p>
 *
 * <h2>Reglas de dominio (Req 61.3, 61.8)</h2>
 * <ul>
 *   <li>{@link #registrar(UUID, String, BigDecimal, String, String)} exige el
 *       Empleado, un periodo con formato valido y una calificacion dentro de la
 *       escala; los comentarios son opcionales (&lt;= 1000).</li>
 * </ul>
 */
@Entity
@Table(name = "evaluacion_desempeno")
public class EvaluacionDesempeno extends TenantScopedEntity {

    /** Calificacion minima de la escala de evaluacion (Req 61.8). */
    public static final BigDecimal CALIFICACION_MINIMA = new BigDecimal("1.00");

    /** Calificacion maxima de la escala de evaluacion (Req 61.8). */
    public static final BigDecimal CALIFICACION_MAXIMA = new BigDecimal("5.00");

    /** Escala decimal de la calificacion (coincide con NUMERIC(4,2) de V36). */
    public static final int ESCALA_CALIFICACION = 2;

    /** Longitud maxima de los comentarios (coincide con VARCHAR(1000) de V36). */
    public static final int LONGITUD_MAXIMA_COMENTARIOS = 1000;

    /** Formato del codigo de periodo: {@code AAAA-MM} (por ejemplo 2026-01). */
    private static final Pattern PATRON_PERIODO = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])$");

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Empleado evaluado (FK a empleado(id) de V32, Req 61.6). */
    @Column(name = "empleado_id", nullable = false, updatable = false)
    private UUID empleadoId;

    /** Codigo del periodo en formato {@code AAAA-MM} (Req 61.3). */
    @Column(name = "periodo", nullable = false, updatable = false, length = 7)
    private String periodo;

    /** Calificacion dentro de la escala 1.00..5.00 (Req 61.8). */
    @Column(name = "calificacion", nullable = false, precision = 4, scale = ESCALA_CALIFICACION)
    private BigDecimal calificacion;

    /** Comentarios asociados; opcional (Req 61.8). */
    @Column(name = "comentarios", length = LONGITUD_MAXIMA_COMENTARIOS)
    private String comentarios;

    /** Instante en que se registro la evaluacion (UTC). */
    @Column(name = "evaluada_en", nullable = false, updatable = false)
    private java.time.Instant evaluadaEn;

    protected EvaluacionDesempeno() {
        // Requerido por JPA.
    }

    /**
     * Registra una Evaluacion_Desempeno de un Empleado en un periodo (Req 61.3,
     * 61.8). El {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir
     * (Req 23.4). La calificacion se normaliza a dos decimales y debe estar dentro
     * de la escala {@value #CALIFICACION_MINIMA}..{@value #CALIFICACION_MAXIMA}.
     *
     * @param empleadoId   Empleado evaluado; obligatorio.
     * @param periodo      codigo del periodo {@code AAAA-MM}; obligatorio y valido.
     * @param calificacion calificacion dentro de la escala; obligatoria.
     * @param comentarios  comentarios opcionales (&lt;= 1000).
     * @param actor        identificador de quien registra, para auditoria.
     * @return la evaluacion lista para persistir.
     * @throws ReglaNegocioException si falta el Empleado, el periodo es invalido,
     *         la calificacion falta o esta fuera de escala, o los comentarios
     *         exceden el maximo (422).
     */
    public static EvaluacionDesempeno registrar(UUID empleadoId, String periodo,
                                                BigDecimal calificacion, String comentarios,
                                                String actor) {
        if (empleadoId == null) {
            throw new ReglaNegocioException("La Evaluacion_Desempeno debe referirse a un Empleado.");
        }
        String periodoNormalizado = normalizarPeriodo(periodo);
        BigDecimal calificacionNormalizada = normalizarCalificacion(calificacion);
        String comentariosNormalizados = normalizarComentarios(comentarios);

        EvaluacionDesempeno evaluacion = new EvaluacionDesempeno();
        evaluacion.id = UUID.randomUUID();
        evaluacion.empleadoId = empleadoId;
        evaluacion.periodo = periodoNormalizado;
        evaluacion.calificacion = calificacionNormalizada;
        evaluacion.comentarios = comentariosNormalizados;
        evaluacion.evaluadaEn = java.time.Instant.now();
        evaluacion.setCreatedBy(actor);
        evaluacion.setUpdatedBy(actor);
        return evaluacion;
    }

    private static String normalizarPeriodo(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El periodo de la Evaluacion_Desempeno es obligatorio.");
        }
        String normalizado = valor.strip();
        if (!PATRON_PERIODO.matcher(normalizado).matches()) {
            throw new ReglaNegocioException(
                    "El periodo debe tener el formato AAAA-MM (por ejemplo 2026-01).");
        }
        return normalizado;
    }

    private static BigDecimal normalizarCalificacion(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("La calificacion de la Evaluacion_Desempeno es obligatoria.");
        }
        BigDecimal normalizada = valor.setScale(ESCALA_CALIFICACION, RoundingMode.HALF_UP);
        if (normalizada.compareTo(CALIFICACION_MINIMA) < 0
                || normalizada.compareTo(CALIFICACION_MAXIMA) > 0) {
            throw new ReglaNegocioException(
                    "La calificacion debe estar dentro de la escala "
                            + CALIFICACION_MINIMA + ".." + CALIFICACION_MAXIMA + ".");
        }
        return normalizada;
    }

    private static String normalizarComentarios(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_COMENTARIOS) {
            throw new ReglaNegocioException(
                    "Los comentarios no pueden exceder "
                            + LONGITUD_MAXIMA_COMENTARIOS + " caracteres.");
        }
        return normalizado;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpleadoId() {
        return empleadoId;
    }

    public String getPeriodo() {
        return periodo;
    }

    public BigDecimal getCalificacion() {
        return calificacion;
    }

    public String getComentarios() {
        return comentarios;
    }

    public java.time.Instant getEvaluadaEn() {
        return evaluadaEn;
    }
}
