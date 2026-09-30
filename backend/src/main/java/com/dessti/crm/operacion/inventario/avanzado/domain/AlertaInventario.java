package com.dessti.crm.operacion.inventario.avanzado.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de una ALERTA DE STOCK del inventario avanzado (Req 60), mapeada sobre la
 * tabla {@code alerta_inventario} de la migracion V88. Es la BITACORA PERSISTENTE de las
 * condiciones de stock (minimo/maximo/reabastecimiento) que el servicio detecta al evaluar
 * un saldo frente a la configuracion del Material, para que la UI pueda LISTARLAS y darles
 * seguimiento (a diferencia del placeholder previo, que solo escribia en el log).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}; el
 * {@code tenant_id} se fija desde el {@code TenantContext} al persistir (Req 23.4). El mapeo
 * de columnas coincide <em>exactamente</em> con V88.</p>
 *
 * <p><strong>Ciclo de vida:</strong> la deteccion INSERTA; el seguimiento solo alterna
 * {@link #atendida} (no se borra, para conservar el historial).</p>
 */
@Entity
@Table(name = "alerta_inventario")
public class AlertaInventario extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tipo de la alerta; se persiste como etiqueta ASCII (Req 60). */
    @Convert(converter = TipoAlertaInventarioConverter.class)
    @Column(name = "tipo", nullable = false, updatable = false, length = 20)
    private TipoAlertaInventario tipo;

    /** Almacen afectado (Req 60). */
    @Column(name = "almacen_id", nullable = false, updatable = false)
    private UUID almacenId;

    /** Material afectado (Req 60). */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Snapshot del nombre del Material al detectar la alerta; opcional. */
    @Column(name = "nombre_material", updatable = false, length = 200)
    private String nombreMaterial;

    /** Saldo en el momento de la deteccion; no negativo. */
    @Column(name = "cantidad", nullable = false, updatable = false, precision = 18, scale = 3)
    private BigDecimal cantidad;

    /** Umbral cruzado (punto de reorden o stock maximo); no negativo. */
    @Column(name = "umbral", nullable = false, updatable = false, precision = 18, scale = 3)
    private BigDecimal umbral;

    /** Seguimiento: {@code true} cuando la alerta ya fue atendida/resuelta. */
    @Column(name = "atendida", nullable = false)
    private boolean atendida;

    /** Instante de la deteccion. */
    @Column(name = "detectada_en", nullable = false, updatable = false)
    private Instant detectadaEn;

    protected AlertaInventario() {
        // Requerido por JPA.
    }

    /**
     * Registra una nueva alerta de stock detectada (Req 60). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4). Nace no atendida.
     *
     * @param tipo           tipo de la alerta; obligatorio.
     * @param almacenId      Almacen afectado; obligatorio.
     * @param materialId     Material afectado; obligatorio.
     * @param nombreMaterial nombre del Material para el mensaje; opcional.
     * @param cantidad       saldo en el momento de la deteccion; &gt;= 0.
     * @param umbral         umbral cruzado; &gt;= 0.
     * @param detectadaEn    instante de la deteccion; obligatorio.
     * @param actor          identificador de quien la registra (normalmente el sistema).
     * @return la alerta lista para persistir.
     * @throws ReglaNegocioException si faltan datos obligatorios o algun valor es invalido (422).
     */
    public static AlertaInventario registrar(TipoAlertaInventario tipo, UUID almacenId,
                                             UUID materialId, String nombreMaterial,
                                             BigDecimal cantidad, BigDecimal umbral,
                                             Instant detectadaEn, String actor) {
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de la alerta es obligatorio.");
        }
        if (almacenId == null || materialId == null) {
            throw new ReglaNegocioException("La alerta debe referirse a un Almacen y un Material.");
        }
        if (detectadaEn == null) {
            throw new ReglaNegocioException("El instante de deteccion de la alerta es obligatorio.");
        }
        AlertaInventario alerta = new AlertaInventario();
        alerta.id = UUID.randomUUID();
        alerta.tipo = tipo;
        alerta.almacenId = almacenId;
        alerta.materialId = materialId;
        alerta.nombreMaterial = (nombreMaterial == null || nombreMaterial.isBlank())
                ? null : nombreMaterial.trim();
        alerta.cantidad = exigirNoNegativo(cantidad, "La cantidad");
        alerta.umbral = exigirNoNegativo(umbral, "El umbral");
        alerta.atendida = false;
        alerta.detectadaEn = detectadaEn;
        alerta.setCreatedBy(actor);
        alerta.setUpdatedBy(actor);
        return alerta;
    }

    /**
     * Marca la alerta como atendida/no atendida (seguimiento, Req 60).
     *
     * @param atendida nuevo estado de seguimiento.
     * @param actor    identificador de quien actualiza, para {@code updated_by}.
     */
    public void marcarAtendida(boolean atendida, String actor) {
        this.atendida = atendida;
        this.setUpdatedBy(actor);
    }

    private static BigDecimal exigirNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null || valor.signum() < 0) {
            throw new ReglaNegocioException(etiqueta + " de la alerta no puede ser negativo.");
        }
        return valor;
    }

    public UUID getId() {
        return id;
    }

    public TipoAlertaInventario getTipo() {
        return tipo;
    }

    public UUID getAlmacenId() {
        return almacenId;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public String getNombreMaterial() {
        return nombreMaterial;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getUmbral() {
        return umbral;
    }

    public boolean isAtendida() {
        return atendida;
    }

    public Instant getDetectadaEn() {
        return detectadaEn;
    }
}
