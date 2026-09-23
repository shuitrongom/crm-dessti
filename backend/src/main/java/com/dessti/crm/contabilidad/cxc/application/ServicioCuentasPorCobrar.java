package com.dessti.crm.contabilidad.cxc.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.cxc.adapter.out.persistence.AplicacionPagoRepository;
import com.dessti.crm.contabilidad.cxc.adapter.out.persistence.CuentaPorCobrarRepository;
import com.dessti.crm.contabilidad.cxc.adapter.out.persistence.PagoClienteRepository;
import com.dessti.crm.contabilidad.cxc.domain.AplicacionPago;
import com.dessti.crm.contabilidad.cxc.domain.CuentaPorCobrar;
import com.dessti.crm.contabilidad.cxc.domain.EstadoCuentaPorCobrar;
import com.dessti.crm.contabilidad.cxc.domain.PagoCliente;
import com.dessti.crm.facturacion.application.PacPort;
import com.dessti.crm.facturacion.application.ResultadoTimbrado;
import com.dessti.crm.facturacion.application.SolicitudTimbrado;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna las Cuentas_Por_Cobrar (CxC), los Pagos de
 * Cliente y el Complemento de Pago (Req 36, 37). Implementa
 * {@link CuentaPorCobrarPort}, la frontera hexagonal que facturacion invoca para
 * cerrar la integracion con CxC. Replica el patron de
 * {@code ServicioOrdenesFabricacion}/{@code ServicioNotasCredito}.
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>registrarPorFacturaTimbrada (Req 36.1):</strong> al timbrar una
 *       Factura, crea su CxC por el saldo = total. Idempotente: si ya existe una
 *       CxC para esa Factura, no hace nada.</li>
 *   <li><strong>registrarPago (Req 36.2, 36.3, 36.4; Property 13):</strong>
 *       registra un Pago_Cliente y lo aplica a una o varias Facturas; cada
 *       aplicacion disminuye el saldo de la CxC acotada por el saldo (rechazo 422
 *       con excedente si excede, conservando saldos — operacion atomica). Si es
 *       parcialidad/diferido, genera y timbra un Complemento_Pago via el PAC.
 *       Audita el registro y las aplicaciones.</li>
 *   <li><strong>disminuirPorNotaCredito (Req 37.1):</strong> reduce el saldo de la
 *       CxC de una Factura por el monto de una Nota de Credito emitida.</li>
 *   <li><strong>consultarAging (Req 36.5):</strong> antiguedad de saldos por
 *       Cliente clasificada por rango de dias de vencimiento (solo lectura).</li>
 *   <li><strong>consultar / listar (Req 23.3, 36.6):</strong> 404 + auditoria del
 *       intento si no es accesible; listados paginados (20/100) con filtros.</li>
 * </ul>
 *
 * <h2>Complemento de Pago rechazado (Req 36.4, decision)</h2>
 * <p>Si el PAC rechaza el Timbrado del Complemento_Pago de una parcialidad, la
 * operacion completa se <strong>rechaza con 422</strong> (se revierte la
 * transaccion, conservando saldos) y se audita el rechazo, de modo coherente con el
 * tratamiento del rechazo del PAC en Factura/Nota de Credito (Req 35.2).</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 36.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se audita via
 * {@link AuditoriaPort}. El {@link Clock} inyectado hace deterministas los calculos
 * de antiguedad (Req 36.5).</p>
 */
@Service
public class ServicioCuentasPorCobrar implements CuentaPorCobrarPort {

    /** Tipo de recurso de auditoria/RBAC del Pago_Cliente. */
    static final String RECURSO_PAGO_CLIENTE = "pago_cliente";

    /** Tipo de recurso de auditoria/RBAC de la Cuenta_Por_Cobrar. */
    static final String RECURSO_CXC = "cuenta_por_cobrar";

    /** Escala monetaria coherente con NUMERIC(18,2) de V31. */
    private static final int ESCALA_MONETARIA = 2;

    private final CuentaPorCobrarRepository cuentaPorCobrarRepository;
    private final PagoClienteRepository pagoClienteRepository;
    private final AplicacionPagoRepository aplicacionPagoRepository;
    private final PacPort pac;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioCuentasPorCobrar(CuentaPorCobrarRepository cuentaPorCobrarRepository,
                                    PagoClienteRepository pagoClienteRepository,
                                    AplicacionPagoRepository aplicacionPagoRepository,
                                    PacPort pac,
                                    AuditoriaPort auditoria,
                                    Clock clock) {
        this.cuentaPorCobrarRepository = cuentaPorCobrarRepository;
        this.pagoClienteRepository = pagoClienteRepository;
        this.aplicacionPagoRepository = aplicacionPagoRepository;
        this.pac = pac;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // CuentaPorCobrarPort (integracion con facturacion)
    // ------------------------------------------------------------------

    /**
     * Registra la Cuenta_Por_Cobrar de una Factura recien timbrada con saldo = total
     * (Req 36.1). Idempotente: si ya existe una CxC para la Factura en el tenant, no
     * crea otra.
     */
    @Override
    @Transactional
    public void registrarPorFacturaTimbrada(UUID facturaId, UUID clienteId, BigDecimal total) {
        if (facturaId == null) {
            return;
        }
        if (cuentaPorCobrarRepository.existsByFacturaId(facturaId)) {
            return; // Idempotencia (Req 36.1).
        }
        String actor = actorActual();
        CuentaPorCobrar cxc = CuentaPorCobrar.paraFactura(facturaId, clienteId, total, actor);
        CuentaPorCobrar guardada = cuentaPorCobrarRepository.save(cxc);
        auditar(actor, "crear", RECURSO_CXC, guardada.getId(),
                "registrada Cuenta_Por_Cobrar por factura timbrada [factura=" + facturaId
                        + ", saldo=" + guardada.getSaldo().toPlainString() + "]", null, null);
    }

    /**
     * Disminuye el saldo de la CxC de una Factura por el monto de una Nota de
     * Credito emitida (Req 37.1). Si no existe CxC para la Factura, no tiene efecto.
     */
    @Override
    @Transactional
    public void disminuirPorNotaCredito(UUID facturaId, BigDecimal monto) {
        if (facturaId == null || monto == null || monto.signum() <= 0) {
            return;
        }
        cuentaPorCobrarRepository.findByFacturaId(facturaId).ifPresent(cxc -> {
            String actor = actorActual();
            BigDecimal saldoAnterior = cxc.getSaldo();
            EstadoCuentaPorCobrar estadoAnterior = cxc.getEstado();
            cxc.disminuir(monto, actor);
            CuentaPorCobrar guardada = cuentaPorCobrarRepository.save(cxc);
            auditar(actor, "disminuir_nota_credito", RECURSO_CXC, guardada.getId(),
                    "disminuida CxC por nota de credito [factura=" + facturaId + ", monto="
                            + monto.toPlainString() + ", saldo=" + saldoAnterior.toPlainString()
                            + "->" + guardada.getSaldo().toPlainString() + "]",
                    estadoAnterior.valorBd(), guardada.getEstado().valorBd());
        });
    }

    // ------------------------------------------------------------------
    // Pago de Cliente (Req 36.2, 36.3, 36.4)
    // ------------------------------------------------------------------

    /**
     * Registra un Pago_Cliente y lo aplica a una o varias Facturas (Req 36.2, 36.3,
     * 36.4; Property 13). Cada aplicacion disminuye el saldo de la CxC de la Factura
     * <em>acotado por el saldo</em>: si el monto excede el saldo pendiente, se
     * rechaza con 422 informando el excedente y, por ser una unica transaccion, se
     * conservan todos los saldos. Si el pago es parcialidad/diferido, se genera y
     * timbra un Complemento_Pago via el PAC (rechazo del PAC -> 422). Audita.
     *
     * @param comando datos del pago y su desglose de aplicaciones.
     * @return el DTO del Pago_Cliente registrado con sus aplicaciones.
     * @throws RecursoNoEncontradoException si alguna Factura no tiene CxC accesible (404).
     * @throws ReglaNegocioException si el pago o las aplicaciones son invalidos, un
     *         monto excede el saldo (Req 36.3) o el PAC rechaza el complemento (422).
     */
    @Transactional
    public PagoClienteDto registrarPago(RegistrarPagoClienteCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.clienteId() == null) {
            throw new ReglaNegocioException("El Pago_Cliente debe registrar el Cliente.");
        }
        if (comando.aplicaciones() == null || comando.aplicaciones().isEmpty()) {
            throw new ReglaNegocioException(
                    "El Pago_Cliente debe aplicarse al menos a una Factura.");
        }

        BigDecimal sumaAplicaciones = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        for (AplicacionPagoCommand aplicacion : comando.aplicaciones()) {
            if (aplicacion == null || aplicacion.facturaId() == null) {
                throw new ReglaNegocioException(
                        "Cada aplicacion de pago debe referenciar una Factura.");
            }
            if (aplicacion.monto() == null || aplicacion.monto().signum() <= 0) {
                throw new ReglaNegocioException(
                        "El monto de cada aplicacion de pago debe ser positivo.");
            }
            sumaAplicaciones = sumaAplicaciones.add(
                    aplicacion.monto().setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP));
        }
        BigDecimal montoPago = comando.monto() == null ? null
                : comando.monto().setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (montoPago == null || montoPago.signum() <= 0) {
            throw new ReglaNegocioException("El monto del Pago_Cliente debe ser positivo.");
        }
        if (sumaAplicaciones.compareTo(montoPago) > 0) {
            throw new ReglaNegocioException(
                    "la suma de las aplicaciones (" + sumaAplicaciones.toPlainString()
                            + ") excede el monto del pago (" + montoPago.toPlainString() + ").");
        }

        PagoCliente pago = PagoCliente.registrar(
                comando.clienteId(), comando.monto(), comando.formaPago(),
                comando.esParcialidad(), actor);
        PagoCliente pagoGuardado = pagoClienteRepository.save(pago);

        List<AplicacionPago> aplicaciones = new ArrayList<>();
        for (AplicacionPagoCommand linea : comando.aplicaciones()) {
            CuentaPorCobrar cxc = cargarCxCPorFactura(linea.facturaId(), actor);
            BigDecimal saldoAnterior = cxc.getSaldo();
            EstadoCuentaPorCobrar estadoAnterior = cxc.getEstado();
            // Property 13 / Req 36.3: aplicacion acotada por el saldo (422 si excede,
            // saldos conservados por ser transaccion unica).
            cxc.aplicarPago(linea.monto(), actor);
            CuentaPorCobrar cxcGuardada = cuentaPorCobrarRepository.save(cxc);

            AplicacionPago aplicacion = AplicacionPago.de(
                    pagoGuardado.getId(), cxcGuardada.getId(), linea.facturaId(),
                    linea.monto(), actor);
            aplicaciones.add(aplicacionPagoRepository.save(aplicacion));

            auditar(actor, "aplicar", RECURSO_PAGO_CLIENTE, pagoGuardado.getId(),
                    "aplicado pago a factura [factura=" + linea.facturaId() + ", monto="
                            + aplicacion.getMontoAplicado().toPlainString() + ", saldo="
                            + saldoAnterior.toPlainString() + "->"
                            + cxcGuardada.getSaldo().toPlainString() + "]",
                    estadoAnterior.valorBd(), cxcGuardada.getEstado().valorBd());
        }

        if (comando.esParcialidad()) {
            timbrarComplementoPago(pagoGuardado, actor);
            pagoGuardado = pagoClienteRepository.save(pagoGuardado);
        }

        auditar(actor, "crear", RECURSO_PAGO_CLIENTE, pagoGuardado.getId(),
                "registrado Pago_Cliente [cliente=" + pagoGuardado.getClienteId() + ", monto="
                        + pagoGuardado.getMonto().toPlainString() + ", parcialidad="
                        + pagoGuardado.isEsParcialidad() + "]", null, null);
        return PagoClienteDto.de(pagoGuardado, aplicaciones);
    }

    /**
     * Solicita al PAC el Timbrado del Complemento_Pago (CFDI tipo pago) de una
     * parcialidad/diferido y registra folio/sello/fecha en el Pago_Cliente
     * (Req 36.4). Si el PAC rechaza, lanza 422 (revierte la transaccion) y audita.
     */
    private void timbrarComplementoPago(PagoCliente pago, String actor) {
        SolicitudTimbrado solicitud = new SolicitudTimbrado(
                TenantContext.require(), pago.getId(),
                "XAXX010101000", "Complemento de Pago", "00000", "601", "CP01",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, pago.getMonto());
        ResultadoTimbrado resultado = pac.timbrar(solicitud);
        if (!resultado.exito()) {
            auditar(actor, "complemento_rechazado", RECURSO_PAGO_CLIENTE, pago.getId(),
                    "el PAC rechazo el timbrado del complemento de pago: " + resultado.mensajeError(),
                    null, null);
            throw new ReglaNegocioException(
                    "El PAC rechazo el timbrado del Complemento_Pago: " + resultado.mensajeError());
        }
        pago.registrarComplemento(resultado.folioFiscal(), resultado.selloSat(),
                resultado.fechaTimbrado(), actor);
        auditar(actor, "complemento_timbrado", RECURSO_PAGO_CLIENTE, pago.getId(),
                "complemento de pago timbrado [folio_fiscal=" + resultado.folioFiscal() + "]",
                null, null);
    }

    /**
     * Consulta puntual de un Pago_Cliente del tenant con su desglose (Req 23.3).
     *
     * @param pagoId identificador del Pago_Cliente.
     * @return el DTO del Pago_Cliente con sus aplicaciones.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public PagoClienteDto consultarPago(UUID pagoId) {
        String actor = actorActual();
        PagoCliente pago = cargarPago(pagoId, actor);
        return PagoClienteDto.de(pago, aplicacionPagoRepository.findByPagoClienteId(pago.getId()));
    }

    /**
     * Listado paginado de Pagos de Cliente del tenant con filtro opcional por
     * Cliente (Req 36.6).
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Pagos de Cliente como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<PagoClienteDto> listarPagos(UUID clienteId, Pageable pageable) {
        return pagoClienteRepository.buscarConFiltros(clienteId, pageable)
                .map(pago -> PagoClienteDto.de(
                        pago, aplicacionPagoRepository.findByPagoClienteId(pago.getId())));
    }

    // ------------------------------------------------------------------
    // Cuenta_Por_Cobrar (Req 36.5, 36.6)
    // ------------------------------------------------------------------

    /**
     * Consulta puntual de una Cuenta_Por_Cobrar del tenant (Req 23.3).
     *
     * @param cxcId identificador de la CxC.
     * @return el DTO de la CxC.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public CuentaPorCobrarDto consultarCxC(UUID cxcId) {
        String actor = actorActual();
        return CuentaPorCobrarDto.de(cargarCxC(cxcId, actor));
    }

    /**
     * Listado paginado de Cuentas_Por_Cobrar del tenant con filtros opcionales por
     * Cliente y por estado (Req 36.6).
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param estado    etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de CxC como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<CuentaPorCobrarDto> listarCxC(UUID clienteId, String estado, Pageable pageable) {
        EstadoCuentaPorCobrar filtro =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return cuentaPorCobrarRepository.buscarConFiltros(clienteId, filtro, pageable)
                .map(CuentaPorCobrarDto::de);
    }

    /**
     * Calcula la antiguedad de saldos (aging) de las CxC del tenant con saldo
     * pendiente, agrupada por Cliente y clasificada por rango de dias de vencimiento
     * (Req 36.5). Solo lectura: no muta ninguna CxC. Los dias se miden con el
     * {@link Clock} inyectado desde la fecha de vencimiento (o la de emision cuando
     * no hay vencimiento).
     *
     * @param clienteId Cliente a filtrar; {@code null} incluye a todos.
     * @return el reporte de antiguedad de saldos por Cliente.
     */
    @Transactional(readOnly = true)
    public AntiguedadSaldosDto consultarAging(UUID clienteId) {
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<CuentaPorCobrar> pendientes =
                cuentaPorCobrarRepository.buscarPendientesParaAging(clienteId);

        Map<UUID, BigDecimal[]> acumulado = new LinkedHashMap<>();
        for (CuentaPorCobrar cxc : pendientes) {
            long dias = diasAntiguedad(cxc, hoy);
            int bucket = indiceRango(dias);
            BigDecimal[] rangos = acumulado.computeIfAbsent(
                    cxc.getClienteId(), k -> nuevoAcumulador());
            rangos[bucket] = rangos[bucket].add(cxc.getSaldo());
        }

        List<AntiguedadSaldosDto.RenglonCliente> renglones = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal[]> entrada : acumulado.entrySet()) {
            BigDecimal[] r = entrada.getValue();
            BigDecimal total = r[0].add(r[1]).add(r[2]).add(r[3]);
            renglones.add(new AntiguedadSaldosDto.RenglonCliente(
                    entrada.getKey(), total, r[0], r[1], r[2], r[3]));
        }
        return new AntiguedadSaldosDto(renglones);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private BigDecimal[] nuevoAcumulador() {
        BigDecimal cero = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return new BigDecimal[] {cero, cero, cero, cero};
    }

    /**
     * Dias de antiguedad de una CxC respecto a {@code hoy}: dias transcurridos desde
     * su fecha de vencimiento, o desde su fecha de emision si no tiene vencimiento.
     * Nunca negativo (una CxC aun no vencida se clasifica en el rango 0-30).
     */
    private long diasAntiguedad(CuentaPorCobrar cxc, LocalDate hoy) {
        LocalDate referencia = (cxc.getFechaVencimiento() != null)
                ? cxc.getFechaVencimiento()
                : LocalDate.ofInstant(cxc.getFechaEmision(), ZoneOffset.UTC);
        long dias = ChronoUnit.DAYS.between(referencia, hoy);
        return Math.max(0L, dias);
    }

    /** Rango de aging: 0 (0-30), 1 (31-60), 2 (61-90), 3 (&gt;90). */
    private int indiceRango(long dias) {
        if (dias <= 30) {
            return 0;
        }
        if (dias <= 60) {
            return 1;
        }
        if (dias <= 90) {
            return 2;
        }
        return 3;
    }

    private CuentaPorCobrar cargarCxC(UUID cxcId, String actor) {
        if (cxcId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Cuenta_Por_Cobrar solicitada.");
        }
        return cuentaPorCobrarRepository.findById(cxcId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CXC, cxcId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Cuenta_Por_Cobrar solicitada.");
                });
    }

    private CuentaPorCobrar cargarCxCPorFactura(UUID facturaId, String actor) {
        return cuentaPorCobrarRepository.findByFacturaId(facturaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CXC, facturaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro una Cuenta_Por_Cobrar para la Factura indicada.");
                });
    }

    private PagoCliente cargarPago(UUID pagoId, String actor) {
        if (pagoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Pago_Cliente solicitado.");
        }
        return pagoClienteRepository.findById(pagoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_PAGO_CLIENTE, pagoId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el Pago_Cliente solicitado.");
                });
    }

    private EstadoCuentaPorCobrar interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado de la Cuenta_Por_Cobrar es obligatorio.");
        }
        try {
            return EstadoCuentaPorCobrar.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Cuenta_Por_Cobrar desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, String recurso, UUID recursoId,
                         String detalle, String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, recurso,
                detalle + " [id=" + recursoId + "]", valorAnterior, valorNuevo));
    }

    private void auditarAccesoCruzado(String actor, String recurso, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", recurso,
                "intento de acceso a " + recurso + " no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
