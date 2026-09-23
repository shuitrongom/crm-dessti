package com.dessti.crm.estrategia.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code objetivo_estrategico}: objetivo con
 * nombre, responsable, periodo y meta medible, con avance y resultados clave
 * ponderados (Req 58), mapeada sobre la tabla {@code objetivo_estrategico} de la
 * migracion V38 (Req 58, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (Req 23.4), {@code version} (Req 49) y las marcas de
 * auditoria. El mapeo de columnas coincide <em>exactamente</em> con V38.</p>
 *
 * <h2>Reglas de dominio (Req 58)</h2>
 * <ul>
 *   <li>{@link #crear} exige nombre, responsable, periodo y meta (Req 58.2, 58.3),
 *       valida que el fin del periodo no sea anterior al inicio, y fija el avance
 *       inicial en 0 (Req 58.2). Si falta un campo obligatorio se rechaza con
 *       {@link ReglaNegocioException} (422) nombrando el campo (Req 58.3).</li>
 *   <li>{@link #agregarResultadoClave} anade un resultado clave y recalcula el
 *       avance como el porcentaje ponderado de sus resultados clave (Req 58.8;
 *       {@link CalculoAvanceObjetivo}).</li>
 *   <li>{@link #recalcularAvancePorResultados} recomputa el avance a partir de los
 *       resultados clave; es la fuente del avance cuando existen (Req 58.8).</li>
 *   <li>{@link #actualizarAvanceManual} fija el avance directamente, acotado a
 *       [0, 100] (Req 58.9). <strong>Precedencia:</strong> cuando el objetivo tiene
 *       resultados clave, el avance es una agregacion derivada de solo lectura
 *       (Req 58.8) y el ajuste manual no aplica; solo se permite el avance manual
 *       mientras el objetivo no tiene resultados clave (ver {@link #tieneResultadosClave()}).</li>
 * </ul>
 */
@Entity
@Table(name = "objetivo_estrategico")
public class ObjetivoEstrategico extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Nombre del objetivo; obligatorio (Req 58.2). */
    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Responsable del objetivo; obligatorio (Req 58.2). */
    @Column(name = "responsable", nullable = false)
    private String responsable;

    /** Inicio del periodo; obligatorio (Req 58.2). */
    @Column(name = "periodo_inicio", nullable = false)
    private LocalDate periodoInicio;

    /** Fin del periodo; obligatorio, no anterior al inicio (Req 58.2). */
    @Column(name = "periodo_fin", nullable = false)
    private LocalDate periodoFin;

    /** Meta medible; obligatoria (Req 58.2). */
    @Column(name = "meta", nullable = false)
    private String meta;

    /** Avance en [0, 100]; inicial 0 (Req 58.2, 58.9). */
    @Column(name = "avance", nullable = false)
    private BigDecimal avance;

    /**
     * Resultados clave del objetivo (relacion uno-a-muchos, hijos del agregado). La
     * FK {@code resultado_clave.objetivo_estrategico_id} es NOT NULL con ON DELETE
     * CASCADE en V38.
     */
    @OneToMany(mappedBy = "objetivo", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ResultadoClave> resultadosClave = new ArrayList<>();

    protected ObjetivoEstrategico() {
        // Requerido por JPA.
    }

    /**
     * Crea un Objetivo_Estrategico validando los campos obligatorios (Req 58.2,
     * 58.3) y con avance inicial 0 (Req 58.2). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param nombre        nombre; obligatorio (Req 58.3).
     * @param responsable   responsable; obligatorio (Req 58.3).
     * @param periodoInicio inicio del periodo; obligatorio (Req 58.3).
     * @param periodoFin    fin del periodo; obligatorio, no anterior al inicio (Req 58.3).
     * @param meta          meta medible; obligatoria (Req 58.3).
     * @param actor         identificador de quien crea, para {@code created_by}/
     *                      {@code updated_by}.
     * @return el objetivo listo para persistir, con avance inicial 0.
     * @throws ReglaNegocioException si falta un campo obligatorio (422, nombrando el
     *         campo, Req 58.3) o si el fin del periodo es anterior al inicio (422).
     */
    public static ObjetivoEstrategico crear(String nombre, String responsable,
                                            LocalDate periodoInicio, LocalDate periodoFin,
                                            String meta, String actor) {
        ObjetivoEstrategico obj = new ObjetivoEstrategico();
        obj.id = UUID.randomUUID();
        obj.nombre = EstrategiaValidaciones.normalizarObligatorio(
                nombre, "nombre", EstrategiaValidaciones.LONGITUD_NOMBRE);
        obj.responsable = EstrategiaValidaciones.normalizarObligatorio(
                responsable, "responsable", EstrategiaValidaciones.LONGITUD_RESPONSABLE);
        if (periodoInicio == null) {
            throw new ReglaNegocioException("El campo 'periodo_inicio' es obligatorio.");
        }
        if (periodoFin == null) {
            throw new ReglaNegocioException("El campo 'periodo_fin' es obligatorio.");
        }
        if (periodoFin.isBefore(periodoInicio)) {
            throw new ReglaNegocioException(
                    "El fin del periodo no puede ser anterior a su inicio.");
        }
        obj.meta = EstrategiaValidaciones.normalizarObligatorio(
                meta, "meta", EstrategiaValidaciones.LONGITUD_META);
        obj.periodoInicio = periodoInicio;
        obj.periodoFin = periodoFin;
        obj.avance = EstrategiaValidaciones.AVANCE_MINIMO;
        obj.resultadosClave = new ArrayList<>();
        obj.setCreatedBy(actor);
        obj.setUpdatedBy(actor);
        return obj;
    }

    /**
     * Agrega un resultado clave al objetivo y recalcula el avance como el porcentaje
     * ponderado de sus resultados clave (Req 58.8).
     *
     * @param resultado resultado clave a agregar; obligatorio (ya validado en su fabrica).
     * @param actor     identificador de quien realiza el cambio, para {@code updated_by}.
     * @throws ReglaNegocioException si el resultado es nulo (422).
     */
    public void agregarResultadoClave(ResultadoClave resultado, String actor) {
        if (resultado == null) {
            throw new ReglaNegocioException("El resultado clave es obligatorio.");
        }
        resultado.asignarObjetivo(this);
        this.resultadosClave.add(resultado);
        recalcularAvancePorResultados();
        this.setUpdatedBy(actor);
    }

    /**
     * Recalcula el avance del objetivo a partir de sus resultados clave como el
     * porcentaje ponderado de cumplimiento, acotado a [0, 100] (Req 58.8, 58.9;
     * {@link CalculoAvanceObjetivo}). Es la fuente de verdad del avance cuando el
     * objetivo tiene resultados clave (agregacion de solo lectura). Si no tiene
     * ninguno, no modifica el avance (queda el valor gestionado manualmente).
     *
     * @param actor identificador de quien provoca el recalculo, para {@code updated_by}.
     */
    public void recalcularAvancePorResultados(String actor) {
        recalcularAvancePorResultados();
        this.setUpdatedBy(actor);
    }

    private void recalcularAvancePorResultados() {
        if (this.resultadosClave.isEmpty()) {
            return;
        }
        List<ResultadoClaveValor> valores = new ArrayList<>(this.resultadosClave.size());
        for (ResultadoClave rc : this.resultadosClave) {
            valores.add(rc.comoValor());
        }
        this.avance = CalculoAvanceObjetivo.avancePonderado(valores);
    }

    /**
     * Fija el avance del objetivo directamente, acotado a [0, 100] (Req 58.9). Solo
     * aplica mientras el objetivo <strong>no</strong> tiene resultados clave: cuando
     * los tiene, el avance es una agregacion derivada de solo lectura (Req 58.8) y el
     * ajuste manual se rechaza para no contradecir la ponderacion.
     *
     * @param nuevoAvance nuevo avance; se acota a [0, 100] (Req 58.9).
     * @param actor       identificador de quien modifica, para {@code updated_by}.
     * @throws ReglaNegocioException si el objetivo tiene resultados clave (422): en
     *         ese caso el avance se deriva y no se ajusta a mano.
     */
    public void actualizarAvanceManual(BigDecimal nuevoAvance, String actor) {
        if (tieneResultadosClave()) {
            throw new ReglaNegocioException(
                    "El avance del objetivo se deriva de sus resultados clave y no puede "
                            + "ajustarse manualmente.");
        }
        this.avance = EstrategiaValidaciones.acotarAvance(nuevoAvance);
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si el objetivo tiene al menos un resultado clave; en ese caso su avance
     * es una agregacion derivada de solo lectura (Req 58.8).
     *
     * @return {@code true} si el objetivo tiene resultados clave.
     */
    public boolean tieneResultadosClave() {
        return !this.resultadosClave.isEmpty();
    }

    /**
     * Deriva el estado del objetivo (en_riesgo / en_curso / cumplido) a partir de su
     * avance y de la fraccion del periodo transcurrida al dia {@code hoy} (Req 58.10;
     * {@link DerivacionEstadoObjetivo}). Es una derivacion de solo lectura.
     *
     * @param hoy fecha de consulta (normalmente el reloj del sistema).
     * @return el estado derivado del objetivo.
     */
    public EstadoObjetivo estadoDerivado(LocalDate hoy) {
        return DerivacionEstadoObjetivo.derivar(this.avance, this.periodoInicio, this.periodoFin, hoy);
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getResponsable() {
        return responsable;
    }

    public LocalDate getPeriodoInicio() {
        return periodoInicio;
    }

    public LocalDate getPeriodoFin() {
        return periodoFin;
    }

    public String getMeta() {
        return meta;
    }

    public BigDecimal getAvance() {
        return avance;
    }

    /**
     * Vista de solo lectura de los resultados clave del objetivo.
     *
     * @return lista inmutable de resultados clave.
     */
    public List<ResultadoClave> getResultadosClave() {
        return Collections.unmodifiableList(resultadosClave);
    }
}
