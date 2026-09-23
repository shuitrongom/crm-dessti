package com.dessti.crm.comercial.oportunidad.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la {@code oportunidad} (prospecto o lead de venta gestionado en
 * el pipeline comercial antes de convertirse en Cotizacion), mapeada sobre la
 * tabla {@code oportunidad} de la migracion V13 (Req 14, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y por tanto <em>hereda</em> {@code tenant_id} (asignada automaticamente desde
 * el {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca
 * desde la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49)
 * y las marcas de auditoria {@code created_at}/{@code updated_at}/
 * {@code created_by}/{@code updated_by}. Esas columnas <em>no</em> se redeclaran
 * aqui. El mapeo de columnas coincide <em>exactamente</em> con V13.</p>
 *
 * <h2>Reglas de dominio (Req 14)</h2>
 * <ul>
 *   <li>{@link #crear} valida los datos obligatorios (cliente existente, titulo
 *       1..200, valor estimado en [0.01, 999,999,999.99]) y fija la etapa inicial
 *       {@link EtapaOportunidad#NUEVO} (Req 14.1).</li>
 *   <li>{@link #asignarResponsable(UUID, String)} registra al Usuario de ventas
 *       responsable (Req 14.2).</li>
 *   <li>{@link #cambiarEtapa(EtapaOportunidad, String)} aplica la maquina de
 *       estados <strong>pura</strong> del pipeline: solo permite las transiciones
 *       del Req 14.3 y rechaza el resto —incluidas las que parten de una etapa
 *       final— con {@link TransicionInvalidaException} (409), conservando la
 *       etapa actual (Req 14.4).</li>
 *   <li>{@link #marcarConvertida(UUID, String)} enlaza la Cotizacion generada al
 *       convertir una Oportunidad {@code ganado} (Req 14.5; la creacion de la
 *       Cotizacion corresponde a la tarea 17.2).</li>
 * </ul>
 */
@Entity
@Table(name = "oportunidad")
public class Oportunidad extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cliente existente al que pertenece la Oportunidad (Req 14.1). */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    /** Valor estimado en la moneda unica del sistema; escala 2 (Req 14.1). */
    @Column(name = "valor_estimado", nullable = false)
    private BigDecimal valorEstimado;

    /** Etapa del pipeline; se persiste como etiqueta ASCII (Req 14.3). */
    @Convert(converter = EtapaOportunidadConverter.class)
    @Column(name = "etapa", nullable = false)
    private EtapaOportunidad etapa;

    /** Usuario de ventas responsable; {@code null} mientras no se asigna (Req 14.2). */
    @Column(name = "responsable_usuario_id")
    private UUID responsableUsuarioId;

    /**
     * Cotizacion generada al convertir la Oportunidad ganada; {@code null} hasta
     * la conversion (Req 14.5). Sin FK en BD: la tabla {@code cotizacion} la crea
     * la tarea 17.2, que reutilizara esta columna sin cambiar la migracion.
     */
    @Column(name = "cotizacion_id")
    private UUID cotizacionId;

    /**
     * Canal de venta al que se clasifica la Oportunidad (Req 63.1); {@code null}
     * mientras no se clasifica. La clasificacion es <strong>opcional</strong>. Se
     * persiste sobre la columna nullable {@code canal_venta_id} (FK a
     * {@code canal_venta}, V15) para su analisis y para la segmentacion de
     * reportes comerciales por canal (Req 63.2). Sin FK gestionada por JPA en la
     * relacion (solo el identificador), coherente con {@code cliente_id}.
     */
    @Column(name = "canal_venta_id")
    private UUID canalVentaId;

    protected Oportunidad() {
        // Requerido por JPA.
    }

    /**
     * Crea una Oportunidad nueva validando los datos obligatorios (Req 14.1) y
     * fijando la etapa inicial {@link EtapaOportunidad#NUEVO}. El {@code tenant_id}
     * <strong>no</strong> se asigna aqui: lo fija {@link TenantScopedEntity} desde
     * el contexto autenticado al persistir (Req 23.4). La existencia del Cliente
     * en el tenant la verifica la capa de aplicacion antes de invocar este metodo.
     *
     * @param clienteId     identificador del Cliente existente; obligatorio.
     * @param titulo        titulo de la Oportunidad; obligatorio (1..200).
     * @param valorEstimado valor estimado; obligatorio, en [0.01, 999,999,999.99].
     * @param actor         identificador de quien crea, para {@code created_by}/
     *                      {@code updated_by}.
     * @return la Oportunidad lista para persistir, en etapa {@code nuevo}.
     * @throws ReglaNegocioException si falta el Cliente, o el titulo/valor son
     *         invalidos (422, Req 14.1).
     */
    public static Oportunidad crear(UUID clienteId, String titulo, BigDecimal valorEstimado, String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException("La Oportunidad debe asociarse a un Cliente existente.");
        }
        Oportunidad oportunidad = new Oportunidad();
        oportunidad.id = UUID.randomUUID();
        oportunidad.clienteId = clienteId;
        oportunidad.titulo = OportunidadValidaciones.normalizarTitulo(titulo);
        oportunidad.valorEstimado = OportunidadValidaciones.validarValorEstimado(valorEstimado);
        oportunidad.etapa = EtapaOportunidad.NUEVO;
        oportunidad.responsableUsuarioId = null;
        oportunidad.cotizacionId = null;
        oportunidad.setCreatedBy(actor);
        oportunidad.setUpdatedBy(actor);
        return oportunidad;
    }

    /**
     * Registra al Usuario de ventas responsable de la Oportunidad (Req 14.2). La
     * existencia del Usuario en el tenant la verifica la capa de aplicacion.
     *
     * @param usuarioId identificador del Usuario responsable; obligatorio.
     * @param actor     identificador de quien realiza la asignacion, para
     *                  {@code updated_by}.
     * @throws ReglaNegocioException si {@code usuarioId} es nulo (422).
     */
    public void asignarResponsable(UUID usuarioId, String actor) {
        if (usuarioId == null) {
            throw new ReglaNegocioException("El Usuario responsable es obligatorio.");
        }
        this.responsableUsuarioId = usuarioId;
        this.setUpdatedBy(actor);
    }

    /**
     * Cambia la etapa de la Oportunidad aplicando la maquina de estados pura del
     * pipeline (Req 14.3, 14.4). Solo permite las transiciones definidas; toda
     * transicion no permitida —incluida cualquiera que parta de una etapa final—
     * se rechaza con {@link TransicionInvalidaException} (409) y la etapa actual
     * se conserva sin cambios (Req 14.4).
     *
     * @param nuevaEtapa etapa destino; obligatoria.
     * @param actor      identificador de quien realiza el cambio, para
     *                   {@code updated_by}.
     * @throws ReglaNegocioException        si {@code nuevaEtapa} es nula (422).
     * @throws TransicionInvalidaException  si la transicion no esta permitida (409).
     */
    public void cambiarEtapa(EtapaOportunidad nuevaEtapa, String actor) {
        if (nuevaEtapa == null) {
            throw new ReglaNegocioException("La etapa destino es obligatoria.");
        }
        if (!this.etapa.puedeTransicionarA(nuevaEtapa)) {
            throw new TransicionInvalidaException(
                    "Transicion de etapa invalida: de '" + this.etapa.valorBd()
                            + "' a '" + nuevaEtapa.valorBd() + "'.");
        }
        this.etapa = nuevaEtapa;
        this.setUpdatedBy(actor);
    }

    /**
     * Enlaza la Cotizacion generada al convertir esta Oportunidad y deja
     * constancia del actor (Req 14.5). La <em>guarda</em> de que la etapa sea
     * {@link EtapaOportunidad#GANADO} y la creacion efectiva de la Cotizacion se
     * gobiernan en la capa de aplicacion (tarea 17.2). Es idempotente para la
     * misma Cotizacion.
     *
     * @param cotizacionId identificador de la Cotizacion creada; obligatorio.
     * @param actor        identificador de quien realiza la conversion, para
     *                     {@code updated_by}.
     * @throws ReglaNegocioException si {@code cotizacionId} es nulo (422).
     */
    public void marcarConvertida(UUID cotizacionId, String actor) {
        if (cotizacionId == null) {
            throw new ReglaNegocioException("El identificador de la Cotizacion es obligatorio.");
        }
        this.cotizacionId = cotizacionId;
        this.setUpdatedBy(actor);
    }

    /**
     * Clasifica (asigna o modifica) el canal de venta de la Oportunidad, o lo
     * limpia (Req 63.1). La clasificacion es <strong>opcional</strong>: un
     * {@code canalVentaId} nulo <em>desclasifica</em> la Oportunidad (deja el
     * canal en {@code null}). La existencia del canal en el tenant la verifica la
     * capa de aplicacion antes de invocar este metodo. La auditoria de la
     * asignacion/modificacion la realiza la capa de aplicacion (Req 63.3).
     *
     * @param canalVentaId identificador del Canal_Venta; {@code null} para limpiar
     *                     la clasificacion.
     * @param actor        identificador de quien realiza la asignacion, para
     *                     {@code updated_by}.
     */
    public void asignarCanalVenta(UUID canalVentaId, String actor) {
        this.canalVentaId = canalVentaId;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si la Oportunidad esta en una etapa que admite conversion a
     * Cotizacion, es decir {@link EtapaOportunidad#GANADO} (Req 14.5, 14.6).
     *
     * @return {@code true} si la etapa actual es {@code ganado}.
     */
    public boolean esConvertible() {
        return this.etapa == EtapaOportunidad.GANADO;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public String getTitulo() {
        return titulo;
    }

    public BigDecimal getValorEstimado() {
        return valorEstimado;
    }

    public EtapaOportunidad getEtapa() {
        return etapa;
    }

    public UUID getResponsableUsuarioId() {
        return responsableUsuarioId;
    }

    public UUID getCotizacionId() {
        return cotizacionId;
    }

    public UUID getCanalVentaId() {
        return canalVentaId;
    }
}
