package com.dessti.crm.vertical.anuncios.pruebadiseno.domain;

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
 * Entidad JPA y raiz del agregado {@code prueba_diseno}: una prueba de diseno
 * (arte) <strong>versionada</strong> vinculada a una {@code Cotizacion}, mapeada
 * sobre la tabla {@code prueba_diseno} de la migracion V16 (Req 15, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V16.</p>
 *
 * <h2>Version de negocio vs concurrencia optimista (CRITICO)</h2>
 * <p>El campo {@link #numeroVersion} es la <strong>version de negocio</strong> de
 * la Prueba_Diseno (1, 2, 3, ...), mapeada sobre la columna {@code numero_version}.
 * Es <em>distinta</em> de la columna heredada {@code version} (BIGINT), que
 * gestiona la concurrencia optimista de Hibernate ({@code @Version}, Req 49). No
 * deben confundirse: reusar {@code version} para el numero de version romperia el
 * bloqueo optimista y el versionado monotono (Property 8).</p>
 *
 * <h2>Reglas de dominio (Req 15)</h2>
 * <ul>
 *   <li>{@link #generarInicial(UUID, String)} crea la version 1 en estado
 *       {@link EstadoPruebaDiseno#PENDIENTE} (Req 15.1).</li>
 *   <li>{@link #aprobar(String, Clock)} aplica {@code pendiente -> aprobada},
 *       registra el actor y el instante UTC de la decision (Req 15.2).</li>
 *   <li>{@link #rechazar(String, Clock)} aplica {@code pendiente -> rechazada},
 *       registra el actor y el instante UTC; la generacion de la nueva version la
 *       coordina la capa de aplicacion via {@link #siguienteVersion(UUID, int,
 *       String)} (Req 15.3, Property 8).</li>
 *   <li>La maquina de estados pura ({@link EstadoPruebaDiseno}) rechaza decidir una
 *       prueba ya decidida con {@link TransicionInvalidaException} (409),
 *       preservando la inmutabilidad del historial (Req 15.4).</li>
 * </ul>
 */
@Entity
@Table(name = "prueba_diseno")
public class PruebaDiseno extends TenantScopedEntity {

    /** Numero de version de negocio de la primera Prueba_Diseno (Req 15.1). */
    public static final int VERSION_INICIAL = 1;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cotizacion existente a la que pertenece la Prueba_Diseno (Req 15.1). */
    @Column(name = "cotizacion_id", nullable = false, updatable = false)
    private UUID cotizacionId;

    /**
     * Version de <strong>negocio</strong> (1, 2, 3, ...). SEPARADA de la columna
     * heredada {@code version} (concurrencia optimista). Es inmutable una vez
     * creada.
     */
    @Column(name = "numero_version", nullable = false, updatable = false)
    private int numeroVersion;

    /** Estado; se persiste como etiqueta ASCII (Req 15.1). */
    @Convert(converter = EstadoPruebaDisenoConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoPruebaDiseno estado;

    /** Actor que aprobo la prueba; {@code null} salvo tras la aprobacion (Req 15.2). */
    @Column(name = "aprobada_por")
    private String aprobadaPor;

    /** Actor que rechazo la prueba; {@code null} salvo tras el rechazo (Req 15.3). */
    @Column(name = "rechazada_por")
    private String rechazadaPor;

    /** Instante UTC en que se decidio (aprobo/rechazo); {@code null} si pendiente. */
    @Column(name = "decidida_en")
    private Instant decididaEn;

    protected PruebaDiseno() {
        // Requerido por JPA.
    }

    /**
     * Genera la Prueba_Diseno <strong>inicial</strong> de una Cotizacion: numero de
     * version 1 y estado {@link EstadoPruebaDiseno#PENDIENTE} (Req 15.1). La
     * existencia de la Cotizacion la verifica la capa de aplicacion antes de
     * invocar este metodo. El {@code tenant_id} lo fija {@link TenantScopedEntity}
     * al persistir (Req 23.4).
     *
     * @param cotizacionId identificador de la Cotizacion existente; obligatorio.
     * @param actor        identificador de quien genera, para {@code created_by}/
     *                     {@code updated_by}.
     * @return la Prueba_Diseno lista para persistir, version 1, en {@code pendiente}.
     * @throws ReglaNegocioException si falta la Cotizacion (422, Req 15.1).
     */
    public static PruebaDiseno generarInicial(UUID cotizacionId, String actor) {
        return nueva(cotizacionId, VERSION_INICIAL, actor);
    }

    /**
     * Crea la <strong>siguiente</strong> version pendiente de una Cotizacion, como
     * consecuencia del rechazo de la version previa (Req 15.3, Property 8). El
     * numero de version debe ser el maximo previo mas 1; la capa de aplicacion lo
     * calcula consultando el repositorio. La unicidad
     * {@code (tenant_id, cotizacion_id, numero_version)} de V16 refuerza el
     * versionado monotono a nivel de BD.
     *
     * @param cotizacionId    identificador de la Cotizacion; obligatorio.
     * @param siguienteNumero numero de version de negocio de la nueva prueba
     *                        (max previo + 1); debe ser &gt;= 2.
     * @param actor           identificador de quien realiza la accion.
     * @return la nueva Prueba_Diseno en {@code pendiente}.
     * @throws ReglaNegocioException si falta la Cotizacion o el numero es &lt; 2 (422).
     */
    public static PruebaDiseno siguienteVersion(UUID cotizacionId, int siguienteNumero, String actor) {
        if (siguienteNumero <= VERSION_INICIAL) {
            throw new ReglaNegocioException(
                    "La siguiente version de una Prueba_Diseno debe ser mayor que "
                            + VERSION_INICIAL + ".");
        }
        return nueva(cotizacionId, siguienteNumero, actor);
    }

    private static PruebaDiseno nueva(UUID cotizacionId, int numeroVersion, String actor) {
        if (cotizacionId == null) {
            throw new ReglaNegocioException(
                    "La Prueba_Diseno debe asociarse a una Cotizacion existente.");
        }
        if (numeroVersion < VERSION_INICIAL) {
            throw new ReglaNegocioException("El numero de version debe ser mayor o igual a "
                    + VERSION_INICIAL + ".");
        }
        PruebaDiseno prueba = new PruebaDiseno();
        prueba.id = UUID.randomUUID();
        prueba.cotizacionId = cotizacionId;
        prueba.numeroVersion = numeroVersion;
        prueba.estado = EstadoPruebaDiseno.PENDIENTE;
        prueba.setCreatedBy(actor);
        prueba.setUpdatedBy(actor);
        return prueba;
    }

    /**
     * Aprueba una Prueba_Diseno en estado {@code pendiente}, registrando el actor y
     * el instante UTC de la decision (Req 15.2). Solo permite la transicion
     * {@code pendiente -> aprobada}: aprobar (o rechazar) una prueba ya decidida se
     * rechaza con {@link TransicionInvalidaException} (409), preservando el
     * historial inmutable (Req 15.4).
     *
     * @param actor identificador de quien aprueba (o representa al Cliente).
     * @param clock reloj (UTC) para fijar {@code decidida_en}; obligatorio.
     * @throws TransicionInvalidaException si la prueba no esta en {@code pendiente} (409).
     */
    public void aprobar(String actor, Clock clock) {
        transicionar(EstadoPruebaDiseno.APROBADA, actor, clock);
        this.aprobadaPor = actor;
    }

    /**
     * Rechaza una Prueba_Diseno en estado {@code pendiente}, registrando el actor y
     * el instante UTC de la decision (Req 15.3). Solo permite la transicion
     * {@code pendiente -> rechazada}. La generacion de la nueva version pendiente
     * (numero + 1) la coordina la capa de aplicacion (Property 8), pues requiere
     * consultar el maximo de version de la Cotizacion.
     *
     * @param actor identificador de quien rechaza (o representa al Cliente).
     * @param clock reloj (UTC) para fijar {@code decidida_en}; obligatorio.
     * @throws TransicionInvalidaException si la prueba no esta en {@code pendiente} (409).
     */
    public void rechazar(String actor, Clock clock) {
        transicionar(EstadoPruebaDiseno.RECHAZADA, actor, clock);
        this.rechazadaPor = actor;
    }

    private void transicionar(EstadoPruebaDiseno destino, String actor, Clock clock) {
        if (clock == null) {
            throw new ReglaNegocioException("El reloj para fijar la decision es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + destino.valorBd()
                            + "' (la Prueba_Diseno ya esta decidida o el destino no es valido).");
        }
        this.estado = destino;
        this.decididaEn = clock.instant();
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCotizacionId() {
        return cotizacionId;
    }

    public int getNumeroVersion() {
        return numeroVersion;
    }

    public EstadoPruebaDiseno getEstado() {
        return estado;
    }

    public String getAprobadaPor() {
        return aprobadaPor;
    }

    public String getRechazadaPor() {
        return rechazadaPor;
    }

    public Instant getDecididaEn() {
        return decididaEn;
    }
}
