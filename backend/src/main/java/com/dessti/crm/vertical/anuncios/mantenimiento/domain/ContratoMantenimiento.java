package com.dessti.crm.vertical.anuncios.mantenimiento.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code contrato_mantenimiento}: un Contrato de
 * Mantenimiento con SLA asociado a un Cliente, mapeado sobre la tabla
 * {@code contrato_mantenimiento} de la migracion V27 (Req 20.1, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V27.</p>
 *
 * <h2>Reglas de dominio (Req 20.1)</h2>
 * <ul>
 *   <li>{@link #crear(UUID, TipoContratoMantenimiento, int, int, String)} registra
 *       el contrato vinculado a un Cliente con sus datos obligatorios (tipo
 *       {@code preventivo}/{@code correctivo} y los tiempos del SLA en horas),
 *       validando que el Cliente y el tipo esten presentes y que ambos tiempos del
 *       SLA sean estrictamente positivos. La <em>existencia</em> del Cliente en el
 *       tenant la garantiza la FK de V27 (la capa de aplicacion no dispone aun de
 *       un puerto de lectura de Cliente en este submodulo; documentado como en el
 *       submodulo proyecto).</li>
 *   <li>{@link #desactivar(String)} da de baja logica el contrato sin destruir el
 *       historico de Tickets_Servicio.</li>
 * </ul>
 */
@Entity
@Table(name = "contrato_mantenimiento")
public class ContratoMantenimiento extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cliente al que pertenece el contrato (Req 20.1). Inmutable. */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /** Tipo de contrato (Req 20.1): {@code preventivo} o {@code correctivo}. */
    @Convert(converter = TipoContratoMantenimientoConverter.class)
    @Column(name = "tipo", nullable = false)
    private TipoContratoMantenimiento tipo;

    /** Tiempo de respuesta del SLA en horas (Req 20.1); estrictamente positivo. */
    @Column(name = "sla_respuesta_horas", nullable = false)
    private int slaRespuestaHoras;

    /** Tiempo de resolucion del SLA en horas (Req 20.1); estrictamente positivo. */
    @Column(name = "sla_resolucion_horas", nullable = false)
    private int slaResolucionHoras;

    /** Baja logica del contrato (no destruye el historico). */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected ContratoMantenimiento() {
        // Requerido por JPA.
    }

    /**
     * Registra un Contrato_Mantenimiento asociado a un Cliente con sus datos
     * obligatorios (Req 20.1). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4); el contrato nace activo.
     *
     * @param clienteId          Cliente al que se asocia el contrato; obligatorio.
     * @param tipo               tipo del contrato ({@code preventivo}/
     *                           {@code correctivo}); obligatorio.
     * @param slaRespuestaHoras  tiempo de respuesta del SLA en horas; debe ser > 0.
     * @param slaResolucionHoras tiempo de resolucion del SLA en horas; debe ser > 0.
     * @param actor              identificador de quien registra, para
     *                           {@code created_by}/{@code updated_by}.
     * @return el Contrato_Mantenimiento listo para persistir (activo).
     * @throws ReglaNegocioException si falta el Cliente o el tipo, o si algun
     *         tiempo del SLA no es estrictamente positivo (422).
     */
    public static ContratoMantenimiento crear(UUID clienteId, TipoContratoMantenimiento tipo,
                                              int slaRespuestaHoras, int slaResolucionHoras,
                                              String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException(
                    "El Contrato_Mantenimiento debe asociarse a un Cliente existente.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException(
                    "El tipo del Contrato_Mantenimiento es obligatorio.");
        }
        if (slaRespuestaHoras <= 0) {
            throw new ReglaNegocioException(
                    "El tiempo de respuesta del SLA debe ser mayor que cero.");
        }
        if (slaResolucionHoras <= 0) {
            throw new ReglaNegocioException(
                    "El tiempo de resolucion del SLA debe ser mayor que cero.");
        }
        ContratoMantenimiento contrato = new ContratoMantenimiento();
        contrato.id = UUID.randomUUID();
        contrato.clienteId = clienteId;
        contrato.tipo = tipo;
        contrato.slaRespuestaHoras = slaRespuestaHoras;
        contrato.slaResolucionHoras = slaResolucionHoras;
        contrato.activo = true;
        contrato.setCreatedBy(actor);
        contrato.setUpdatedBy(actor);
        return contrato;
    }

    /**
     * Da de baja logica el contrato (lo marca inactivo) sin destruir el historico
     * de Tickets_Servicio asociados.
     *
     * @param actor identificador de quien realiza la baja, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public TipoContratoMantenimiento getTipo() {
        return tipo;
    }

    public int getSlaRespuestaHoras() {
        return slaRespuestaHoras;
    }

    public int getSlaResolucionHoras() {
        return slaResolucionHoras;
    }

    public boolean isActivo() {
        return activo;
    }
}
