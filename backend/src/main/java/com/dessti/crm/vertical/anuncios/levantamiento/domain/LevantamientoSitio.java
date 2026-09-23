package com.dessti.crm.vertical.anuncios.levantamiento.domain;

import java.time.Clock;
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
 * Entidad JPA y raiz del agregado {@code levantamiento_sitio}: el levantamiento de
 * las condiciones fisicas y electricas de un Sitio antes de fabricar e instalar,
 * mapeado sobre la tabla {@code levantamiento_sitio} de la migracion V19
 * (Req 16, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V19.</p>
 *
 * <h2>Reglas de dominio (Req 16)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, String, UUID, UUID, UUID, String)} valida
 *       los datos obligatorios (mediciones, tipo de superficie/estructura y
 *       condiciones electricas) y fija el estado inicial
 *       {@link EstadoLevantamiento#EN_PROCESO} (Req 16.1). Los vinculos a Sitio,
 *       Cotizacion y Orden_Fabricacion son <em>opcionales</em> (Req 16.2); la
 *       existencia de los vinculos proporcionados la verifica la capa de
 *       aplicacion.</li>
 *   <li>{@link #completar(String, Clock)} aplica la transicion
 *       {@code en_proceso -> completado}, registrando el actor y el instante UTC
 *       (Clock inyectado) de la finalizacion (Req 16.4). Completar un
 *       Levantamiento ya completado se rechaza con
 *       {@link TransicionInvalidaException} (409), conservando el estado.</li>
 * </ul>
 *
 * <p><strong>Fotografias (Req 16.3):</strong> se modelan como entidad hija
 * independiente {@link LevantamientoFoto} (una fila por foto), no como coleccion
 * embebida, siguiendo el patron de {@code Contacto} respecto de {@code Cliente}.</p>
 */
@Entity
@Table(name = "levantamiento_sitio")
public class LevantamientoSitio extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Vinculo OPCIONAL al Sitio (Req 16.2). Referencia debil (sin FK, ver V19). */
    @Column(name = "sitio_id")
    private UUID sitioId;

    /** Vinculo OPCIONAL a la Cotizacion (Req 16.2). FK NULL -> cotizacion (V19). */
    @Column(name = "cotizacion_id")
    private UUID cotizacionId;

    /** Vinculo OPCIONAL a la Orden_Fabricacion (Req 16.2). FK NULL -> orden_fabricacion (V19). */
    @Column(name = "orden_fabricacion_id")
    private UUID ordenFabricacionId;

    /** Mediciones del sitio; dato obligatorio (Req 16.1). */
    @Column(name = "mediciones", nullable = false)
    private String mediciones;

    /** Tipo de superficie o estructura; dato obligatorio (Req 16.1). */
    @Column(name = "tipo_superficie", nullable = false)
    private String tipoSuperficie;

    /** Condiciones electricas; dato obligatorio (Req 16.1). */
    @Column(name = "condiciones_electricas", nullable = false)
    private String condicionesElectricas;

    /** Estado; se persiste como etiqueta ASCII (Req 16.1, 16.4). */
    @Convert(converter = EstadoLevantamientoConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoLevantamiento estado;

    /** Actor que completo el levantamiento; {@code null} mientras esta en proceso (Req 16.4). */
    @Column(name = "completado_por")
    private String completadoPor;

    /** Instante UTC de la finalizacion; {@code null} mientras esta en proceso (Req 16.4). */
    @Column(name = "completado_en")
    private Instant completadoEn;

    protected LevantamientoSitio() {
        // Requerido por JPA.
    }

    /**
     * Crea un Levantamiento_Sitio nuevo validando los datos obligatorios y fijando
     * el estado inicial {@link EstadoLevantamiento#EN_PROCESO} (Req 16.1). Los
     * vinculos a Sitio, Cotizacion y Orden_Fabricacion son opcionales (Req 16.2);
     * su existencia en el tenant la verifica la capa de aplicacion antes de
     * persistir. El {@code tenant_id} lo fija {@link TenantScopedEntity} al
     * persistir (Req 23.4).
     *
     * @param mediciones            mediciones del sitio; obligatorio (no vacio).
     * @param tipoSuperficie        tipo de superficie o estructura; obligatorio (1..200).
     * @param condicionesElectricas condiciones electricas; obligatorio (no vacio).
     * @param sitioId               Sitio vinculado; opcional (Req 16.2).
     * @param cotizacionId          Cotizacion vinculada; opcional (Req 16.2).
     * @param ordenFabricacionId    Orden_Fabricacion vinculada; opcional (Req 16.2).
     * @param actor                 identificador de quien crea, para {@code created_by}/
     *                              {@code updated_by}.
     * @return el Levantamiento_Sitio listo para persistir, en {@code en_proceso}.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido
     *         (422, Req 16.1).
     */
    public static LevantamientoSitio crear(String mediciones, String tipoSuperficie,
                                           String condicionesElectricas, UUID sitioId,
                                           UUID cotizacionId, UUID ordenFabricacionId,
                                           String actor) {
        LevantamientoSitio levantamiento = new LevantamientoSitio();
        levantamiento.id = UUID.randomUUID();
        levantamiento.mediciones = exigirTexto(mediciones, "las mediciones");
        levantamiento.tipoSuperficie = exigirTipoSuperficie(tipoSuperficie);
        levantamiento.condicionesElectricas =
                exigirTexto(condicionesElectricas, "las condiciones electricas");
        levantamiento.sitioId = sitioId;
        levantamiento.cotizacionId = cotizacionId;
        levantamiento.ordenFabricacionId = ordenFabricacionId;
        levantamiento.estado = EstadoLevantamiento.EN_PROCESO;
        levantamiento.setCreatedBy(actor);
        levantamiento.setUpdatedBy(actor);
        return levantamiento;
    }

    /**
     * Marca el Levantamiento_Sitio como {@link EstadoLevantamiento#COMPLETADO},
     * registrando el actor y el instante UTC de la finalizacion (Req 16.4). Aplica
     * la maquina de estados pura: solo permite {@code en_proceso -> completado}.
     * Completar un Levantamiento ya completado se rechaza con
     * {@link TransicionInvalidaException} (409), conservando el estado actual.
     *
     * @param actor identificador de quien completa; obligatorio.
     * @param clock reloj (UTC) para fijar {@code completado_en}; obligatorio.
     * @throws ReglaNegocioException       si el reloj es nulo (422).
     * @throws TransicionInvalidaException si el Levantamiento no esta en
     *                                     {@code en_proceso} (409, Req 16.4).
     */
    public void completar(String actor, Clock clock) {
        if (clock == null) {
            throw new ReglaNegocioException("El reloj para fijar la finalizacion es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(EstadoLevantamiento.COMPLETADO)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + EstadoLevantamiento.COMPLETADO.valorBd()
                            + "' (el Levantamiento_Sitio ya esta completado).");
        }
        this.estado = EstadoLevantamiento.COMPLETADO;
        this.completadoPor = actor;
        this.completadoEn = clock.instant();
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si el Levantamiento_Sitio esta {@code completado} (Req 16.4, 16.5).
     *
     * @return {@code true} si el estado es {@link EstadoLevantamiento#COMPLETADO}.
     */
    public boolean estaCompletado() {
        return this.estado == EstadoLevantamiento.COMPLETADO;
    }

    private static String exigirTexto(String valor, String etiqueta) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El campo obligatorio " + etiqueta
                    + " del Levantamiento_Sitio no puede estar vacio.");
        }
        return valor.strip();
    }

    private static String exigirTipoSuperficie(String valor) {
        String normalizado = exigirTexto(valor, "el tipo de superficie o estructura");
        if (normalizado.length() > 200) {
            throw new ReglaNegocioException(
                    "El tipo de superficie o estructura no puede exceder 200 caracteres.");
        }
        return normalizado;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSitioId() {
        return sitioId;
    }

    public UUID getCotizacionId() {
        return cotizacionId;
    }

    public UUID getOrdenFabricacionId() {
        return ordenFabricacionId;
    }

    public String getMediciones() {
        return mediciones;
    }

    public String getTipoSuperficie() {
        return tipoSuperficie;
    }

    public String getCondicionesElectricas() {
        return condicionesElectricas;
    }

    public EstadoLevantamiento getEstado() {
        return estado;
    }

    public String getCompletadoPor() {
        return completadoPor;
    }

    public Instant getCompletadoEn() {
        return completadoEn;
    }
}
