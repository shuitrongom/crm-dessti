package com.dessti.crm.comercial.cotizacion.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code cotizacion} (documento comercial con
 * partidas y totales), mapeada sobre la tabla {@code cotizacion} de la migracion
 * V14 (Req 6, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V14.</p>
 *
 * <h2>Reglas de dominio (Req 6)</h2>
 * <ul>
 *   <li>{@link #crear} exige entre 1 y 500 partidas (Req 6.1, 6.2), fija el estado
 *       inicial {@link EstadoCotizacion#BORRADOR} y recalcula el total como
 *       {@code round(Σ subtotales, 2)} (Req 6.5; Property 2).</li>
 *   <li>{@link #crearCascaronConversion} crea una Cotizacion en
 *       {@code borrador} <em>sin</em> partidas, vinculada a la Oportunidad de
 *       origen (Req 14.5); las partidas se agregan despues. El envio
 *       ({@code borrador -> enviada}) exige >=1 partida, de modo que un cascaron
 *       vacio no puede enviarse.</li>
 *   <li>{@link #agregarPartida} anade una partida y recalcula los totales,
 *       manteniendo el invariante {@code total == Σ subtotales} (Property 2). Solo
 *       se permite mientras la Cotizacion esta en {@code borrador}.</li>
 *   <li>{@link #cambiarEstado} aplica la maquina de estados pura (Req 6.6, 6.7);
 *       la transicion {@code borrador -> enviada} exige >=1 partida.</li>
 * </ul>
 */
@Entity
@Table(name = "cotizacion")
public class Cotizacion extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cliente existente al que pertenece la Cotizacion (Req 6.1). */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /**
     * Oportunidad de origen cuando la Cotizacion se creo por conversion (Req 14.5);
     * {@code null} en el alta manual.
     */
    @Column(name = "oportunidad_id", updatable = false)
    private UUID oportunidadId;

    /** Estado; se persiste como etiqueta ASCII (Req 6.6). */
    @Convert(converter = EstadoCotizacionConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoCotizacion estado;

    /** Suma de los subtotales antes de consolidar; escala 2 (Req 6.5). */
    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    /**
     * Total neto = subtotal - descuento_global + iva - retencion_isr -
     * retencion_iva (V80; antes era simplemente Σ subtotales). Escala 2 half-up.
     */
    @Column(name = "total", nullable = false)
    private BigDecimal total;

    /**
     * Descuento global (monto) que se resta del subtotal para obtener la base
     * gravable (V80); escala 2, no negativo. Por defecto 0.
     */
    @Column(name = "descuento_global", nullable = false)
    private BigDecimal descuentoGlobal;

    /** IVA consolidado = suma del IVA de cada partida (V80); escala 2. */
    @Column(name = "iva", nullable = false)
    private BigDecimal iva;

    /** Retencion de ISR (monto) que se resta del total (V80); escala 2. Por defecto 0. */
    @Column(name = "retencion_isr", nullable = false)
    private BigDecimal retencionIsr;

    /** Retencion de IVA (monto) que se resta del total (V80); escala 2. Por defecto 0. */
    @Column(name = "retencion_iva", nullable = false)
    private BigDecimal retencionIva;

    /**
     * Canal de venta al que se clasifica la Cotizacion (Req 63.1); {@code null}
     * mientras no se clasifica. La clasificacion es <strong>opcional</strong>. Se
     * persiste sobre la columna nullable {@code canal_venta_id} (FK a
     * {@code canal_venta}, V15) para su analisis y para la segmentacion de
     * reportes comerciales por canal (Req 63.2).
     */
    @Column(name = "canal_venta_id")
    private UUID canalVentaId;

    /**
     * Folio legible por humanos (p. ej. {@code COT-2026-0001}), unico por tenant
     * (indice {@code uq_cotizacion_tenant_folio} de V60). Lo asigna la capa de
     * aplicacion al crear la Cotizacion via {@link #asignarFolio(String)}; en
     * filas historicas anteriores a V60 puede ser {@code null}.
     */
    @Column(name = "folio", updatable = false)
    private String folio;

    /** Fecha de emision (por defecto la fecha de creacion); opcional (V60). */
    @Column(name = "fecha_emision")
    private LocalDate fechaEmision;

    /**
     * Fecha de vigencia/expiracion; opcional. Si esta presente, debe ser
     * &ge; {@link #fechaEmision} (validado al asignar los datos descriptivos, V60).
     */
    @Column(name = "valido_hasta")
    private LocalDate validoHasta;

    /** Terminos y condiciones; texto libre opcional (max. 2000, V60). */
    @Column(name = "condiciones")
    private String condiciones;

    /** Notas libres; texto opcional (max. 2000, V60). */
    @Column(name = "notas")
    private String notas;

    /** Moneda de la Cotizacion (ISO 4217); por defecto {@code MXN} (V60). */
    @Column(name = "moneda")
    private String moneda;

    /** Marca temporal (UTC) del ultimo envio por correo; {@code null} si nunca (V60). */
    @Column(name = "enviada_en")
    private Instant enviadaEn;

    /**
     * Partidas de la Cotizacion (relacion uno-a-muchos, hijos del agregado). La FK
     * {@code partida_cotizacion.cotizacion_id} es NOT NULL con ON DELETE CASCADE en
     * V14; JPA persiste/elimina las partidas junto con la Cotizacion.
     */
    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PartidaCotizacion> partidas = new ArrayList<>();

    protected Cotizacion() {
        // Requerido por JPA.
    }

    /**
     * Crea una Cotizacion en estado inicial {@code borrador} con entre 1 y 500
     * partidas (Req 6.1, 6.2), calculando el total como {@code round(Σ subtotales,
     * 2)} (Req 6.5; Property 2). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4). La existencia del Cliente
     * la verifica la capa de aplicacion antes de invocar este metodo.
     *
     * @param clienteId identificador del Cliente existente; obligatorio.
     * @param partidas  partidas iniciales; entre 1 y 500 (Req 6.1, 6.2).
     * @param actor     identificador de quien crea, para {@code created_by}/
     *                  {@code updated_by}.
     * @return la Cotizacion lista para persistir, en {@code borrador} y con total.
     * @throws ReglaNegocioException si falta el Cliente, o el numero de partidas
     *         esta fuera de [1, 500] (422, Req 6.1, 6.2).
     */
    public static Cotizacion crear(UUID clienteId, List<PartidaCotizacion> partidas, String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException("La Cotizacion debe asociarse a un Cliente existente.");
        }
        if (partidas == null || partidas.size() < CotizacionValidaciones.PARTIDAS_MINIMAS) {
            throw new ReglaNegocioException(
                    "La Cotizacion debe tener al menos una Partida_Cotizacion.");
        }
        if (partidas.size() > CotizacionValidaciones.PARTIDAS_MAXIMAS) {
            throw new ReglaNegocioException(
                    "La Cotizacion no puede tener mas de "
                            + CotizacionValidaciones.PARTIDAS_MAXIMAS + " partidas.");
        }
        Cotizacion cotizacion = nuevaBorrador(clienteId, null, actor);
        for (PartidaCotizacion partida : partidas) {
            cotizacion.enlazar(partida);
        }
        cotizacion.recalcularTotales();
        return cotizacion;
    }

    /**
     * Crea un <strong>cascaron</strong> de Cotizacion en {@code borrador}
     * <em>sin partidas</em>, vinculado a la Oportunidad de origen y a su Cliente,
     * como resultado de convertir una Oportunidad ganada (Req 14.5). Las partidas
     * se agregan posteriormente via {@link #agregarPartida}. Los totales quedan en
     * cero hasta entonces; el envio exige >=1 partida (ver {@link #cambiarEstado}).
     *
     * @param oportunidadId Oportunidad de origen; obligatoria.
     * @param clienteId     Cliente de la Oportunidad; obligatorio.
     * @param actor         identificador de quien realiza la conversion.
     * @return el cascaron de Cotizacion en {@code borrador}, sin partidas.
     * @throws ReglaNegocioException si falta el Cliente o la Oportunidad (422).
     */
    public static Cotizacion crearCascaronConversion(UUID oportunidadId, UUID clienteId, String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException("La Cotizacion debe asociarse a un Cliente existente.");
        }
        if (oportunidadId == null) {
            throw new ReglaNegocioException("La Oportunidad de origen es obligatoria en la conversion.");
        }
        Cotizacion cotizacion = nuevaBorrador(clienteId, oportunidadId, actor);
        cotizacion.recalcularTotales();
        return cotizacion;
    }

    private static Cotizacion nuevaBorrador(UUID clienteId, UUID oportunidadId, String actor) {
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.id = UUID.randomUUID();
        cotizacion.clienteId = clienteId;
        cotizacion.oportunidadId = oportunidadId;
        cotizacion.estado = EstadoCotizacion.BORRADOR;
        BigDecimal cero = BigDecimal.ZERO.setScale(CotizacionValidaciones.ESCALA_MONETARIA);
        cotizacion.subtotal = cero;
        cotizacion.total = cero;
        cotizacion.descuentoGlobal = cero;
        cotizacion.iva = cero;
        cotizacion.retencionIsr = cero;
        cotizacion.retencionIva = cero;
        cotizacion.moneda = CotizacionValidaciones.MONEDA_POR_DEFECTO;
        cotizacion.partidas = new ArrayList<>();
        cotizacion.setCreatedBy(actor);
        cotizacion.setUpdatedBy(actor);
        return cotizacion;
    }

    /**
     * Asigna el folio legible por humanos (p. ej. {@code COT-2026-0001}) a la
     * Cotizacion recien creada (V60). Solo puede asignarse una vez: el folio es
     * permanente. La capa de aplicacion lo computa (consecutivo por tenant/anio)
     * antes de persistir.
     *
     * @param folio folio a asignar; obligatorio y no vacio.
     * @throws ReglaNegocioException si el folio es nulo/vacio, o si la Cotizacion
     *         ya tiene un folio asignado (422).
     */
    public void asignarFolio(String folio) {
        if (folio == null || folio.isBlank()) {
            throw new ReglaNegocioException("El folio de la Cotizacion es obligatorio.");
        }
        if (this.folio != null) {
            throw new ReglaNegocioException("El folio de la Cotizacion ya fue asignado y es permanente.");
        }
        this.folio = folio.strip();
    }

    /**
     * Aplica los datos descriptivos OPCIONALES de la Cotizacion (V60): fecha de
     * emision, fecha de vigencia, condiciones, notas y moneda. Normaliza y valida
     * las cotas; si {@code validoHasta} esta presente, debe ser &ge;
     * {@code fechaEmision} (de lo contrario 422). Un valor nulo deja el campo
     * como ausente; la moneda cae a {@code MXN} por defecto.
     *
     * @param fechaEmision fecha de emision; si {@code null}, se conserva la actual
     *                     (o queda ausente si aun no se fijo).
     * @param validoHasta  fecha de vigencia; opcional, &ge; fechaEmision si presente.
     * @param condiciones  terminos y condiciones; opcional (max. 2000).
     * @param notas        notas libres; opcional (max. 2000).
     * @param moneda       moneda ISO 4217; {@code null}/blanco usa {@code MXN}.
     * @param actor        identificador de quien realiza el cambio, para {@code updated_by}.
     * @throws ReglaNegocioException si algun texto excede su cota o
     *         {@code validoHasta} es anterior a {@code fechaEmision} (422).
     */
    public void aplicarDatosDescriptivos(LocalDate fechaEmision, LocalDate validoHasta,
                                         String condiciones, String notas, String moneda,
                                         String actor) {
        LocalDate emision = (fechaEmision != null) ? fechaEmision : this.fechaEmision;
        String nuevasCondiciones =
                CotizacionValidaciones.normalizarTextoLargoOpcional(condiciones, "condiciones");
        String nuevasNotas =
                CotizacionValidaciones.normalizarTextoLargoOpcional(notas, "notas");
        String nuevaMoneda = CotizacionValidaciones.normalizarMoneda(moneda);
        if (validoHasta != null && emision != null && validoHasta.isBefore(emision)) {
            throw new ReglaNegocioException(
                    "La fecha de vigencia (valido_hasta) no puede ser anterior a la fecha de emision.");
        }
        this.fechaEmision = emision;
        this.validoHasta = validoHasta;
        this.condiciones = nuevasCondiciones;
        this.notas = nuevasNotas;
        this.moneda = nuevaMoneda;
        this.setUpdatedBy(actor);
    }

    /**
     * Marca la Cotizacion como enviada por correo en el instante indicado (V60).
     *
     * @param instante marca temporal del envio; obligatoria.
     * @param actor    identificador de quien realiza el envio, para {@code updated_by}.
     * @throws ReglaNegocioException si el instante es nulo (422).
     */
    public void marcarEnviada(Instant instante, String actor) {
        if (instante == null) {
            throw new ReglaNegocioException("La marca temporal de envio es obligatoria.");
        }
        this.enviadaEn = instante;
        this.setUpdatedBy(actor);
    }

    /**
     * Agrega una partida a la Cotizacion y recalcula los totales, manteniendo el
     * invariante {@code total == round(Σ subtotales, 2)} (Req 6.5; Property 2).
     * Solo se permite mientras la Cotizacion esta en {@code borrador}: una vez
     * enviada/aprobada/rechazada, sus partidas no cambian.
     *
     * @param partida partida a agregar; obligatoria (ya validada en su fabrica).
     * @param actor   identificador de quien realiza el cambio, para {@code updated_by}.
     * @throws ReglaNegocioException si la partida es nula, si la Cotizacion no esta
     *         en {@code borrador}, o si se excede el maximo de 500 partidas (422).
     */
    public void agregarPartida(PartidaCotizacion partida, String actor) {
        if (partida == null) {
            throw new ReglaNegocioException("La partida es obligatoria.");
        }
        if (this.estado != EstadoCotizacion.BORRADOR) {
            throw new ReglaNegocioException(
                    "Solo se pueden agregar partidas a una Cotizacion en estado 'borrador'.");
        }
        if (this.partidas.size() >= CotizacionValidaciones.PARTIDAS_MAXIMAS) {
            throw new ReglaNegocioException(
                    "La Cotizacion no puede tener mas de "
                            + CotizacionValidaciones.PARTIDAS_MAXIMAS + " partidas.");
        }
        enlazar(partida);
        recalcularTotales();
        this.setUpdatedBy(actor);
    }

    private void enlazar(PartidaCotizacion partida) {
        if (partida == null) {
            throw new ReglaNegocioException("La partida es obligatoria.");
        }
        partida.asignarCotizacion(this);
        this.partidas.add(partida);
    }

    /**
     * Aplica el descuento global y las retenciones (ISR/IVA) de la Cotizacion y
     * recalcula el total (V80). Solo se permite mientras la Cotizacion esta en
     * {@code borrador} (una vez enviada/aprobada, los importes quedan fijos). El
     * descuento global se acota a la base disponible (no puede exceder el subtotal).
     *
     * @param descuentoGlobal descuento global (monto); {@code null}=0, no negativo.
     * @param retencionIsr    retencion de ISR (monto); {@code null}=0, no negativa.
     * @param retencionIva    retencion de IVA (monto); {@code null}=0, no negativa.
     * @param actor           identificador de quien edita, para {@code updated_by}.
     * @throws ReglaNegocioException si la Cotizacion no esta en {@code borrador},
     *         algun monto es negativo, o el descuento global excede el subtotal (422).
     */
    public void aplicarAjustesFiscales(BigDecimal descuentoGlobal, BigDecimal retencionIsr,
                                       BigDecimal retencionIva, String actor) {
        if (this.estado != EstadoCotizacion.BORRADOR) {
            throw new ReglaNegocioException(
                    "Solo se pueden ajustar impuestos y descuentos de una Cotizacion en 'borrador'.");
        }
        this.descuentoGlobal = CotizacionValidaciones.validarMontoFiscalNoNegativo(
                descuentoGlobal, "descuento global");
        this.retencionIsr = CotizacionValidaciones.validarMontoFiscalNoNegativo(
                retencionIsr, "retencion de ISR");
        this.retencionIva = CotizacionValidaciones.validarMontoFiscalNoNegativo(
                retencionIva, "retencion de IVA");
        recalcularTotales();
        this.setUpdatedBy(actor);
    }

    /**
     * Recalcula el desglose fiscal completo de la Cotizacion siguiendo el orden
     * CFDI (V80):
     * <ol>
     *   <li>{@code subtotal} = Σ base neta de partida (importe bruto - descuento
     *       de partida).</li>
     *   <li>{@code iva} = Σ IVA de partida (sobre su base neta, respetando tasas
     *       mixtas).</li>
     *   <li>base gravable = subtotal - descuento_global (acotado a &ge; 0).</li>
     *   <li>{@code total} = base gravable + iva - retencion_isr - retencion_iva
     *       (acotado a &ge; 0).</li>
     * </ol>
     * Todo a escala 2 half-up. Es idempotente y la unica fuente de verdad de los
     * importes derivados, de modo que subtotal/iva/total quedan siempre coherentes
     * con las partidas y los ajustes fiscales.
     *
     * <p>Si el descuento global captado excede el subtotal (p. ej. tras quitar
     * partidas en una futura edicion), se acota al subtotal para no producir una
     * base negativa; el dominio no falla por ello en el recalculo.</p>
     */
    private void recalcularTotales() {
        BigDecimal sumaBases = BigDecimal.ZERO;
        BigDecimal sumaIva = BigDecimal.ZERO;
        for (PartidaCotizacion partida : this.partidas) {
            sumaBases = sumaBases.add(partida.getSubtotal());
            sumaIva = sumaIva.add(partida.getIva());
        }
        this.subtotal = CotizacionValidaciones.normalizarMonto(sumaBases);
        this.iva = CotizacionValidaciones.normalizarMonto(sumaIva);

        // El descuento global no puede dejar una base gravable negativa: se acota
        // al subtotal disponible (defensa; la captura tambien se valida en la UI).
        BigDecimal descuentoAplicado = this.descuentoGlobal.min(this.subtotal);
        BigDecimal baseGravable = CotizacionValidaciones.normalizarMonto(
                this.subtotal.subtract(descuentoAplicado));

        BigDecimal totalCalculado = baseGravable
                .add(this.iva)
                .subtract(this.retencionIsr)
                .subtract(this.retencionIva);
        // Un total negativo (retenciones mayores que base+iva) se acota a 0 para
        // respetar el CHECK ck_cotizacion_total_no_negativo de V14.
        if (totalCalculado.signum() < 0) {
            totalCalculado = BigDecimal.ZERO;
        }
        this.total = CotizacionValidaciones.normalizarMonto(totalCalculado);
    }

    /**
     * Cambia el estado de la Cotizacion aplicando la maquina de estados pura
     * (Req 6.6, 6.7). Solo permite las transiciones definidas; toda transicion no
     * permitida —incluida cualquiera que parta de un estado final— se rechaza con
     * {@link TransicionInvalidaException} (409) y el estado actual se conserva
     * (Req 6.7). La transicion {@code borrador -> enviada} exige al menos una
     * partida.
     *
     * @param nuevoEstado estado destino; obligatorio.
     * @param actor       identificador de quien realiza el cambio, para
     *                    {@code updated_by}.
     * @throws ReglaNegocioException        si {@code nuevoEstado} es nulo, o si se
     *         intenta enviar una Cotizacion sin partidas (422).
     * @throws TransicionInvalidaException  si la transicion no esta permitida (409).
     */
    public void cambiarEstado(EstadoCotizacion nuevoEstado, String actor) {
        if (nuevoEstado == null) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(nuevoEstado)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + nuevoEstado.valorBd() + "'.");
        }
        if (nuevoEstado == EstadoCotizacion.ENVIADA && this.partidas.isEmpty()) {
            throw new ReglaNegocioException(
                    "No se puede enviar una Cotizacion sin al menos una Partida_Cotizacion.");
        }
        this.estado = nuevoEstado;
        this.setUpdatedBy(actor);
    }

    /**
     * Clasifica (asigna o modifica) el canal de venta de la Cotizacion, o lo
     * limpia (Req 63.1). La clasificacion es <strong>opcional</strong>: un
     * {@code canalVentaId} nulo <em>desclasifica</em> la Cotizacion (deja el canal
     * en {@code null}). La existencia del canal en el tenant la verifica la capa
     * de aplicacion antes de invocar este metodo. La auditoria de la
     * asignacion/modificacion la realiza la capa de aplicacion (Req 63.3). No
     * depende del estado de la Cotizacion: la clasificacion comercial es
     * ortogonal a la maquina de estados.
     *
     * @param canalVentaId identificador del Canal_Venta; {@code null} para limpiar
     *                     la clasificacion.
     * @param actor        identificador de quien realiza la asignacion, para
     *                     {@code updated_by}.
     */
    public void asignarCanalVenta(UUID canalVentaId, String actor) {
        this.canalVentaId = canalVentaId;
        this.setUpdatedBy(actor);
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

    public EstadoCotizacion getEstado() {
        return estado;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    /** Descuento global (monto) restado del subtotal (V80). */
    public BigDecimal getDescuentoGlobal() {
        return descuentoGlobal;
    }

    /** IVA consolidado de la Cotizacion (suma del IVA de las partidas) (V80). */
    public BigDecimal getIva() {
        return iva;
    }

    /** Retencion de ISR (monto) restada del total (V80). */
    public BigDecimal getRetencionIsr() {
        return retencionIsr;
    }

    /** Retencion de IVA (monto) restada del total (V80). */
    public BigDecimal getRetencionIva() {
        return retencionIva;
    }

    public UUID getCanalVentaId() {
        return canalVentaId;
    }

    public String getFolio() {
        return folio;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public LocalDate getValidoHasta() {
        return validoHasta;
    }

    /**
     * Indica si la Cotizacion esta VENCIDA respecto a la fecha dada (Req 6.11). Una
     * Cotizacion SIN fecha de vigencia ({@code validoHasta == null}) NUNCA vence.
     * Con fecha, se considera vencida a partir del dia SIGUIENTE al de vigencia: el
     * propio dia de {@code validoHasta} sigue siendo valido (frontera inclusiva),
     * coherente con {@code PermisoInstalacion.estaVigente} y {@code Lote.estaCaducado}.
     *
     * <p>Es una funcion de dominio PURA sobre {@link LocalDate}; la capa de
     * aplicacion calcula {@code hoy} con el {@link java.time.Clock} inyectado (UTC),
     * para ser determinista en pruebas. Sustenta la guarda de negocio que impide
     * enviar o aprobar una Cotizacion vencida.</p>
     *
     * @param hoy fecha de referencia (normalmente hoy en UTC); obligatoria.
     * @return {@code true} si la Cotizacion tiene vigencia y esta ya vencida antes de {@code hoy}.
     * @throws ReglaNegocioException si {@code hoy} es nulo (422).
     */
    public boolean estaVencida(LocalDate hoy) {
        if (hoy == null) {
            throw new ReglaNegocioException(
                    "La fecha de referencia para evaluar la vigencia es obligatoria.");
        }
        return validoHasta != null && hoy.isAfter(validoHasta);
    }

    public String getCondiciones() {
        return condiciones;
    }

    public String getNotas() {
        return notas;
    }

    public String getMoneda() {
        return moneda;
    }

    public Instant getEnviadaEn() {
        return enviadaEn;
    }

    /**
     * Vista de solo lectura de las partidas de la Cotizacion.
     *
     * @return lista inmutable de partidas.
     */
    public List<PartidaCotizacion> getPartidas() {
        return Collections.unmodifiableList(partidas);
    }
}
