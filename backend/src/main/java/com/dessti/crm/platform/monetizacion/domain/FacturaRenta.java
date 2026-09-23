package com.dessti.crm.platform.monetizacion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA y raiz del agregado {@code factura_renta}: la factura de renta
 * mensual de una Empresa por sus modulos habilitados (V23). Tabla de plataforma
 * (sin RLS), administrada por el {@code super_admin}.
 *
 * <p>La factura se construye con {@link #emitir(UUID, LocalDate, String, List, String)}
 * a partir de un conjunto de lineas (una por modulo habilitado con su precio
 * aplicado en la moneda de la factura). El {@code total} se calcula como la suma
 * de los precios aplicados, redondeada a 2 decimales (HALF_UP).</p>
 */
@Entity
@Table(name = "factura_renta")
public class FacturaRenta {

    /** Estado emitido (unica factura por Empresa/periodo/moneda). */
    public static final String ESTADO_EMITIDA = "emitida";
    /** Estado cancelado. */
    public static final String ESTADO_CANCELADA = "cancelada";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Periodo facturado (primer dia del mes). */
    @Column(name = "periodo", nullable = false, updatable = false)
    private LocalDate periodo;

    @Column(name = "moneda_codigo", nullable = false, updatable = false, length = 3)
    private String monedaCodigo;

    @Column(name = "total", nullable = false, precision = 18, scale = 2)
    private BigDecimal total;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "emitida_en", nullable = false, updatable = false)
    private Instant emitidaEn;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_renta_id", nullable = false, insertable = false, updatable = false)
    private List<FacturaRentaLinea> lineas = new ArrayList<>();

    protected FacturaRenta() {
        // Requerido por JPA.
    }

    /**
     * Emite una factura de renta para una Empresa en un periodo y moneda, con una
     * linea por (clave de modulo, nombre, precio aplicado). El total es la suma de
     * los precios aplicados (HALF_UP, escala 2).
     *
     * @param tenantId     Empresa facturada; obligatorio.
     * @param periodo      periodo (primer dia del mes); obligatorio.
     * @param monedaCodigo moneda de la factura (ISO 4217, mayusculas); obligatorio.
     * @param lineasCrudas lineas a incluir (clave, nombre, precio); al menos una.
     * @param actor        identificador de quien emite (super_admin).
     * @return la factura lista para persistir con su total calculado.
     * @throws ReglaNegocioException si faltan datos o no hay lineas.
     */
    public static FacturaRenta emitir(UUID tenantId, LocalDate periodo, String monedaCodigo,
                                      List<LineaCruda> lineasCrudas, String actor) {
        if (tenantId == null) {
            throw new ReglaNegocioException("La Empresa a facturar es obligatoria.");
        }
        if (periodo == null) {
            throw new ReglaNegocioException("El periodo a facturar es obligatorio.");
        }
        String moneda = MonetizacionValidaciones.normalizarCodigoMoneda(monedaCodigo);
        if (lineasCrudas == null || lineasCrudas.isEmpty()) {
            throw new ReglaNegocioException(
                    "No hay modulos habilitados con precio para facturar en la moneda " + moneda + ".");
        }

        FacturaRenta f = new FacturaRenta();
        f.id = UUID.randomUUID();
        f.tenantId = tenantId;
        f.periodo = periodo.withDayOfMonth(1);
        f.monedaCodigo = moneda;
        f.estado = ESTADO_EMITIDA;
        f.emitidaEn = Instant.now();
        f.createdBy = actor;
        f.updatedBy = actor;

        BigDecimal total = BigDecimal.ZERO;
        for (LineaCruda lc : lineasCrudas) {
            FacturaRentaLinea linea = FacturaRentaLinea.crear(
                    f.id, lc.moduloClave(), lc.moduloNombre(), lc.precio(), actor);
            f.lineas.add(linea);
            total = total.add(linea.getPrecioAplicado());
        }
        f.total = total.setScale(MonetizacionValidaciones.ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return f;
    }

    /** Cancela la factura de renta. */
    public void cancelar(String actor) {
        this.estado = ESTADO_CANCELADA;
        this.updatedBy = actor;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public LocalDate getPeriodo() {
        return periodo;
    }

    public String getMonedaCodigo() {
        return monedaCodigo;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getEstado() {
        return estado;
    }

    public Instant getEmitidaEn() {
        return emitidaEn;
    }

    public long getVersion() {
        return version;
    }

    public List<FacturaRentaLinea> getLineas() {
        return Collections.unmodifiableList(lineas);
    }

    /**
     * Dato de entrada para una linea de la factura: clave del modulo, nombre
     * visible y precio aplicado (ya resuelto: especial o de lista).
     */
    public record LineaCruda(String moduloClave, String moduloNombre, BigDecimal precio) {
    }
}