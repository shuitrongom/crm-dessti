package com.dessti.crm.comercial.producto.domain;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la {@code lista_precios} (conjunto de precios vigentes de los
 * Productos, aplicable por periodo o por segmento de Cliente), mapeada sobre la
 * tabla {@code lista_precios} de la migracion V12 (Req 59, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * (hereda {@code tenant_id}, {@code version} y las marcas de auditoria; no se
 * redeclaran). El mapeo coincide <em>exactamente</em> con V12.</p>
 *
 * <h2>Reglas de dominio (Req 59.3, 59.9)</h2>
 * <ul>
 *   <li>{@link #crear} valida el nombre (1..200), que la vigencia sea coherente
 *       ({@code vigenciaFin >= vigenciaInicio} cuando hay fin) y normaliza el
 *       segmento opcional.</li>
 *   <li>{@link #prioridad} rige la seleccion: a MAYOR valor, MAYOR prioridad de
 *       aplicacion (Req 59.9).</li>
 *   <li>{@link #segmento} {@code null} representa la lista general; un valor
 *       identifica la lista especifica de un segmento de Cliente (Req 59.9).</li>
 *   <li>{@link #estaVigente(LocalDate)} indica si la lista aplica a una fecha
 *       dada (dentro de {@code [vigenciaInicio, vigenciaFin]}, con fin abierto si
 *       es {@code null}).</li>
 * </ul>
 */
@Entity
@Table(name = "lista_precios")
public class ListaPrecios extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Prioridad de aplicacion; a mayor valor, mayor prioridad (Req 59.9). */
    @Column(name = "prioridad", nullable = false)
    private int prioridad;

    /** Segmento de Cliente al que aplica; {@code null} = lista general (Req 59.9). */
    @Column(name = "segmento")
    private String segmento;

    /** Inicio de la vigencia (inclusive, Req 59.3). */
    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    /** Fin de la vigencia (inclusive); {@code null} = vigencia abierta (Req 59.3). */
    @Column(name = "vigencia_fin")
    private LocalDate vigenciaFin;

    /** Bandera de baja logica de la lista; {@code true} mientras esta vigente. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected ListaPrecios() {
        // Requerido por JPA.
    }

    /**
     * Crea una Lista_Precios nueva y activa validando el nombre, la coherencia de
     * la vigencia y normalizando el segmento (Req 59.3, 59.9). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param nombre          nombre de la lista; obligatorio (1..200).
     * @param prioridad       prioridad de aplicacion (mayor = antes, Req 59.9).
     * @param segmento        segmento de Cliente; opcional ({@code null} = general).
     * @param vigenciaInicio  inicio de la vigencia; obligatorio.
     * @param vigenciaFin     fin de la vigencia; opcional (>= inicio si se indica).
     * @param actor           identificador de quien crea, para la auditoria.
     * @return la Lista_Precios lista para persistir.
     * @throws ReglaNegocioException si el nombre es invalido, falta la vigencia
     *         de inicio o la vigencia de fin es anterior al inicio (422).
     */
    public static ListaPrecios crear(String nombre, int prioridad, String segmento,
                                     LocalDate vigenciaInicio, LocalDate vigenciaFin,
                                     String actor) {
        ListaPrecios lista = new ListaPrecios();
        lista.id = UUID.randomUUID();
        lista.nombre = CatalogoValidaciones.normalizarNombre(nombre);
        lista.prioridad = prioridad;
        lista.segmento = CatalogoValidaciones.normalizarSegmentoOpcional(segmento);
        lista.vigenciaInicio = exigirVigenciaInicio(vigenciaInicio);
        lista.vigenciaFin = validarVigenciaFin(lista.vigenciaInicio, vigenciaFin);
        lista.activo = true;
        lista.setCreatedBy(actor);
        lista.setUpdatedBy(actor);
        return lista;
    }

    /**
     * Actualiza los datos de la Lista_Precios revalidando las reglas (Req 59.3,
     * 59.9). No modifica el estado {@code activo}.
     *
     * @param nombre          nuevo nombre; obligatorio (1..200).
     * @param prioridad       nueva prioridad.
     * @param segmento        nuevo segmento; opcional.
     * @param vigenciaInicio  nuevo inicio de vigencia; obligatorio.
     * @param vigenciaFin     nuevo fin de vigencia; opcional (>= inicio si se indica).
     * @param actor           identificador de quien actualiza, para {@code updated_by}.
     * @throws ReglaNegocioException si algun dato es invalido (422).
     */
    public void actualizar(String nombre, int prioridad, String segmento,
                           LocalDate vigenciaInicio, LocalDate vigenciaFin,
                           String actor) {
        String nuevoNombre = CatalogoValidaciones.normalizarNombre(nombre);
        String nuevoSegmento = CatalogoValidaciones.normalizarSegmentoOpcional(segmento);
        LocalDate nuevoInicio = exigirVigenciaInicio(vigenciaInicio);
        LocalDate nuevoFin = validarVigenciaFin(nuevoInicio, vigenciaFin);
        this.nombre = nuevoNombre;
        this.prioridad = prioridad;
        this.segmento = nuevoSegmento;
        this.vigenciaInicio = nuevoInicio;
        this.vigenciaFin = nuevoFin;
        this.setUpdatedBy(actor);
    }

    /**
     * Realiza el borrado logico de la lista ({@code activo=false}). Idempotente.
     *
     * @param actor identificador de quien realiza la baja, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si la lista esta vigente a la fecha indicada: activa y dentro de
     * {@code [vigenciaInicio, vigenciaFin]} (fin abierto si es {@code null}).
     *
     * @param fecha fecha de referencia; obligatoria.
     * @return {@code true} si la lista aplica a esa fecha.
     */
    public boolean estaVigente(LocalDate fecha) {
        if (fecha == null) {
            throw new ReglaNegocioException("La fecha de referencia es obligatoria.");
        }
        if (!activo) {
            return false;
        }
        boolean trasInicio = !fecha.isBefore(vigenciaInicio);
        boolean antesDeFin = vigenciaFin == null || !fecha.isAfter(vigenciaFin);
        return trasInicio && antesDeFin;
    }

    /**
     * Indica si esta lista es especifica de un segmento de Cliente (no general).
     *
     * @return {@code true} si tiene un segmento asociado.
     */
    public boolean tieneSegmento() {
        return segmento != null;
    }

    /**
     * Indica si el segmento de esta lista coincide con el indicado sin distinguir
     * mayusculas (Req 59.9). Una lista general (sin segmento) nunca coincide.
     *
     * @param segmentoCliente segmento del Cliente; puede ser {@code null}.
     * @return {@code true} si ambos segmentos coinciden ignorando mayusculas.
     */
    public boolean coincideSegmento(String segmentoCliente) {
        if (segmento == null || segmentoCliente == null || segmentoCliente.isBlank()) {
            return false;
        }
        return segmento.equalsIgnoreCase(segmentoCliente.strip());
    }

    private static LocalDate exigirVigenciaInicio(LocalDate vigenciaInicio) {
        if (vigenciaInicio == null) {
            throw new ReglaNegocioException("La fecha de inicio de vigencia es obligatoria.");
        }
        return vigenciaInicio;
    }

    private static LocalDate validarVigenciaFin(LocalDate vigenciaInicio, LocalDate vigenciaFin) {
        if (vigenciaFin != null && vigenciaFin.isBefore(vigenciaInicio)) {
            throw new ReglaNegocioException(
                    "La fecha de fin de vigencia no puede ser anterior a la de inicio.");
        }
        return vigenciaFin;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public String getSegmento() {
        return segmento;
    }

    /** Segmento normalizado a minusculas para comparaciones; {@code null} si general. */
    public String getSegmentoNormalizado() {
        return segmento == null ? null : segmento.toLowerCase(Locale.ROOT);
    }

    public LocalDate getVigenciaInicio() {
        return vigenciaInicio;
    }

    public LocalDate getVigenciaFin() {
        return vigenciaFin;
    }

    public boolean isActivo() {
        return activo;
    }
}
