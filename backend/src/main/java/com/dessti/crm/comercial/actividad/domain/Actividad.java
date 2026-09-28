package com.dessti.crm.comercial.actividad.domain;

import java.time.Instant;
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
 * Entidad JPA de la {@code actividad_comercial} (interaccion o tarea de
 * seguimiento con el Cliente: llamada, correo, reunion, tarea o nota), mapeada
 * sobre la tabla {@code actividad_comercial} de la migracion V79.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y por tanto <em>hereda</em> {@code tenant_id} (asignada automaticamente desde
 * el {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca
 * desde la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49)
 * y las marcas de auditoria {@code created_at}/{@code updated_at}/
 * {@code created_by}/{@code updated_by}. Esas columnas <em>no</em> se redeclaran
 * aqui. El mapeo de columnas coincide <em>exactamente</em> con V79.</p>
 *
 * <h2>Reglas de dominio</h2>
 * <ul>
 *   <li>{@link #crear} valida los datos obligatorios (Cliente, tipo, asunto,
 *       fecha programada) y fija el estado inicial segun el tipo
 *       ({@link TipoActividad#estadoInicial()}): una nota nace
 *       {@link EstadoActividad#COMPLETADA} y el resto {@link EstadoActividad#PENDIENTE}.</li>
 *   <li>{@link #completar(String)} y {@link #cancelar(String)} aplican la maquina
 *       de estados pura ({@link EstadoActividad}); una transicion invalida
 *       —incluida cualquiera desde un estado final— se rechaza con
 *       {@link TransicionInvalidaException} (409). Completar sella
 *       {@code completada_en}.</li>
 *   <li>{@link #reprogramar(Instant, Instant, String)} solo aplica a actividades
 *       no finales y valida la coherencia fecha/vencimiento.</li>
 *   <li>{@link #editarContenido(String, String, String)} actualiza asunto y
 *       descripcion en cualquier estado (correccion de datos), sin alterar el
 *       ciclo de vida.</li>
 *   <li>{@link #asignarResponsable(UUID, String)} registra o limpia al Usuario a
 *       cargo del seguimiento.</li>
 * </ul>
 */
@Entity
@Table(name = "actividad_comercial")
public class Actividad extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cliente al que pertenece la Actividad; obligatorio e inmutable. */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /**
     * Oportunidad del Cliente a la que se vincula la Actividad; {@code null} si la
     * actividad es a nivel Cliente. Inmutable tras la creacion (el vinculo se fija
     * al registrar).
     */
    @Column(name = "oportunidad_id", updatable = false)
    private UUID oportunidadId;

    /** Tipo de interaccion; inmutable tras la creacion. */
    @Convert(converter = TipoActividadConverter.class)
    @Column(name = "tipo", nullable = false, updatable = false)
    private TipoActividad tipo;

    /** Estado del seguimiento; se persiste como etiqueta ASCII. */
    @Convert(converter = EstadoActividadConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoActividad estado;

    @Column(name = "asunto", nullable = false)
    private String asunto;

    /** Detalle libre / nota fechada; {@code null} si no se captura. */
    @Column(name = "descripcion")
    private String descripcion;

    /** Instante planificado de la interaccion o vencimiento de la tarea (UTC). */
    @Column(name = "fecha_programada", nullable = false)
    private Instant fechaProgramada;

    /** Fecha limite opcional (UTC); {@code null} si no aplica. */
    @Column(name = "vencimiento")
    private Instant vencimiento;

    /** Sello de completado (UTC); {@code null} salvo estado completada. */
    @Column(name = "completada_en")
    private Instant completadaEn;

    /** Usuario a cargo del seguimiento; {@code null} si no se asigna. */
    @Column(name = "responsable_usuario_id")
    private UUID responsableUsuarioId;

    protected Actividad() {
        // Requerido por JPA.
    }

    /**
     * Crea una Actividad nueva validando los datos obligatorios y fijando el
     * estado inicial segun el tipo. El {@code tenant_id} <strong>no</strong> se
     * asigna aqui: lo fija {@link TenantScopedEntity} desde el contexto autenticado
     * al persistir (Req 23.4). La existencia del Cliente (activo) y de la
     * Oportunidad en el tenant las verifica la capa de aplicacion antes de invocar
     * este metodo.
     *
     * @param clienteId            Cliente al que pertenece; obligatorio.
     * @param oportunidadId        Oportunidad vinculada; {@code null} si aplica a
     *                             nivel Cliente.
     * @param tipo                 tipo de interaccion; obligatorio.
     * @param asunto               titulo breve; obligatorio (1..200).
     * @param descripcion          detalle libre; opcional.
     * @param fechaProgramada      instante planificado; obligatorio.
     * @param vencimiento          fecha limite; opcional, no anterior a la
     *                             fecha programada.
     * @param responsableUsuarioId Usuario a cargo; opcional.
     * @param actor                identificador de quien crea, para
     *                             {@code created_by}/{@code updated_by}.
     * @return la Actividad lista para persistir.
     * @throws ReglaNegocioException si falta el Cliente o el tipo, o si el
     *         asunto/fechas son invalidos (422).
     */
    public static Actividad crear(UUID clienteId, UUID oportunidadId, TipoActividad tipo,
                                  String asunto, String descripcion, Instant fechaProgramada,
                                  Instant vencimiento, UUID responsableUsuarioId, String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException("La Actividad debe asociarse a un Cliente existente.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de la Actividad es obligatorio.");
        }
        Instant programada = ActividadValidaciones.validarFechaProgramada(fechaProgramada);
        Actividad actividad = new Actividad();
        actividad.id = UUID.randomUUID();
        actividad.clienteId = clienteId;
        actividad.oportunidadId = oportunidadId;
        actividad.tipo = tipo;
        actividad.asunto = ActividadValidaciones.normalizarAsunto(asunto);
        actividad.descripcion = ActividadValidaciones.normalizarDescripcion(descripcion);
        actividad.fechaProgramada = programada;
        actividad.vencimiento = ActividadValidaciones.validarVencimiento(programada, vencimiento);
        actividad.responsableUsuarioId = responsableUsuarioId;
        actividad.estado = tipo.estadoInicial();
        // Una nota nace completada: sella su marca de completado de forma coherente
        // con el CHECK ck_actividad_completada_en de V79.
        actividad.completadaEn = (actividad.estado == EstadoActividad.COMPLETADA) ? programada : null;
        actividad.setCreatedBy(actor);
        actividad.setUpdatedBy(actor);
        return actividad;
    }

    /**
     * Marca la Actividad como {@link EstadoActividad#COMPLETADA} aplicando la
     * maquina de estados y sellando {@code completada_en}. Idempotente si ya esta
     * completada.
     *
     * @param actor identificador de quien completa, para {@code updated_by}.
     * @throws TransicionInvalidaException si la Actividad esta cancelada (409).
     */
    public void completar(String actor) {
        if (this.estado == EstadoActividad.COMPLETADA) {
            return;
        }
        transicionar(EstadoActividad.COMPLETADA);
        this.completadaEn = Instant.now();
        this.setUpdatedBy(actor);
    }

    /**
     * Marca la Actividad como {@link EstadoActividad#CANCELADA} aplicando la
     * maquina de estados. Idempotente si ya esta cancelada.
     *
     * @param actor identificador de quien cancela, para {@code updated_by}.
     * @throws TransicionInvalidaException si la Actividad esta completada (409).
     */
    public void cancelar(String actor) {
        if (this.estado == EstadoActividad.CANCELADA) {
            return;
        }
        transicionar(EstadoActividad.CANCELADA);
        this.completadaEn = null;
        this.setUpdatedBy(actor);
    }

    /**
     * Reprograma la fecha planificada y, opcionalmente, el vencimiento de una
     * Actividad aun no finalizada. No aplica a actividades completadas o
     * canceladas (su seguimiento ya cerro).
     *
     * @param nuevaFecha  nueva fecha programada; obligatoria.
     * @param vencimiento nuevo vencimiento; opcional, no anterior a la fecha.
     * @param actor       identificador de quien reprograma, para {@code updated_by}.
     * @throws ReglaNegocioException si la Actividad ya es final o las fechas son
     *         invalidas (422).
     */
    public void reprogramar(Instant nuevaFecha, Instant vencimiento, String actor) {
        if (this.estado.esFinal()) {
            throw new ReglaNegocioException(
                    "No se puede reprogramar una Actividad " + this.estado.valorBd() + ".");
        }
        Instant programada = ActividadValidaciones.validarFechaProgramada(nuevaFecha);
        this.fechaProgramada = programada;
        this.vencimiento = ActividadValidaciones.validarVencimiento(programada, vencimiento);
        this.setUpdatedBy(actor);
    }

    /**
     * Corrige el asunto y la descripcion de la Actividad en cualquier estado, sin
     * alterar su ciclo de vida (permite enmendar datos capturados). El tipo, el
     * Cliente y la Oportunidad son inmutables.
     *
     * @param asunto      nuevo asunto; obligatorio (1..200).
     * @param descripcion nueva descripcion; opcional.
     * @param actor       identificador de quien edita, para {@code updated_by}.
     * @throws ReglaNegocioException si el asunto/descripcion son invalidos (422).
     */
    public void editarContenido(String asunto, String descripcion, String actor) {
        this.asunto = ActividadValidaciones.normalizarAsunto(asunto);
        this.descripcion = ActividadValidaciones.normalizarDescripcion(descripcion);
        this.setUpdatedBy(actor);
    }

    /**
     * Registra o limpia al Usuario responsable del seguimiento. La existencia del
     * Usuario en el tenant la verifica la capa de aplicacion.
     *
     * @param usuarioId identificador del Usuario responsable; {@code null} para
     *                  desasignar.
     * @param actor     identificador de quien asigna, para {@code updated_by}.
     */
    public void asignarResponsable(UUID usuarioId, String actor) {
        this.responsableUsuarioId = usuarioId;
        this.setUpdatedBy(actor);
    }

    private void transicionar(EstadoActividad destino) {
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + destino.valorBd() + "'.");
        }
        this.estado = destino;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public UUID getOportunidadId() {
        return oportunidadId;
    }

    public TipoActividad getTipo() {
        return tipo;
    }

    public EstadoActividad getEstado() {
        return estado;
    }

    public String getAsunto() {
        return asunto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Instant getFechaProgramada() {
        return fechaProgramada;
    }

    public Instant getVencimiento() {
        return vencimiento;
    }

    public Instant getCompletadaEn() {
        return completadaEn;
    }

    public UUID getResponsableUsuarioId() {
        return responsableUsuarioId;
    }
}
