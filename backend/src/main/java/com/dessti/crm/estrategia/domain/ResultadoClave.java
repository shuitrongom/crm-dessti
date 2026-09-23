package com.dessti.crm.estrategia.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code resultado_clave}: metrica medible asociada a un
 * {@link ObjetivoEstrategico}, con valor objetivo, valor actual y peso relativo
 * (Req 58.8), mapeada sobre la tabla {@code resultado_clave} de la migracion V38
 * (Req 58, 23). Es una entidad hija del agregado {@link ObjetivoEstrategico}.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (Req 23.4), {@code version} (Req 49) y las marcas de
 * auditoria. El mapeo de columnas coincide <em>exactamente</em> con V38.</p>
 *
 * <h2>Reglas de dominio (Req 58.8)</h2>
 * <ul>
 *   <li>{@link #crear} valida la descripcion, el valor objetivo (estrictamente
 *       positivo), el valor actual (no negativo) y el peso (en (0, 100]); una
 *       entrada invalida se rechaza con
 *       {@link com.dessti.crm.platform.error.ReglaNegocioException} (422).</li>
 *   <li>{@link #actualizarValorActual(BigDecimal, String)} actualiza la medicion
 *       actual; el recalculo del avance del objetivo lo dispara el agregado.</li>
 *   <li>{@link #comoValor()} proyecta la metrica al objeto de valor puro
 *       {@link ResultadoClaveValor} que consume {@link CalculoAvanceObjetivo}.</li>
 * </ul>
 */
@Entity
@Table(name = "resultado_clave")
public class ResultadoClave extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Objetivo al que pertenece el resultado clave (relacion muchos-a-uno). La FK
     * {@code objetivo_estrategico_id} es NOT NULL con ON DELETE CASCADE en V38. Se
     * gestiona desde el agregado {@link ObjetivoEstrategico}.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "objetivo_estrategico_id", nullable = false, updatable = false)
    private ObjetivoEstrategico objetivo;

    /** Descripcion de la metrica (Req 58.8). */
    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    /** Valor objetivo (meta medible); estrictamente positivo, escala 4 (Req 58.8). */
    @Column(name = "valor_objetivo", nullable = false)
    private BigDecimal valorObjetivo;

    /** Valor actual medido; no negativo, escala 4 (Req 58.8). */
    @Column(name = "valor_actual", nullable = false)
    private BigDecimal valorActual;

    /** Peso relativo en la ponderacion; en (0, 100], escala 2 (Req 58.8). */
    @Column(name = "peso", nullable = false)
    private BigDecimal peso;

    protected ResultadoClave() {
        // Requerido por JPA.
    }

    /**
     * Crea un resultado clave validando sus campos (Req 58.8). El {@code tenant_id}
     * lo fija {@link TenantScopedEntity} al persistir (Req 23.4). La asociacion con
     * el {@link ObjetivoEstrategico} la establece el agregado al agregarlo.
     *
     * @param descripcion   descripcion de la metrica; obligatoria (1..300).
     * @param valorObjetivo valor objetivo; estrictamente positivo (Req 58.8).
     * @param valorActual   valor actual; no negativo (Req 58.8).
     * @param peso          peso relativo; en (0, 100] (Req 58.8).
     * @param actor         identificador de quien crea, para {@code created_by}/
     *                      {@code updated_by}.
     * @return el resultado clave listo para agregar al objetivo.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun campo es
     *         invalido (422).
     */
    public static ResultadoClave crear(String descripcion, BigDecimal valorObjetivo,
                                       BigDecimal valorActual, BigDecimal peso, String actor) {
        ResultadoClave rc = new ResultadoClave();
        rc.id = UUID.randomUUID();
        rc.descripcion = EstrategiaValidaciones.normalizarObligatorio(
                descripcion, "descripcion", EstrategiaValidaciones.LONGITUD_DESCRIPCION_RC);
        rc.valorObjetivo = EstrategiaValidaciones.validarValorObjetivo(valorObjetivo);
        rc.valorActual = EstrategiaValidaciones.validarValorActual(valorActual);
        rc.peso = EstrategiaValidaciones.validarPeso(peso);
        rc.setCreatedBy(actor);
        rc.setUpdatedBy(actor);
        return rc;
    }

    /**
     * Actualiza el valor actual medido de la metrica (Req 58.8) y sella el actor.
     * El recalculo del avance del objetivo lo dispara el agregado
     * {@link ObjetivoEstrategico}.
     *
     * @param nuevoValorActual nuevo valor actual; no negativo (Req 58.8).
     * @param actor            identificador de quien modifica, para {@code updated_by}.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si es invalido (422).
     */
    public void actualizarValorActual(BigDecimal nuevoValorActual, String actor) {
        this.valorActual = EstrategiaValidaciones.validarValorActual(nuevoValorActual);
        this.setUpdatedBy(actor);
    }

    /**
     * Vincula este resultado clave a su Objetivo contenedor. Uso interno del
     * agregado {@link ObjetivoEstrategico}.
     *
     * @param objetivo Objetivo contenedor; obligatorio.
     */
    void asignarObjetivo(ObjetivoEstrategico objetivo) {
        this.objetivo = objetivo;
    }

    /**
     * Proyecta la metrica al objeto de valor puro que consume la funcion de calculo
     * del avance ponderado ({@link CalculoAvanceObjetivo}).
     *
     * @return la contribucion del resultado clave como {@link ResultadoClaveValor}.
     */
    public ResultadoClaveValor comoValor() {
        return new ResultadoClaveValor(valorActual, valorObjetivo, peso);
    }

    public UUID getId() {
        return id;
    }

    /**
     * Objetivo contenedor al que pertenece el resultado clave. Permite a la capa de
     * aplicacion recalcular el avance del agregado tras actualizar la metrica
     * (Req 58.8).
     *
     * @return el {@link ObjetivoEstrategico} contenedor.
     */
    public ObjetivoEstrategico getObjetivo() {
        return objetivo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getValorObjetivo() {
        return valorObjetivo;
    }

    public BigDecimal getValorActual() {
        return valorActual;
    }

    public BigDecimal getPeso() {
        return peso;
    }
}
