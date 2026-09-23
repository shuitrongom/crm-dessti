package com.dessti.crm.operacion.produccion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de una partida (linea de BOM) de consumo de una
 * {@link OrdenFabricacion}: un Material y una cantidad positiva a consumir al
 * fabricar (Req 5.1, §B1). Se mapea sobre la tabla {@code partida_orden_fabricacion}
 * de la migracion V66.
 *
 * <p><strong>Multi-tenant (Req 5.4, Req 23):</strong> extiende
 * {@link TenantScopedEntity} y hereda {@code tenant_id} (asignada automaticamente
 * desde el {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca
 * desde la peticion), {@code version} (concurrencia optimista) y las marcas de
 * auditoria. El mapeo de columnas coincide <em>exactamente</em> con V66. Sigue el
 * patron tenant-scoped del Nucleo (ver {@code Material}).</p>
 *
 * <h2>Reglas de dominio (Req 5)</h2>
 * <ul>
 *   <li>{@link #crear(UUID, UUID, BigDecimal, String)} da de alta una partida
 *       vinculada a la OF y al Material, validando que la {@code cantidad} sea
 *       estrictamente positiva ({@link ReglaNegocioException} 422 si es &le; 0,
 *       Req 5.2). La accesibilidad del Material (404) y el estado de la OF
 *       ({@code pendiente}, 422) los verifica la capa de aplicacion antes de crear
 *       la partida.</li>
 * </ul>
 */
@Entity
@Table(name = "partida_orden_fabricacion")
public class PartidaOrdenFabricacion extends TenantScopedEntity {

    /** Escala decimal de la cantidad, coherente con NUMERIC(18,4) de V66. */
    public static final int ESCALA_CANTIDAD = 4;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Orden_Fabricacion a la que pertenece la partida; inmutable (Req 5.1, §B1). */
    @Column(name = "orden_fabricacion_id", nullable = false, updatable = false)
    private UUID ordenFabricacionId;

    /** Material a consumir; inmutable (Req 5.1). */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Cantidad a consumir; estrictamente positiva (Req 5.1, 5.2). */
    @Column(name = "cantidad", nullable = false, precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal cantidad;

    protected PartidaOrdenFabricacion() {
        // Requerido por JPA.
    }

    /**
     * Crea una partida de consumo vinculada a la Orden_Fabricacion y al Material,
     * validando que la cantidad sea estrictamente positiva (Req 5.1, 5.2). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * <p>Esta factoria NO comprueba la accesibilidad del Material en el tenant (404)
     * ni el estado de la Orden_Fabricacion ({@code pendiente}, 422): ambas
     * verificaciones las realiza la capa de aplicacion
     * ({@code ServicioOrdenesFabricacion}) antes de invocar esta factoria, pues
     * requieren consultar otros agregados y el estado de la OF (§B1).</p>
     *
     * @param ordenFabricacionId Orden_Fabricacion de la partida; obligatorio.
     * @param materialId         Material a consumir; obligatorio (Req 5.1).
     * @param cantidad           cantidad a consumir; estrictamente positiva (Req 5.2).
     * @param actor              identificador de quien crea, para {@code created_by}/
     *                           {@code updated_by}.
     * @return la partida lista para persistir.
     * @throws ReglaNegocioException si falta la OF, el Material o la cantidad, o si
     *         la cantidad es &le; 0 (422, Req 5.2).
     */
    public static PartidaOrdenFabricacion crear(UUID ordenFabricacionId, UUID materialId,
                                                BigDecimal cantidad, String actor) {
        if (ordenFabricacionId == null) {
            throw new ReglaNegocioException(
                    "La partida debe asociarse a una Orden_Fabricacion.");
        }
        if (materialId == null) {
            throw new ReglaNegocioException(
                    "La partida de la Orden_Fabricacion debe referir un Material.");
        }
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ReglaNegocioException(
                    "La cantidad de la partida de la Orden_Fabricacion debe ser mayor que 0.");
        }
        PartidaOrdenFabricacion partida = new PartidaOrdenFabricacion();
        partida.id = UUID.randomUUID();
        partida.ordenFabricacionId = ordenFabricacionId;
        partida.materialId = materialId;
        partida.cantidad = cantidad.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
        partida.setCreatedBy(actor);
        partida.setUpdatedBy(actor);
        return partida;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrdenFabricacionId() {
        return ordenFabricacionId;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }
}
