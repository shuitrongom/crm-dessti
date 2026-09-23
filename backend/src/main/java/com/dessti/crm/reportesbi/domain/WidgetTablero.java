package com.dessti.crm.reportesbi.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidad JPA {@code widget_tablero}: un widget de un {@link TableroPersonalizado} que
 * referencia una metrica de un area (Req 48.3), mapeada sobre la tabla
 * {@code widget_tablero} de la migracion V44.
 *
 * <p><strong>Multi-tenant (Req 23, 48.5):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente al persistir), {@code version}
 * (Req 49) y las marcas de auditoria. El mapeo de columnas coincide exactamente con
 * V44. El widget no tiene vida propia fuera de su tablero: pertenece a la composicion
 * del agregado {@link TableroPersonalizado} (cascada/orphan removal).</p>
 */
@Entity
@Table(name = "widget_tablero")
public class WidgetTablero extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tablero personalizado al que pertenece el widget (composicion, Req 48.3). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "tablero_personalizado_id", nullable = false)
    private TableroPersonalizado tablero;

    /** Etiqueta ASCII del area (AreaIndicador.etiqueta()); obligatoria. */
    @Column(name = "area", nullable = false, length = 40)
    private String area;

    /** Clave ASCII de la metrica (ValorIndicador.clave()); obligatoria. */
    @Column(name = "metrica", nullable = false, length = 80)
    private String metrica;

    /** Orden de presentacion del widget dentro del tablero; >= 0. */
    @Column(name = "orden", nullable = false)
    private int orden;

    /** Parametros de presentacion como JSON opaco; opcional. */
    @Column(name = "configuracion_json")
    private String configuracionJson;

    protected WidgetTablero() {
        // Requerido por JPA.
    }

    /**
     * Crea un widget vinculado a un tablero, validando los campos obligatorios
     * (Req 48.3).
     *
     * @param tablero            tablero contenedor; obligatorio.
     * @param area               etiqueta del area; obligatoria (1..40).
     * @param metrica            clave de la metrica; obligatoria (1..80).
     * @param orden              orden de presentacion; no negativo.
     * @param configuracionJson  parametros de presentacion como JSON; opcional.
     * @param actor              actor para {@code created_by}/{@code updated_by}.
     * @return el widget listo para persistir dentro del tablero.
     * @throws ReglaNegocioException si falta el area o la metrica (422).
     */
    static WidgetTablero crear(TableroPersonalizado tablero, String area, String metrica,
                               int orden, String configuracionJson, String actor) {
        if (area == null || area.isBlank()) {
            throw new ReglaNegocioException("El widget debe indicar el area.");
        }
        if (metrica == null || metrica.isBlank()) {
            throw new ReglaNegocioException("El widget debe indicar la metrica.");
        }
        if (orden < 0) {
            throw new ReglaNegocioException("El orden del widget no puede ser negativo.");
        }
        WidgetTablero widget = new WidgetTablero();
        widget.id = UUID.randomUUID();
        widget.tablero = tablero;
        widget.area = area.trim();
        widget.metrica = metrica.trim();
        widget.orden = orden;
        widget.configuracionJson = configuracionJson;
        widget.setCreatedBy(actor);
        widget.setUpdatedBy(actor);
        return widget;
    }

    public UUID getId() {
        return id;
    }

    public TableroPersonalizado getTablero() {
        return tablero;
    }

    public String getArea() {
        return area;
    }

    public String getMetrica() {
        return metrica;
    }

    public int getOrden() {
        return orden;
    }

    public String getConfiguracionJson() {
        return configuracionJson;
    }
}
