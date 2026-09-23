package com.dessti.crm.tesoreria.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.tesoreria.adapter.out.persistence.ConciliacionBancariaRepository;
import com.dessti.crm.tesoreria.adapter.out.persistence.CuentaBancariaRepository;
import com.dessti.crm.tesoreria.adapter.out.persistence.EstadoCuentaBancarioRepository;
import com.dessti.crm.tesoreria.adapter.out.persistence.MovimientoBancarioRepository;
import com.dessti.crm.tesoreria.domain.CandidatoConciliacion;
import com.dessti.crm.tesoreria.domain.ConciliacionBancaria;
import com.dessti.crm.tesoreria.domain.CuentaBancaria;
import com.dessti.crm.tesoreria.domain.EstadoConciliacionBancaria;
import com.dessti.crm.tesoreria.domain.EstadoConciliacionMovimiento;
import com.dessti.crm.tesoreria.domain.EstadoCuentaBancario;
import com.dessti.crm.tesoreria.domain.MovimientoBancario;
import com.dessti.crm.tesoreria.domain.ReglasConciliacion;

/**
 * Servicio de aplicacion que gobierna las cuentas bancarias, la importacion de
 * estados de cuenta y la conciliacion bancaria (Req 43). Replica el patron de
 * {@code ServicioContabilidad}.
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>crearCuentaBancaria / listarCuentas (Req 43.1):</strong> alta y
 *       listado del catalogo de Cuenta_Bancaria por tenant. Audita la creacion
 *       (Req 43.7).</li>
 *   <li><strong>importarEstadoCuenta (Req 43.2):</strong> invoca el
 *       {@link ImportacionBancariaPort} y persiste el Estado_Cuenta_Bancario con sus
 *       Movimiento_Bancario en estado {@code pendiente}. Audita (Req 43.7).</li>
 *   <li><strong>conciliar (Req 43.3, 43.4, 43.5):</strong> empareja cada movimiento
 *       con una Poliza_Contable/Pago dentro de tolerancia via el
 *       {@link PolizaConciliablePort} y la regla PURA {@link ReglasConciliacion};
 *       los no emparejados quedan en excepcion (Req 43.4). Calcula la diferencia
 *       (saldo bancario - saldo contable) y crea la Conciliacion_Bancaria
 *       {@code completa} solo si la diferencia es cero y no hay excepciones (Req 43.5,
 *       Property 18). Audita (Req 43.7).</li>
 *   <li><strong>listarMovimientos / listarConciliaciones (Req 43.6):</strong>
 *       listados paginados (20/100) con filtros.</li>
 * </ul>
 *
 * <h2>Saldo contable (decision de diseno, Req 43.5)</h2>
 * <p>El saldo contable se DERIVA de las partidas emparejadas: es la suma de los
 * montos <em>con signo</em> de los Movimiento_Bancario conciliados. Asi, cuando cada
 * movimiento del periodo encuentra su partida contable (todo explicado), el saldo
 * contable iguala al saldo bancario (saldo final del estado de cuenta menos el saldo
 * inicial) y la diferencia es cero. Los movimientos en excepcion no suman al saldo
 * contable y, por tanto, ademas de dejar partidas sin explicar, producen diferencia,
 * impidiendo la conciliacion completa (Req 43.5).</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 43.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la peticion,
 * Req 23.4). La creacion de Cuenta_Bancaria, la importacion y la conciliacion se
 * auditan via {@link AuditoriaPort}.</p>
 */
@Service
public class ServicioTesoreria {

    /** Tipo de recurso de auditoria/RBAC de la Cuenta_Bancaria. */
    static final String RECURSO_CUENTA = "cuenta_bancaria";

    /** Tipo de recurso de auditoria/RBAC del Estado_Cuenta_Bancario / conciliacion. */
    static final String RECURSO_CONCILIACION = "conciliacion_bancaria";

    /** Tipo de recurso de auditoria/RBAC del Movimiento_Bancario. */
    static final String RECURSO_MOVIMIENTO = "movimiento_bancario";

    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final EstadoCuentaBancarioRepository estadoCuentaBancarioRepository;
    private final MovimientoBancarioRepository movimientoBancarioRepository;
    private final ConciliacionBancariaRepository conciliacionBancariaRepository;
    private final ImportacionBancariaPort importacionBancariaPort;
    private final PolizaConciliablePort polizaConciliablePort;
    private final AuditoriaPort auditoria;
    private final Clock clock;
    private final ConciliacionBancariaProperties propiedades;

    public ServicioTesoreria(CuentaBancariaRepository cuentaBancariaRepository,
                             EstadoCuentaBancarioRepository estadoCuentaBancarioRepository,
                             MovimientoBancarioRepository movimientoBancarioRepository,
                             ConciliacionBancariaRepository conciliacionBancariaRepository,
                             ImportacionBancariaPort importacionBancariaPort,
                             PolizaConciliablePort polizaConciliablePort,
                             AuditoriaPort auditoria,
                             Clock clock,
                             ConciliacionBancariaProperties propiedades) {
        this.cuentaBancariaRepository = cuentaBancariaRepository;
        this.estadoCuentaBancarioRepository = estadoCuentaBancarioRepository;
        this.movimientoBancarioRepository = movimientoBancarioRepository;
        this.conciliacionBancariaRepository = conciliacionBancariaRepository;
        this.importacionBancariaPort = importacionBancariaPort;
        this.polizaConciliablePort = polizaConciliablePort;
        this.auditoria = auditoria;
        this.clock = clock;
        this.propiedades = propiedades;
    }

    // ------------------------------------------------------------------
    // Cuenta_Bancaria (Req 43.1)
    // ------------------------------------------------------------------

    /**
     * Da de alta una Cuenta_Bancaria en el catalogo del tenant (Req 43.1). Audita la
     * creacion (Req 43.7).
     *
     * @param comando datos de la cuenta a crear.
     * @return el DTO de la cuenta creada.
     * @throws ReglaNegocioException si faltan datos o son invalidos (422).
     */
    @Transactional
    public CuentaBancariaDto crearCuentaBancaria(CrearCuentaBancariaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Cuenta_Bancaria son obligatorios.");
        }
        CuentaBancaria cuenta = CuentaBancaria.crear(
                comando.nombre(), comando.banco(), comando.clabe(), comando.moneda(), actor);
        CuentaBancaria guardada = cuentaBancariaRepository.save(cuenta);
        auditar(actor, "crear", RECURSO_CUENTA, guardada.getId(),
                "creada Cuenta_Bancaria [nombre=" + guardada.getNombre() + ", banco="
                        + guardada.getBanco() + "]");
        return CuentaBancariaDto.de(guardada);
    }

    /**
     * Consulta puntual de una Cuenta_Bancaria del tenant (Req 23.3).
     *
     * @param cuentaId identificador de la cuenta.
     * @return el DTO de la cuenta.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public CuentaBancariaDto consultarCuenta(UUID cuentaId) {
        String actor = actorActual();
        return CuentaBancariaDto.de(cargarCuenta(cuentaId, actor));
    }

    /**
     * Listado paginado de Cuentas_Bancarias del tenant con filtro opcional por
     * bandera de actividad (Req 43.1).
     *
     * @param activa   filtro de actividad; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de cuentas como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<CuentaBancariaDto> listarCuentas(Boolean activa, Pageable pageable) {
        return cuentaBancariaRepository.buscarConFiltros(activa, pageable)
                .map(CuentaBancariaDto::de);
    }

    // ------------------------------------------------------------------
    // Estado_Cuenta_Bancario (Req 43.2)
    // ------------------------------------------------------------------

    /**
     * Importa un Estado_Cuenta_Bancario para una Cuenta_Bancaria a traves del
     * {@link ImportacionBancariaPort} y persiste sus Movimiento_Bancario en estado
     * {@code pendiente} (Req 43.2). Audita la importacion (Req 43.7).
     *
     * @param cuentaBancariaId Cuenta_Bancaria destino; debe existir en el tenant.
     * @param comando          datos de la importacion (periodo, referencia y, para el
     *                         stub/pruebas, movimientos explicitos).
     * @return el DTO del estado de cuenta importado con sus movimientos.
     * @throws RecursoNoEncontradoException si la cuenta no es accesible (404).
     * @throws ReglaNegocioException        si faltan datos de la importacion (422).
     */
    @Transactional
    public EstadoCuentaBancarioDto importarEstadoCuenta(UUID cuentaBancariaId,
                                                       ImportarEstadoCuentaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la importacion son obligatorios.");
        }
        CuentaBancaria cuenta = cargarCuenta(cuentaBancariaId, actor);

        SolicitudImportacion solicitud = new SolicitudImportacion(
                TenantContext.require(),
                cuenta.getId(),
                comando.referenciaArchivo(),
                comando.periodoInicio(),
                comando.periodoFin(),
                comando.movimientos());
        EstadoCuentaImportado importado = importacionBancariaPort.importar(solicitud);
        if (importado == null) {
            throw new ReglaNegocioException(
                    "La importacion no devolvio ningun Estado_Cuenta_Bancario.");
        }

        EstadoCuentaBancario estado = EstadoCuentaBancario.importar(
                cuenta.getId(), comando.periodoInicio(), comando.periodoFin(),
                importado.saldoInicial(), importado.saldoFinal(), actor);
        for (MovimientoImportado linea : importado.movimientos()) {
            estado.agregarMovimiento(
                    linea.fecha(), linea.monto(), linea.referencia(), linea.descripcion(), actor);
        }
        EstadoCuentaBancario guardado = estadoCuentaBancarioRepository.save(estado);
        auditar(actor, "importar", RECURSO_CONCILIACION, guardado.getId(),
                "importado Estado_Cuenta_Bancario [cuenta=" + cuenta.getId() + ", periodo="
                        + guardado.getPeriodoInicio() + ".." + guardado.getPeriodoFin()
                        + ", movimientos=" + guardado.getMovimientos().size() + "]");
        return EstadoCuentaBancarioDto.de(guardado);
    }

    // ------------------------------------------------------------------
    // Conciliacion_Bancaria (Req 43.3, 43.4, 43.5)
    // ------------------------------------------------------------------

    /**
     * Concilia un Estado_Cuenta_Bancario (Req 43.3, 43.4, 43.5). Para cada
     * Movimiento_Bancario busca una partida contable candidata (Poliza_Contable/Pago)
     * que coincida en monto, fecha (dentro de la tolerancia configurada) y referencia
     * ({@link ReglasConciliacion}); si empareja, lo marca {@code conciliado}
     * enlazando la partida (Req 43.3); si no, lo marca {@code excepcion} para revision
     * manual (Req 43.4). Calcula la diferencia (saldo bancario - saldo contable) y
     * crea la Conciliacion_Bancaria {@code completa} solo si la diferencia es cero y
     * no quedan excepciones (Req 43.5, Property 18). Audita (Req 43.7).
     *
     * @param estadoCuentaId Estado_Cuenta_Bancario a conciliar; debe existir en el
     *                       tenant.
     * @return el DTO de la Conciliacion_Bancaria resultante.
     * @throws RecursoNoEncontradoException si el estado de cuenta no es accesible (404).
     */
    @Transactional
    public ConciliacionBancariaDto conciliar(UUID estadoCuentaId) {
        String actor = actorActual();
        EstadoCuentaBancario estado = cargarEstadoCuenta(estadoCuentaId, actor);
        int toleranciaDias = propiedades.toleranciaDias();

        List<MovimientoBancario> movimientos = estado.getMovimientos();
        List<CandidatoConciliacion> candidatos = buscarCandidatos(movimientos, toleranciaDias);

        BigDecimal saldoContable = BigDecimal.ZERO
                .setScale(ReglasConciliacion.ESCALA_MONETARIA, RoundingMode.HALF_UP);
        int excepciones = 0;
        for (MovimientoBancario movimiento : movimientos) {
            Optional<CandidatoConciliacion> emparejado = ReglasConciliacion.emparejar(
                    movimiento.getMonto(), movimiento.getFecha(), movimiento.getReferencia(),
                    candidatos, toleranciaDias);
            if (emparejado.isPresent()) {
                CandidatoConciliacion candidato = emparejado.get();
                if (candidato.origen() == CandidatoConciliacion.Origen.PAGO) {
                    movimiento.conciliarConPago(candidato.id(), actor);
                } else {
                    movimiento.conciliarConPoliza(candidato.id(), actor);
                }
                // El saldo contable suma el monto CON SIGNO de la partida explicada.
                saldoContable = saldoContable.add(movimiento.getMonto());
            } else {
                movimiento.marcarExcepcion(actor);
                excepciones++;
            }
        }
        saldoContable = saldoContable.setScale(ReglasConciliacion.ESCALA_MONETARIA,
                RoundingMode.HALF_UP);

        // Saldo bancario del periodo: saldo final menos saldo inicial (variacion neta),
        // comparable con la suma de movimientos explicados.
        BigDecimal saldoBancario = estado.getSaldoFinal().subtract(estado.getSaldoInicial())
                .setScale(ReglasConciliacion.ESCALA_MONETARIA, RoundingMode.HALF_UP);

        movimientoBancarioRepository.saveAll(movimientos);

        ConciliacionBancaria conciliacion = ConciliacionBancaria.registrar(
                estado.getCuentaBancariaId(), estado.getId(), saldoBancario, saldoContable,
                excepciones, Instant.now(clock), actor);
        ConciliacionBancaria guardada = conciliacionBancariaRepository.save(conciliacion);

        auditar(actor, "conciliar", RECURSO_CONCILIACION, guardada.getId(),
                "conciliado Estado_Cuenta_Bancario [estado_cuenta=" + estado.getId()
                        + ", diferencia=" + guardada.getDiferencia().toPlainString()
                        + ", excepciones=" + excepciones + ", resultado="
                        + guardada.getEstado().valorBd() + "]");
        return ConciliacionBancariaDto.de(guardada);
    }

    /**
     * Listado paginado de Movimiento_Bancario del tenant con filtros opcionales por
     * Cuenta_Bancaria, periodo (rango de fechas) y estado de conciliacion (Req 43.6).
     *
     * @param cuentaBancariaId   Cuenta_Bancaria a filtrar; {@code null} no filtra.
     * @param desde              fecha minima (inclusiva); {@code null} no filtra.
     * @param hasta              fecha maxima (inclusiva); {@code null} no filtra.
     * @param estadoConciliacion estado a filtrar; {@code null} no filtra.
     * @param pageable           parametros de paginacion ya acotados (20/100).
     * @return la pagina de movimientos como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<MovimientoBancarioDto> listarMovimientos(UUID cuentaBancariaId, LocalDate desde,
                                                        LocalDate hasta,
                                                        EstadoConciliacionMovimiento estadoConciliacion,
                                                        Pageable pageable) {
        return movimientoBancarioRepository
                .buscarConFiltros(cuentaBancariaId, desde, hasta, estadoConciliacion, pageable)
                .map(MovimientoBancarioDto::de);
    }

    /**
     * Listado paginado de Conciliacion_Bancaria del tenant con filtros opcionales por
     * Cuenta_Bancaria, periodo (rango de instantes) y estado (Req 43.6).
     *
     * @param cuentaBancariaId Cuenta_Bancaria a filtrar; {@code null} no filtra.
     * @param desde            instante minimo (inclusivo); {@code null} no filtra.
     * @param hasta            instante maximo (inclusivo); {@code null} no filtra.
     * @param estado           estado a filtrar; {@code null} no filtra.
     * @param pageable         parametros de paginacion ya acotados (20/100).
     * @return la pagina de conciliaciones como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ConciliacionBancariaDto> listarConciliaciones(UUID cuentaBancariaId, Instant desde,
                                                             Instant hasta,
                                                             EstadoConciliacionBancaria estado,
                                                             Pageable pageable) {
        return conciliacionBancariaRepository
                .buscarConFiltros(cuentaBancariaId, desde, hasta, estado, pageable)
                .map(ConciliacionBancariaDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Reune los candidatos de emparejamiento consultando el {@link PolizaConciliablePort}
     * sobre la ventana de fechas de los movimientos ampliada por la tolerancia en dias.
     */
    private List<CandidatoConciliacion> buscarCandidatos(List<MovimientoBancario> movimientos,
                                                         int toleranciaDias) {
        LocalDate min = null;
        LocalDate max = null;
        for (MovimientoBancario movimiento : movimientos) {
            LocalDate fecha = movimiento.getFecha();
            if (min == null || fecha.isBefore(min)) {
                min = fecha;
            }
            if (max == null || fecha.isAfter(max)) {
                max = fecha;
            }
        }
        if (min == null || max == null) {
            return List.of();
        }
        LocalDate desde = min.minusDays(toleranciaDias);
        LocalDate hasta = max.plusDays(toleranciaDias);
        List<CandidatoConciliacion> candidatos = polizaConciliablePort.buscarCandidatos(desde, hasta);
        return (candidatos == null) ? List.of() : candidatos;
    }

    private CuentaBancaria cargarCuenta(UUID cuentaId, String actor) {
        if (cuentaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Cuenta_Bancaria solicitada.");
        }
        return cuentaBancariaRepository.findById(cuentaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CUENTA, cuentaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Cuenta_Bancaria solicitada.");
                });
    }

    private EstadoCuentaBancario cargarEstadoCuenta(UUID estadoCuentaId, String actor) {
        if (estadoCuentaId == null) {
            throw new RecursoNoEncontradoException(
                    "No se encontro el Estado_Cuenta_Bancario solicitado.");
        }
        return estadoCuentaBancarioRepository.findById(estadoCuentaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CONCILIACION, estadoCuentaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el Estado_Cuenta_Bancario solicitado.");
                });
    }

    private void auditar(String actor, String accion, String recurso, UUID recursoId,
                         String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, recurso,
                detalle + " [id=" + recursoId + "]", null, null));
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
