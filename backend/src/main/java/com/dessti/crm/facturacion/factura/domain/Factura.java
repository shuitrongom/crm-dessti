package com.dessti.crm.facturacion.factura.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.facturacion.factura.domain.CalculoFiscalCfdi.ImportesFiscales;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code factura}: una Factura (CFDI 4.0) emitida a
 * un Cliente, mapeada sobre la tabla {@code factura} de la migracion V30 (Req 34,
 * 35, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada desde el {@code TenantContext} al persistir,
 * nunca desde la peticion, Req 23.4), {@code version} (concurrencia optimista,
 * Req 49) y las marcas de auditoria. El mapeo de columnas coincide
 * <em>exactamente</em> con V30.</p>
 *
 * <h2>Reglas de dominio (Req 34, 35)</h2>
 * <ul>
 *   <li>{@link #emitirDesdeCotizacion} / {@link #emitirDesdeOrdenFabricacion}
 *       validan los datos fiscales del receptor (Req 34.1, 34.3), calculan
 *       subtotal/IVA/retenciones/total half-up (Req 34.2; Property 4) y fijan el
 *       estado inicial {@link EstadoFactura#BORRADOR} (Req 34.1). Cada Factura tiene
 *       exactamente un origen: una Cotizacion aprobada o una Orden_Fabricacion.</li>
 *   <li>{@link #timbrar} registra el Folio_Fiscal y el sello devueltos por el PAC y
 *       transita {@code borrador -> timbrada} (Req 35.1). A partir de ahi los datos
 *       fiscales son <strong>inmutables</strong> (Req 35.3; Property 21).</li>
 *   <li>{@link #iniciarCancelacion} transita {@code timbrada ->
 *       cancelacion_en_proceso} registrando el motivo SAT (Req 35.4, 35.5);
 *       {@link #confirmarCancelacion} transita {@code cancelacion_en_proceso ->
 *       cancelada} (Req 35.5). El Folio_Fiscal y el sello se conservan como
 *       historico inmutable aun tras la cancelacion (Req 35.6).</li>
 * </ul>
 */
@Entity
@Table(name = "factura")
public class Factura extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cotizacion (aprobada) de origen; {@code null} si el origen es una OF (Req 34.1). */
    @Column(name = "cotizacion_id", updatable = false)
    private UUID cotizacionId;

    /** Orden_Fabricacion de origen; {@code null} si el origen es una Cotizacion (Req 34.1). */
    @Column(name = "orden_fabricacion_id", updatable = false)
    private UUID ordenFabricacionId;

    /** Cliente al que se emite la Factura, para el filtro del listado (Req 34.4). */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /** RFC del receptor (Req 34.1). Inmutable una vez timbrada (Req 35.3). */
    @Column(name = "receptor_rfc", nullable = false)
    private String receptorRfc;

    /** Nombre o razon social del receptor (Req 34.1). */
    @Column(name = "receptor_nombre", nullable = false)
    private String receptorNombre;

    /** Codigo postal del domicilio fiscal del receptor (Req 34.1). */
    @Column(name = "receptor_cp", nullable = false)
    private String receptorCp;

    /** Clave del regimen fiscal del receptor (Req 34.1). */
    @Column(name = "receptor_regimen_fiscal", nullable = false)
    private String receptorRegimenFiscal;

    /** Clave del uso de CFDI (Req 34.1). */
    @Column(name = "uso_cfdi", nullable = false)
    private String usoCfdi;

    /** Subtotal de la Factura, escala 2 (Req 34.2). */
    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    /** IVA trasladado = round(subtotal * 0.16, 2) (Req 34.2). */
    @Column(name = "iva", nullable = false)
    private BigDecimal iva;

    /** Retenciones = round(subtotal * tasaRetencion, 2) (Req 34.2). */
    @Column(name = "retenciones", nullable = false)
    private BigDecimal retenciones;

    /** Total = round(subtotal + iva - retenciones, 2) (Req 34.2). */
    @Column(name = "total", nullable = false)
    private BigDecimal total;

    /** Estado; se persiste como etiqueta ASCII (Req 35.7). */
    @Convert(converter = EstadoFacturaConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoFactura estado;

    /** Folio_Fiscal (UUID del SAT) asignado al timbrar (Req 35.1); {@code null} en borrador. */
    @Column(name = "folio_fiscal")
    private UUID folioFiscal;

    /** Sello digital del SAT devuelto por el PAC al timbrar (Req 35.1). */
    @Column(name = "sello_sat")
    private String selloSat;

    /** Fecha/hora del Timbrado en UTC (Req 35.1); {@code null} en borrador. */
    @Column(name = "fecha_timbrado")
    private Instant fechaTimbrado;

    /** Motivo de cancelacion del catalogo del SAT (Req 35.4); {@code null} si no se cancela. */
    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    protected Factura() {
        // Requerido por JPA.
    }

    /**
     * Emite una Factura en {@code borrador} a partir de una Cotizacion aprobada
     * (Req 34.1). La precondicion de que la Cotizacion este {@code aprobada} la
     * verifica la capa de aplicacion antes de invocar esta fabrica.
     *
     * @param cotizacionId  Cotizacion aprobada de origen; obligatoria.
     * @param clienteId     Cliente al que se emite (Req 34.4); obligatorio.
     * @param datosFiscales datos fiscales validados del receptor (Req 34.1, 34.3).
     * @param subtotal      subtotal base para el calculo fiscal (Req 34.2).
     * @param tasaRetencion tasa de retencion aplicable ({@code 0} si no aplica).
     * @param actor         identificador de quien emite (auditoria).
     * @return la Factura lista para persistir, en {@code borrador} y con importes.
     * @throws ReglaNegocioException si faltan datos o el calculo es invalido (422).
     */
    public static Factura emitirDesdeCotizacion(UUID cotizacionId, UUID clienteId,
                                                DatosFiscalesReceptor datosFiscales,
                                                BigDecimal subtotal, BigDecimal tasaRetencion,
                                                String actor) {
        if (cotizacionId == null) {
            throw new ReglaNegocioException("La Factura debe referenciar la Cotizacion de origen.");
        }
        Factura factura = nuevaBorrador(clienteId, datosFiscales, subtotal, tasaRetencion, actor);
        factura.cotizacionId = cotizacionId;
        return factura;
    }

    /**
     * Emite una Factura en {@code borrador} a partir de una Orden_Fabricacion
     * (Req 34.1).
     *
     * @param ordenFabricacionId Orden_Fabricacion de origen; obligatoria.
     * @param clienteId          Cliente al que se emite (Req 34.4); obligatorio.
     * @param datosFiscales      datos fiscales validados del receptor (Req 34.1, 34.3).
     * @param subtotal           subtotal base para el calculo fiscal (Req 34.2).
     * @param tasaRetencion      tasa de retencion aplicable ({@code 0} si no aplica).
     * @param actor              identificador de quien emite (auditoria).
     * @return la Factura lista para persistir, en {@code borrador} y con importes.
     * @throws ReglaNegocioException si faltan datos o el calculo es invalido (422).
     */
    public static Factura emitirDesdeOrdenFabricacion(UUID ordenFabricacionId, UUID clienteId,
                                                      DatosFiscalesReceptor datosFiscales,
                                                      BigDecimal subtotal, BigDecimal tasaRetencion,
                                                      String actor) {
        if (ordenFabricacionId == null) {
            throw new ReglaNegocioException(
                    "La Factura debe referenciar la Orden_Fabricacion de origen.");
        }
        Factura factura = nuevaBorrador(clienteId, datosFiscales, subtotal, tasaRetencion, actor);
        factura.ordenFabricacionId = ordenFabricacionId;
        return factura;
    }

    private static Factura nuevaBorrador(UUID clienteId, DatosFiscalesReceptor datosFiscales,
                                         BigDecimal subtotal, BigDecimal tasaRetencion, String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException("La Factura debe emitirse a un Cliente existente.");
        }
        if (datosFiscales == null) {
            throw new ReglaNegocioException("Los datos fiscales del receptor son obligatorios.");
        }
        ImportesFiscales importes = CalculoFiscalCfdi.calcular(subtotal, tasaRetencion);

        Factura factura = new Factura();
        factura.id = UUID.randomUUID();
        factura.clienteId = clienteId;
        factura.receptorRfc = datosFiscales.rfc();
        factura.receptorNombre = datosFiscales.nombre();
        factura.receptorCp = datosFiscales.codigoPostal();
        factura.receptorRegimenFiscal = datosFiscales.regimenFiscal();
        factura.usoCfdi = datosFiscales.usoCfdi();
        factura.subtotal = importes.subtotal();
        factura.iva = importes.iva();
        factura.retenciones = importes.retenciones();
        factura.total = importes.total();
        factura.estado = EstadoFactura.BORRADOR;
        factura.setCreatedBy(actor);
        factura.setUpdatedBy(actor);
        return factura;
    }

    /**
     * Registra el resultado exitoso del Timbrado del PAC (Folio_Fiscal, sello y
     * fecha) y transita {@code borrador -> timbrada} (Req 35.1). A partir de aqui
     * los datos fiscales quedan inmutables (Req 35.3).
     *
     * @param folioFiscal   Folio_Fiscal (UUID del SAT); obligatorio.
     * @param selloSat      sello digital del SAT; obligatorio.
     * @param fechaTimbrado fecha/hora del Timbrado (UTC); obligatoria.
     * @param actor         identificador de quien timbra (auditoria).
     * @throws ReglaNegocioException       si faltan datos del Timbrado (422).
     * @throws TransicionInvalidaException si la Factura no esta en {@code borrador} (409).
     */
    public void timbrar(UUID folioFiscal, String selloSat, Instant fechaTimbrado, String actor) {
        if (folioFiscal == null || selloSat == null || selloSat.isBlank() || fechaTimbrado == null) {
            throw new ReglaNegocioException(
                    "El Timbrado requiere Folio_Fiscal, sello y fecha del PAC.");
        }
        transitar(EstadoFactura.TIMBRADA);
        this.folioFiscal = folioFiscal;
        this.selloSat = selloSat;
        this.fechaTimbrado = fechaTimbrado;
        this.setUpdatedBy(actor);
    }

    /**
     * Inicia la cancelacion de una Factura timbrada registrando el motivo del
     * catalogo del SAT y transita {@code timbrada -> cancelacion_en_proceso}
     * (Req 35.4, 35.5).
     *
     * @param motivoSat clave del motivo de cancelacion del SAT; obligatoria (Req 35.4).
     * @param actor     identificador de quien cancela (auditoria).
     * @throws ReglaNegocioException       si falta el motivo (422).
     * @throws TransicionInvalidaException si la Factura no esta {@code timbrada} (409).
     */
    public void iniciarCancelacion(String motivoSat, String actor) {
        if (motivoSat == null || motivoSat.isBlank()) {
            throw new ReglaNegocioException(
                    "Se requiere un motivo de cancelacion conforme a los catalogos del SAT.");
        }
        transitar(EstadoFactura.CANCELACION_EN_PROCESO);
        this.motivoCancelacion = motivoSat.strip();
        this.setUpdatedBy(actor);
    }

    /**
     * Confirma la cancelacion y transita {@code cancelacion_en_proceso -> cancelada}
     * (Req 35.5). El Folio_Fiscal, el sello y los datos fiscales se conservan como
     * historico inmutable (Req 35.6).
     *
     * @param actor identificador de quien confirma (auditoria).
     * @throws TransicionInvalidaException si la Factura no esta en
     *         {@code cancelacion_en_proceso} (409).
     */
    public void confirmarCancelacion(String actor) {
        transitar(EstadoFactura.CANCELADA);
        this.setUpdatedBy(actor);
    }

    /**
     * Aplica una transicion de estado por la maquina de estados pura (Req 35.7).
     * Toda transicion no permitida —incluida cualquiera que parta de un estado
     * final— se rechaza con {@link TransicionInvalidaException} (409) conservando
     * el estado actual.
     */
    private void transitar(EstadoFactura destino) {
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

    /**
     * Indica si los datos fiscales de la Factura son <strong>modificables</strong>:
     * solo mientras la Factura esta en {@code borrador} (Req 35.3; Property 21). Una
     * vez timbrada (o cancelada) son inmutables.
     *
     * @return {@code true} si la Factura esta en {@code borrador}.
     */
    public boolean datosFiscalesModificables() {
        return this.estado == EstadoFactura.BORRADOR;
    }

    /**
     * Actualiza los datos fiscales del receptor y recalcula los importes, solo si la
     * Factura sigue en {@code borrador} (Req 35.3). Una vez timbrada, cualquier
     * intento de modificar los datos fiscales se rechaza con
     * {@link ReglaNegocioException} (422), preservando la inmutabilidad del CFDI
     * timbrado (Property 21).
     *
     * @param datosFiscales nuevos datos fiscales validados del receptor.
     * @param subtotal      nuevo subtotal base del calculo fiscal.
     * @param tasaRetencion tasa de retencion aplicable ({@code 0} si no aplica).
     * @param actor         identificador de quien modifica (auditoria).
     * @throws ReglaNegocioException si la Factura no esta en {@code borrador} (422)
     *         o si los datos/calculo son invalidos.
     */
    public void modificarDatosFiscales(DatosFiscalesReceptor datosFiscales, BigDecimal subtotal,
                                       BigDecimal tasaRetencion, String actor) {
        if (!datosFiscalesModificables()) {
            throw new ReglaNegocioException(
                    "No se pueden modificar los datos fiscales de una Factura en estado '"
                            + this.estado.valorBd() + "' (inmutabilidad del CFDI timbrado).");
        }
        if (datosFiscales == null) {
            throw new ReglaNegocioException("Los datos fiscales del receptor son obligatorios.");
        }
        ImportesFiscales importes = CalculoFiscalCfdi.calcular(subtotal, tasaRetencion);
        this.receptorRfc = datosFiscales.rfc();
        this.receptorNombre = datosFiscales.nombre();
        this.receptorCp = datosFiscales.codigoPostal();
        this.receptorRegimenFiscal = datosFiscales.regimenFiscal();
        this.usoCfdi = datosFiscales.usoCfdi();
        this.subtotal = importes.subtotal();
        this.iva = importes.iva();
        this.retenciones = importes.retenciones();
        this.total = importes.total();
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCotizacionId() {
        return cotizacionId;
    }

    public UUID getOrdenFabricacionId() {
        return ordenFabricacionId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public String getReceptorRfc() {
        return receptorRfc;
    }

    public String getReceptorNombre() {
        return receptorNombre;
    }

    public String getReceptorCp() {
        return receptorCp;
    }

    public String getReceptorRegimenFiscal() {
        return receptorRegimenFiscal;
    }

    public String getUsoCfdi() {
        return usoCfdi;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getIva() {
        return iva;
    }

    public BigDecimal getRetenciones() {
        return retenciones;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public EstadoFactura getEstado() {
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

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }
}
