package com.dessti.crm.contabilidad.cxp.application;

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

import com.dessti.crm.contabilidad.cxp.adapter.out.persistence.CuentaPorPagarRepository;
import com.dessti.crm.contabilidad.cxp.adapter.out.persistence.ProgramacionPagoRepository;
import com.dessti.crm.contabilidad.cxp.domain.CuentaPorPagar;
import com.dessti.crm.contabilidad.cxp.domain.EstadoCuentaPorPagar;
import com.dessti.crm.contabilidad.cxp.domain.ProgramacionPago;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna las Cuentas_Por_Pagar (CxP) y las
 * Programaciones de Pago (Req 42). Implementa {@link CuentaPorPagarPort}, la
 * frontera hexagonal que compras invoca al conciliar una Factura_Proveedor.
 * Es la imagen espejo de {@code ServicioCuentasPorCobrar}.
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>registrarPorFacturaProveedorConciliada (Req 42.1):</strong> al
 *       conciliar una Factura_Proveedor, crea su CxP por el saldo = total.
 *       Idempotente: si ya existe una CxP para esa factura, no hace nada.</li>
 *   <li><strong>crearProgramacionPago (Req 42.2):</strong> registra la fecha
 *       programada y el monto de un pago por CxP; audita.</li>
 *   <li><strong>aplicarPago (Req 42.3, 42.4; Property 15):</strong> aplica un pago a
 *       una CxP disminuyendo el saldo acotado por el saldo (rechazo 422 con el
 *       excedente si excede, conservando el saldo). Cuando el saldo llega a 0, marca
 *       la Factura_Proveedor asociada como pagada via
 *       {@link FacturaProveedorPagablePort} (Req 42.3). Audita.</li>
 *   <li><strong>consultarAging (Req 42.5):</strong> antiguedad de saldos por
 *       Proveedor clasificada por rango de dias de vencimiento (solo lectura).</li>
 *   <li><strong>consultar / listar (Req 23.3, 42.6):</strong> 404 + auditoria del
 *       intento si no es accesible; listados paginados (20/100) con filtros.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 42.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la peticion,
 * Req 23.4). Cada operacion relevante se audita via {@link AuditoriaPort}. El
 * {@link Clock} inyectado hace deterministas los calculos de antiguedad (Req 42.5).</p>
 */
@Service
public class ServicioCuentasPorPagar implements CuentaPorPagarPort {

    /** Tipo de recurso de auditoria/RBAC de la Cuenta_Por_Pagar. */
    static final String RECURSO_CXP = "cuenta_por_pagar";

    /** Tipo de recurso de auditoria/RBAC de la Programacion_Pago. */
    static final String RECURSO_PROGRAMACION = "programacion_pago";

    /** Escala monetaria coherente con NUMERIC(18,2) de V33. */
    private static final int ESCALA_MONETARIA = 2;

    private final CuentaPorPagarRepository cuentaPorPagarRepository;
    private final ProgramacionPagoRepository programacionPagoRepository;
    private final FacturaProveedorPagablePort facturaProveedorPagable;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioCuentasPorPagar(CuentaPorPagarRepository cuentaPorPagarRepository,
                                   ProgramacionPagoRepository programacionPagoRepository,
                                   FacturaProveedorPagablePort facturaProveedorPagable,
                                   AuditoriaPort auditoria,
                                   Clock clock) {
        this.cuentaPorPagarRepository = cuentaPorPagarRepository;
        this.programacionPagoRepository = programacionPagoRepository;
        this.facturaProveedorPagable = facturaProveedorPagable;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // CuentaPorPagarPort (integracion con compras)
    // ------------------------------------------------------------------

    /**
     * Registra la Cuenta_Por_Pagar de una Factura_Proveedor recien conciliada con
     * saldo = total (Req 42.1). Idempotente: si ya existe una CxP para la factura en
     * el tenant, no crea otra.
     */
    @Override
    @Transactional
    public void registrarPorFacturaProveedorConciliada(UUID facturaProveedorId, UUID proveedorId,
                                                        BigDecimal saldo) {
        if (facturaProveedorId == null) {
            return;
        }
        if (cuentaPorPagarRepository.existsByFacturaProveedorId(facturaProveedorId)) {
            return; // Idempotencia (Req 42.1).
        }
        String actor = actorActual();
        CuentaPorPagar cxp = CuentaPorPagar.paraFacturaProveedor(
                facturaProveedorId, proveedorId, saldo, actor);
        CuentaPorPagar guardada = cuentaPorPagarRepository.save(cxp);
        auditar(actor, "crear", RECURSO_CXP, guardada.getId(),
                "registrada Cuenta_Por_Pagar por factura_proveedor conciliada [factura_proveedor="
                        + facturaProveedorId + ", saldo=" + guardada.getSaldo().toPlainString() + "]",
                null, null);
    }

    // ------------------------------------------------------------------
    // Programacion_Pago (Req 42.2) y aplicacion de pago (Req 42.3, 42.4)
    // ------------------------------------------------------------------

    /**
     * Crea una Programacion_Pago (fecha + monto) para una Cuenta_Por_Pagar (Req 42.2).
     *
     * @param comando datos de la programacion.
     * @return el DTO de la Programacion_Pago creada.
     * @throws RecursoNoEncontradoException si la CxP no es accesible (404).
     * @throws ReglaNegocioException si faltan datos o el monto no es positivo (422).
     */
    @Transactional
    public ProgramacionPagoDto crearProgramacionPago(CrearProgramacionPagoCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.cuentaPorPagarId() == null) {
            throw new ReglaNegocioException(
                    "La Programacion_Pago debe referenciar una Cuenta_Por_Pagar.");
        }
        CuentaPorPagar cxp = cargarCxP(comando.cuentaPorPagarId(), actor);
        ProgramacionPago programacion = ProgramacionPago.crear(
                cxp.getId(), comando.fechaProgramada(), comando.monto(), actor);
        ProgramacionPago guardada = programacionPagoRepository.save(programacion);
        auditar(actor, "crear", RECURSO_PROGRAMACION, guardada.getId(),
                "registrada Programacion_Pago [cuenta_por_pagar=" + cxp.getId() + ", fecha="
                        + guardada.getFechaProgramada() + ", monto="
                        + guardada.getMonto().toPlainString() + "]", null, null);
        return ProgramacionPagoDto.de(guardada);
    }

    /**
     * Aplica un pago a una Cuenta_Por_Pagar, <strong>acotado por el saldo</strong>
     * (Req 42.3, 42.4; Property 15). Si el monto excede el saldo pendiente, se
     * rechaza con 422 informando el excedente y la CxP se conserva sin cambios. En
     * otro caso disminuye el saldo y, cuando el saldo llega a 0, marca la
     * Factura_Proveedor asociada como pagada (Req 42.3). Audita.
     *
     * @param cuentaPorPagarId identificador de la CxP a la que se aplica el pago.
     * @param monto            monto a aplicar; positivo y {@code <= saldo}.
     * @return el DTO de la CxP con su nuevo saldo y estado.
     * @throws RecursoNoEncontradoException si la CxP no es accesible (404).
     * @throws ReglaNegocioException si el monto no es positivo o excede el saldo (422).
     */
    @Transactional
    public CuentaPorPagarDto aplicarPago(UUID cuentaPorPagarId, BigDecimal monto) {
        String actor = actorActual();
        CuentaPorPagar cxp = cargarCxP(cuentaPorPagarId, actor);
        BigDecimal saldoAnterior = cxp.getSaldo();
        EstadoCuentaPorPagar estadoAnterior = cxp.getEstado();
        // Property 15 / Req 42.4: aplicacion acotada por el saldo (422 si excede,
        // saldo conservado por no mutar la entidad).
        cxp.aplicarPago(monto, actor);
        CuentaPorPagar guardada = cuentaPorPagarRepository.save(cxp);

        auditar(actor, "aplicar_pago", RECURSO_CXP, guardada.getId(),
                "aplicado pago a Cuenta_Por_Pagar [monto="
                        + monto.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP).toPlainString()
                        + ", saldo=" + saldoAnterior.toPlainString() + "->"
                        + guardada.getSaldo().toPlainString() + "]",
                estadoAnterior.valorBd(), guardada.getEstado().valorBd());

        // Req 42.3: al liquidar la CxP (saldo == 0), marcar la Factura_Proveedor pagada.
        if (guardada.quedoLiquidada()) {
            facturaProveedorPagable.marcarPagada(guardada.getFacturaProveedorId());
            auditar(actor, "liquidar", RECURSO_CXP, guardada.getId(),
                    "Cuenta_Por_Pagar liquidada; Factura_Proveedor marcada como pagada"
                            + " [factura_proveedor=" + guardada.getFacturaProveedorId() + "]",
                    null, null);
        }
        return CuentaPorPagarDto.de(guardada);
    }

    // ------------------------------------------------------------------
    // Consultas (Req 42.5, 42.6, 23.3)
    // ------------------------------------------------------------------

    /**
     * Consulta puntual de una Cuenta_Por_Pagar del tenant (Req 23.3).
     *
     * @param cxpId identificador de la CxP.
     * @return el DTO de la CxP.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public CuentaPorPagarDto consultarCxP(UUID cxpId) {
        String actor = actorActual();
        return CuentaPorPagarDto.de(cargarCxP(cxpId, actor));
    }

    /**
     * Listado paginado de Cuentas_Por_Pagar del tenant con filtros opcionales por
     * Proveedor y por estado (Req 42.6).
     *
     * @param proveedorId Proveedor a filtrar; {@code null} no filtra.
     * @param estado      etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param pageable    parametros de paginacion ya acotados (20/100).
     * @return la pagina de CxP como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<CuentaPorPagarDto> listarCxP(UUID proveedorId, String estado, Pageable pageable) {
        EstadoCuentaPorPagar filtro =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return cuentaPorPagarRepository.buscarConFiltros(proveedorId, filtro, pageable)
                .map(CuentaPorPagarDto::de);
    }

    /**
     * Consulta puntual de una Programacion_Pago del tenant (Req 23.3).
     *
     * @param programacionId identificador de la Programacion_Pago.
     * @return el DTO de la Programacion_Pago.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ProgramacionPagoDto consultarProgramacion(UUID programacionId) {
        String actor = actorActual();
        return ProgramacionPagoDto.de(cargarProgramacion(programacionId, actor));
    }

    /**
     * Listado paginado de Programaciones de Pago del tenant con filtro opcional por
     * Cuenta_Por_Pagar (Req 42.6).
     *
     * @param cuentaPorPagarId Cuenta_Por_Pagar a filtrar; {@code null} no filtra.
     * @param pageable         parametros de paginacion ya acotados (20/100).
     * @return la pagina de Programaciones de Pago como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ProgramacionPagoDto> listarProgramaciones(UUID cuentaPorPagarId, Pageable pageable) {
        return programacionPagoRepository.buscarConFiltros(cuentaPorPagarId, pageable)
                .map(ProgramacionPagoDto::de);
    }

    /**
     * Calcula la antiguedad de saldos (aging) de las CxP del tenant con saldo
     * pendiente, agrupada por Proveedor y clasificada por rango de dias de
     * vencimiento (Req 42.5). Solo lectura: no muta ninguna CxP. Los dias se miden
     * con el {@link Clock} inyectado desde la fecha de vencimiento (o la de registro
     * cuando no hay vencimiento).
     *
     * @param proveedorId Proveedor a filtrar; {@code null} incluye a todos.
     * @return el reporte de antiguedad de saldos por Proveedor.
     */
    @Transactional(readOnly = true)
    public AntiguedadSaldosProveedorDto consultarAging(UUID proveedorId) {
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<CuentaPorPagar> pendientes =
                cuentaPorPagarRepository.buscarPendientesParaAging(proveedorId);

        Map<UUID, BigDecimal[]> acumulado = new LinkedHashMap<>();
        for (CuentaPorPagar cxp : pendientes) {
            long dias = diasAntiguedad(cxp, hoy);
            int bucket = indiceRango(dias);
            BigDecimal[] rangos = acumulado.computeIfAbsent(
                    cxp.getProveedorId(), k -> nuevoAcumulador());
            rangos[bucket] = rangos[bucket].add(cxp.getSaldo());
        }

        List<AntiguedadSaldosProveedorDto.RenglonProveedor> renglones = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal[]> entrada : acumulado.entrySet()) {
            BigDecimal[] r = entrada.getValue();
            BigDecimal total = r[0].add(r[1]).add(r[2]).add(r[3]);
            renglones.add(new AntiguedadSaldosProveedorDto.RenglonProveedor(
                    entrada.getKey(), total, r[0], r[1], r[2], r[3]));
        }
        return new AntiguedadSaldosProveedorDto(renglones);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private BigDecimal[] nuevoAcumulador() {
        BigDecimal cero = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return new BigDecimal[] {cero, cero, cero, cero};
    }

    /**
     * Dias de antiguedad de una CxP respecto a {@code hoy}: dias transcurridos desde
     * su fecha de vencimiento, o desde su fecha de registro si no tiene vencimiento.
     * Nunca negativo (una CxP aun no vencida se clasifica en el rango 0-30).
     */
    private long diasAntiguedad(CuentaPorPagar cxp, LocalDate hoy) {
        LocalDate referencia = (cxp.getFechaVencimiento() != null)
                ? cxp.getFechaVencimiento()
                : LocalDate.ofInstant(cxp.getFechaRegistro(), ZoneOffset.UTC);
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

    private CuentaPorPagar cargarCxP(UUID cxpId, String actor) {
        if (cxpId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Cuenta_Por_Pagar solicitada.");
        }
        return cuentaPorPagarRepository.findById(cxpId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CXP, cxpId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Cuenta_Por_Pagar solicitada.");
                });
    }

    private ProgramacionPago cargarProgramacion(UUID programacionId, String actor) {
        if (programacionId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Programacion_Pago solicitada.");
        }
        return programacionPagoRepository.findById(programacionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_PROGRAMACION, programacionId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Programacion_Pago solicitada.");
                });
    }

    private EstadoCuentaPorPagar interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado de la Cuenta_Por_Pagar es obligatorio.");
        }
        try {
            return EstadoCuentaPorPagar.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Cuenta_Por_Pagar desconocido: " + etiqueta);
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
