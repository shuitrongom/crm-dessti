package com.dessti.crm.facturacion.notacredito.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Entidad JPA y raiz del agregado {@code nota_credito}: una Nota de Credito (CFDI
 * de egreso) que referencia una Factura timbrada, mapeada sobre la tabla
 * {@code nota_credito} de la migracion V30 (Req 37, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id}, {@code version} y las marcas de auditoria. El mapeo de
 * columnas coincide <em>exactamente</em> con V30.</p>
 *
 * <h2>Reglas de dominio (Req 37)</h2>
 * <ul>
 *   <li>{@link #emitir} crea la Nota de Credito en {@code borrador} referenciando
 *       una Factura timbrada, con monto positivo y acotado por el saldo disponible
 *       de la Factura (Req 37.1, 37.2; Property 14). La verificacion de que la
 *       Factura este timbrada y el calculo del saldo (total - notas previas) los
 *       realiza la capa de aplicacion antes de invocar esta fabrica.</li>
 *   <li>{@link #timbrar} registra el Folio_Fiscal y el sello del CFDI de egreso y
 *       transita {@code borrador -> timbrada} (Req 37.1). A partir de ahi el CFDI
 *       es inmutable (Req 37.3; Property 21).</li>
 *   <li>{@link #cancelar} transita {@code timbrada -> cancelada} conservando el
 *       Folio_Fiscal como historico inmutable (Req 37.3).</li>
 * </ul>
 */
@Entity
@Table(name = "nota_credito")
public class NotaCredito extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Factura timbrada referenciada por la Nota de Credito (Req 37.1). */
    @Column(name = "factura_id", nullable = false, updatable = false)
    private UUID facturaId;

    /** Cliente al que se emite, denormalizado para el filtro del listado (Req 37). */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /** Monto del CFDI de egreso, positivo y acotado por el saldo (Req 37.2). */
    @Column(name = "monto", nullable = false, updatable = false)
    private BigDecimal monto;

    /** Estado; se persiste como etiqueta ASCII. */
    @Convert(converter = EstadoNotaCreditoConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoNotaCredito estado;

    /** Folio_Fiscal (UUID del SAT) del CFDI de egreso al timbrar (Req 37.1). */
    @Column(name = "folio_fiscal")
    private UUID folioFiscal;

    /** Sello digital del SAT devuelto por el PAC al timbrar (Req 37.1). */
    @Column(name = "sello_sat")
    private String selloSat;

    /** Fecha/hora del Timbrado en UTC (Req 37.1); {@code null} en borrador. */
    @Column(name = "fecha_timbrado")
    private Instant fechaTimbrado;

    protected NotaCredito() {
        // Requerido por JPA.
    }

    /**
     * Emite una Nota de Credito en {@code borrador} referenciando una Factura
     * timbrada (Req 37.1). El {@code monto} ya debe estar validado contra el saldo
     * disponible de la Factura por la capa de aplicacion
     * ({@link NotaCreditoValidaciones#validarMontoContraSaldo}); aqui se comprueba
     * que sea positivo como red de seguridad (Req 37.2).
     *
     * @param facturaId Factura timbrada referenciada; obligatoria.
     * @param clienteId Cliente al que se emite; obligatorio.
     * @param monto     monto del CFDI de egreso; obligatorio y positivo (Req 37.2).
     * @param actor     identificador de quien emite (auditoria).
     * @return la Nota de Credito lista para persistir, en {@code borrador}.
     * @throws ReglaNegocioException si faltan datos o el monto no es positivo (422).
     */
    public static NotaCredito emitir(UUID facturaId, UUID clienteId, BigDecimal monto, String actor) {
        if (facturaId == null) {
            throw new ReglaNegocioException("La Nota de Credito debe referenciar una Factura timbrada.");
        }
        if (clienteId == null) {
            throw new ReglaNegocioException("La Nota de Credito debe registrar el Cliente.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException("El monto de la Nota de Credito debe ser positivo.");
        }
        NotaCredito nota = new NotaCredito();
        nota.id = UUID.randomUUID();
        nota.facturaId = facturaId;
        nota.clienteId = clienteId;
        nota.monto = monto.setScale(NotaCreditoValidaciones.ESCALA_MONETARIA, RoundingMode.HALF_UP);
        nota.estado = EstadoNotaCredito.BORRADOR;
        nota.setCreatedBy(actor);
        nota.setUpdatedBy(actor);
        return nota;
    }

    /**
     * Registra el resultado exitoso del Timbrado del CFDI de egreso (Folio_Fiscal,
     * sello y fecha) y transita {@code borrador -> timbrada} (Req 37.1). A partir de
     * aqui el CFDI es inmutable (Req 37.3).
     *
     * @param folioFiscal   Folio_Fiscal (UUID del SAT); obligatorio.
     * @param selloSat      sello digital del SAT; obligatorio.
     * @param fechaTimbrado fecha/hora del Timbrado (UTC); obligatoria.
     * @param actor         identificador de quien timbra (auditoria).
     * @throws ReglaNegocioException       si faltan datos del Timbrado (422).
     * @throws TransicionInvalidaException si la Nota no esta en {@code borrador} (409).
     */
    public void timbrar(UUID folioFiscal, String selloSat, Instant fechaTimbrado, String actor) {
        if (folioFiscal == null || selloSat == null || selloSat.isBlank() || fechaTimbrado == null) {
            throw new ReglaNegocioException(
                    "El Timbrado requiere Folio_Fiscal, sello y fecha del PAC.");
        }
        transitar(EstadoNotaCredito.TIMBRADA);
        this.folioFiscal = folioFiscal;
        this.selloSat = selloSat;
        this.fechaTimbrado = fechaTimbrado;
        this.setUpdatedBy(actor);
    }

    /**
     * Cancela una Nota de Credito timbrada y transita {@code timbrada -> cancelada}.
     * El Folio_Fiscal y el sello se conservan como historico inmutable (Req 37.3).
     *
     * @param actor identificador de quien cancela (auditoria).
     * @throws TransicionInvalidaException si la Nota no esta {@code timbrada} (409).
     */
    public void cancelar(String actor) {
        transitar(EstadoNotaCredito.CANCELADA);
        this.setUpdatedBy(actor);
    }

    private void transitar(EstadoNotaCredito destino) {
        if (destino == null) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
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

    public UUID getFacturaId() {
        return facturaId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public EstadoNotaCredito getEstado() {
        return estado;
    }

    public UUID getFolioFiscal() {
        return folioFiscal;
    }

    public String getSelloSat() {
        return selloSat;
    }

    public Instant getFechaTimbrado() {
        return fechaTimbrado;
    }
}
