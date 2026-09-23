package com.dessti.crm.rhnomina.nomina.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.facturacion.application.PacPort;
import com.dessti.crm.facturacion.application.ResultadoTimbrado;
import com.dessti.crm.facturacion.application.SolicitudTimbrado;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.rhnomina.empleado.adapter.out.persistence.ContratoLaboralRepository;
import com.dessti.crm.rhnomina.empleado.adapter.out.persistence.EmpleadoRepository;
import com.dessti.crm.rhnomina.empleado.adapter.out.persistence.IncidenciaRepository;
import com.dessti.crm.rhnomina.empleado.domain.ContratoLaboral;
import com.dessti.crm.rhnomina.empleado.domain.Empleado;
import com.dessti.crm.rhnomina.empleado.domain.Incidencia;
import com.dessti.crm.rhnomina.empleado.domain.Periodicidad;
import com.dessti.crm.rhnomina.empleado.domain.TipoIncidencia;
import com.dessti.crm.rhnomina.nomina.adapter.out.persistence.NominaRepository;
import com.dessti.crm.rhnomina.nomina.adapter.out.persistence.ReciboNominaRepository;
import com.dessti.crm.rhnomina.nomina.domain.CalculoNomina;
import com.dessti.crm.rhnomina.nomina.domain.EntradaNomina;
import com.dessti.crm.rhnomina.nomina.domain.EstadoNomina;
import com.dessti.crm.rhnomina.nomina.domain.Nomina;
import com.dessti.crm.rhnomina.nomina.domain.ReciboNomina;
import com.dessti.crm.rhnomina.nomina.domain.ResultadoNomina;
import com.dessti.crm.rhnomina.nomina.domain.TablasFiscalesNomina;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de la {@link Nomina} y de sus
 * {@link ReciboNomina} (Req 41). Reutiliza las piezas puras del dominio
 * ({@link CalculoNomina}, {@link TablasFiscalesNomina}) y el puerto de Timbrado
 * {@link PacPort} de facturacion (CFDI de nomina). Replica el patron establecido por
 * {@code ServicioFacturas}/{@code ServicioOrdenesFabricacion}.
 *
 * <h2>Operaciones (Req 41)</h2>
 * <ul>
 *   <li><strong>crearNomina (Req 41.1):</strong> crea la Nomina de un Periodo_Nomina
 *       en estado {@code borrador}; audita.</li>
 *   <li><strong>calcular (Req 41.1, 41.2, 41.3):</strong> por cada Empleado activo del
 *       tenant con Contrato_Laboral vigente, valida la presencia de datos fiscales
 *       (RFC/CURP/NSS y contrato); calcula percepciones/deducciones/subsidio/neto con
 *       {@link CalculoNomina}; genera un {@link ReciboNomina} por Empleado; fija los
 *       totales de la Nomina y transita {@code borrador -> calculada}; audita.</li>
 *   <li><strong>autorizar (Req 41.4, 41.5):</strong> transita {@code calculada ->
 *       autorizada}; audita.</li>
 *   <li><strong>timbrar (Req 41.4):</strong> transita {@code autorizada -> timbrada};
 *       por cada Recibo_Nomina solicita el Timbrado al PAC (CFDI de nomina) y, en
 *       exito, lo marca {@code timbrado}; audita.</li>
 *   <li><strong>marcarPagada (Req 41.5):</strong> transita {@code timbrada ->
 *       pagada}; audita.</li>
 *   <li><strong>consultar / listar / listarRecibos (Req 23.3, 41):</strong> 404 +
 *       auditoria del intento si no es accesible; listados paginados (20/100).</li>
 * </ul>
 *
 * <h2>Decisiones documentadas</h2>
 * <ul>
 *   <li><strong>Req 41.3 (datos fiscales faltantes) — ATOMICO:</strong> si algun
 *       Empleado activo carece de datos fiscales (RFC/CURP/NSS) o de Contrato_Laboral
 *       vigente, se <em>rechaza el calculo completo</em> con 422 nombrando al Empleado
 *       y el dato faltante, sin persistir ningun Recibo_Nomina. Es la interpretacion
 *       mas segura: una Nomina parcial (con Empleados omitidos silenciosamente) seria
 *       una fuente de errores de pago. La transaccion no deja rastros.</li>
 *   <li><strong>Timbrado — ATOMICO:</strong> al timbrar la Nomina, si el PAC rechaza
 *       CUALQUIER Recibo_Nomina, se falla la operacion completa con 422 y se audita el
 *       rechazo; la transaccion revierte, de modo que la Nomina conserva su estado
 *       {@code autorizada} y ningun Recibo_Nomina queda a medio timbrar. La Nomina solo
 *       pasa a {@code timbrada} si TODOS sus recibos se timbraron con exito.</li>
 *   <li><strong>Dias del periodo:</strong> se derivan de la periodicidad del contrato
 *       (semanal=7, quincenal=15, mensual=30 dias), coherente con la practica de
 *       nomina en Mexico.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 41.8)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la peticion,
 * Req 23.4). Cada cambio de estado de la Nomina y cada Timbrado de Recibo_Nomina se
 * audita via {@link AuditoriaPort} con el estado anterior y el nuevo (Req 41.8).</p>
 */
@Service
public class ServicioNomina {

    /** Tipo de recurso de auditoria/RBAC de la Nomina. */
    static final String RECURSO_NOMINA = "nomina";

    /** Tipo de recurso de auditoria/RBAC del Recibo_Nomina. */
    static final String RECURSO_RECIBO = "recibo_nomina";

    /** Dias de un periodo semanal (Req 41.1). */
    static final int DIAS_SEMANAL = 7;

    /** Dias de un periodo quincenal (Req 41.1). */
    static final int DIAS_QUINCENAL = 15;

    /** Dias de un periodo mensual (Req 41.1). */
    static final int DIAS_MENSUAL = 30;

    private final NominaRepository nominaRepository;
    private final ReciboNominaRepository reciboRepository;
    private final EmpleadoRepository empleadoRepository;
    private final ContratoLaboralRepository contratoRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final PacPort pac;
    private final AuditoriaPort auditoria;

    public ServicioNomina(NominaRepository nominaRepository,
                          ReciboNominaRepository reciboRepository,
                          EmpleadoRepository empleadoRepository,
                          ContratoLaboralRepository contratoRepository,
                          IncidenciaRepository incidenciaRepository,
                          PacPort pac,
                          AuditoriaPort auditoria) {
        this.nominaRepository = nominaRepository;
        this.reciboRepository = reciboRepository;
        this.empleadoRepository = empleadoRepository;
        this.contratoRepository = contratoRepository;
        this.incidenciaRepository = incidenciaRepository;
        this.pac = pac;
        this.auditoria = auditoria;
    }

    /**
     * Crea una Nomina de un Periodo_Nomina en estado {@code borrador} (Req 41.1) y
     * audita el alta.
     *
     * @param comando datos de la Nomina a crear.
     * @return el DTO de la Nomina creada (en {@code borrador}).
     * @throws ReglaNegocioException si el periodo es nulo o invalido (422).
     */
    @Transactional
    public NominaDto crearNomina(CrearNominaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Nomina son obligatorios.");
        }
        Nomina nomina = Nomina.crear(comando.periodoNomina(), actor);
        Nomina guardada = nominaRepository.save(nomina);
        auditar(actor, "crear", RECURSO_NOMINA, guardada.getId(),
                "creada Nomina del periodo " + guardada.getPeriodoNomina() + " en estado '"
                        + guardada.getEstado().valorBd() + "'", null, guardada.getEstado().valorBd());
        return NominaDto.de(guardada);
    }

    /**
     * Calcula la Nomina (Req 41.1, 41.2, 41.3): por cada Empleado activo del tenant con
     * Contrato_Laboral vigente valida la presencia de datos fiscales; calcula sus
     * importes con {@link CalculoNomina}; genera un {@link ReciboNomina}; fija los
     * totales de la Nomina y transita {@code borrador -> calculada}. Si algun Empleado
     * carece de datos fiscales o de contrato, rechaza el calculo completo (422, atomico).
     *
     * @param comando datos del calculo (Nomina y parametros del proceso).
     * @return el DTO de la Nomina {@code calculada}.
     * @throws RecursoNoEncontradoException si la Nomina no es accesible (404).
     * @throws ReglaNegocioException si a algun Empleado le faltan datos fiscales o
     *         Contrato_Laboral (422, Req 41.3), o no hay Empleados que calcular.
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la Nomina
     *         no esta en {@code borrador} (409).
     */
    @Transactional
    public NominaDto calcular(CalcularNominaCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.nominaId() == null) {
            throw new ReglaNegocioException("El identificador de la Nomina es obligatorio.");
        }
        Nomina nomina = cargarNomina(comando.nominaId(), actor);
        if (reciboRepository.existsByNominaId(nomina.getId())) {
            throw new ReglaNegocioException("La Nomina ya tiene Recibo_Nomina calculados.");
        }

        BigDecimal aguinaldo = valorOCero(comando.aguinaldo());
        BigDecimal ptu = valorOCero(comando.ptu());
        BigDecimal tasaInfonavit = valorOCero(comando.tasaInfonavit());

        List<Empleado> empleados = empleadoRepository.findByActivoTrueOrderByNombreAsc();
        if (empleados.isEmpty()) {
            throw new ReglaNegocioException(
                    "No hay Empleados activos para calcular la Nomina del periodo "
                            + nomina.getPeriodoNomina() + ".");
        }

        List<ReciboNomina> recibos = new ArrayList<>();
        BigDecimal totalPercepciones = BigDecimal.ZERO;
        BigDecimal totalDeducciones = BigDecimal.ZERO;
        BigDecimal totalNeto = BigDecimal.ZERO;

        for (Empleado empleado : empleados) {
            // Req 41.3: validar la presencia de datos fiscales del Empleado.
            validarDatosFiscales(empleado);
            ContratoLaboral contrato = contratoVigente(empleado);

            int dias = diasDelPeriodo(contrato.getPeriodicidad());
            BigDecimal tiempoExtra = tiempoExtraDelPeriodo(empleado.getId(), nomina.getPeriodoNomina());

            ResultadoNomina resultado = CalculoNomina.calcular(new EntradaNomina(
                    contrato.getSalarioDiario(), dias, tiempoExtra, aguinaldo, ptu, tasaInfonavit));

            recibos.add(ReciboNomina.generar(nomina.getId(), empleado.getId(), resultado, actor));
            totalPercepciones = totalPercepciones.add(resultado.percepciones());
            totalDeducciones = totalDeducciones.add(resultado.deducciones());
            totalNeto = totalNeto.add(resultado.neto());
        }

        reciboRepository.saveAll(recibos);
        nomina.registrarCalculo(
                TablasFiscalesNomina.redondear(totalPercepciones),
                TablasFiscalesNomina.redondear(totalDeducciones),
                TablasFiscalesNomina.redondear(totalNeto), actor);
        Nomina guardada = nominaRepository.save(nomina);

        auditar(actor, "calcular", RECURSO_NOMINA, guardada.getId(),
                "calculada Nomina con " + recibos.size() + " Recibo_Nomina [total_neto="
                        + guardada.getTotalNeto().toPlainString() + "]",
                EstadoNomina.BORRADOR.valorBd(), EstadoNomina.CALCULADA.valorBd());
        return NominaDto.de(guardada);
    }

    /**
     * Autoriza la Nomina y transita {@code calculada -> autorizada} (Req 41.4, 41.5).
     *
     * @param nominaId identificador de la Nomina.
     * @return el DTO de la Nomina {@code autorizada}.
     * @throws RecursoNoEncontradoException si la Nomina no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la Nomina
     *         no esta {@code calculada} (409, Req 41.6).
     */
    @Transactional
    public NominaDto autorizar(UUID nominaId) {
        String actor = actorActual();
        Nomina nomina = cargarNomina(nominaId, actor);
        EstadoNomina anterior = nomina.getEstado();
        nomina.autorizar(actor);
        Nomina guardada = nominaRepository.save(nomina);
        auditar(actor, "autorizar", RECURSO_NOMINA, guardada.getId(),
                "Nomina autorizada", anterior.valorBd(), guardada.getEstado().valorBd());
        return NominaDto.de(guardada);
    }

    /**
     * Timbra la Nomina (Req 41.4): transita {@code autorizada -> timbrada} y solicita al
     * PAC el Timbrado de cada Recibo_Nomina como CFDI de nomina. Si el PAC rechaza
     * cualquier recibo, falla la operacion completa (422, atomico) y la transaccion
     * revierte, conservando el estado {@code autorizada}.
     *
     * @param nominaId identificador de la Nomina.
     * @return el DTO de la Nomina {@code timbrada}.
     * @throws RecursoNoEncontradoException si la Nomina no es accesible (404).
     * @throws ReglaNegocioException si la Nomina no tiene Recibo_Nomina o el PAC rechaza
     *         algun Timbrado (422, Req 41.4).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la Nomina
     *         no esta {@code autorizada} (409, Req 41.6).
     */
    @Transactional
    public NominaDto timbrar(UUID nominaId) {
        String actor = actorActual();
        Nomina nomina = cargarNomina(nominaId, actor);
        EstadoNomina anterior = nomina.getEstado();
        nomina.marcarTimbrada(actor);

        List<ReciboNomina> recibos = reciboRepository.findByNominaId(nomina.getId());
        if (recibos.isEmpty()) {
            throw new ReglaNegocioException(
                    "La Nomina no tiene Recibo_Nomina que timbrar.");
        }

        UUID tenantId = TenantContext.require();
        for (ReciboNomina recibo : recibos) {
            Empleado empleado = empleadoRepository.findByIdAndActivoTrue(recibo.getEmpleadoId())
                    .orElseThrow(() -> new ReglaNegocioException(
                            "El Empleado del Recibo_Nomina ya no esta activo; no puede timbrarse."));
            SolicitudTimbrado solicitud = new SolicitudTimbrado(
                    tenantId, recibo.getId(), empleado.getRfc(), empleado.getNombre(),
                    null, null, null,
                    recibo.getPercepciones(), BigDecimal.ZERO, recibo.getDeducciones(),
                    recibo.getNeto());
            ResultadoTimbrado resultado = pac.timbrar(solicitud);
            if (!resultado.exito()) {
                // Timbrado atomico (Req 41.4): un rechazo falla toda la operacion.
                auditar(actor, "timbrado_rechazado", RECURSO_RECIBO, recibo.getId(),
                        "el PAC rechazo el timbrado del Recibo_Nomina: " + resultado.mensajeError(),
                        null, null);
                throw new ReglaNegocioException(
                        "El PAC rechazo el timbrado de un Recibo_Nomina: " + resultado.mensajeError());
            }
            recibo.timbrar(resultado.folioFiscal(), resultado.selloSat(),
                    resultado.fechaTimbrado(), actor);
            auditar(actor, "timbrar", RECURSO_RECIBO, recibo.getId(),
                    "Recibo_Nomina timbrado [folio_fiscal=" + recibo.getFolioFiscal() + "]",
                    null, recibo.getEstado().valorBd());
        }
        reciboRepository.saveAll(recibos);

        Nomina guardada = nominaRepository.save(nomina);
        auditar(actor, "timbrar", RECURSO_NOMINA, guardada.getId(),
                "Nomina timbrada con " + recibos.size() + " Recibo_Nomina",
                anterior.valorBd(), guardada.getEstado().valorBd());
        return NominaDto.de(guardada);
    }

    /**
     * Marca la Nomina como pagada y transita {@code timbrada -> pagada} (Req 41.5).
     *
     * @param nominaId identificador de la Nomina.
     * @return el DTO de la Nomina {@code pagada}.
     * @throws RecursoNoEncontradoException si la Nomina no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la Nomina
     *         no esta {@code timbrada} (409, Req 41.6).
     */
    @Transactional
    public NominaDto marcarPagada(UUID nominaId) {
        String actor = actorActual();
        Nomina nomina = cargarNomina(nominaId, actor);
        EstadoNomina anterior = nomina.getEstado();
        nomina.marcarPagada(actor);
        Nomina guardada = nominaRepository.save(nomina);
        auditar(actor, "pagar", RECURSO_NOMINA, guardada.getId(),
                "Nomina pagada", anterior.valorBd(), guardada.getEstado().valorBd());
        return NominaDto.de(guardada);
    }

    /**
     * Consulta puntual de una Nomina del tenant (Req 23.3).
     *
     * @param nominaId identificador de la Nomina.
     * @return el DTO de la Nomina.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public NominaDto consultar(UUID nominaId) {
        String actor = actorActual();
        return NominaDto.de(cargarNomina(nominaId, actor));
    }

    /**
     * Listado paginado de Nominas del tenant con filtros opcionales por Periodo_Nomina
     * y por estado (Req 41). Un filtro nulo/blanco no restringe.
     *
     * @param periodo  codigo de Periodo_Nomina a filtrar; {@code null}/blanco no filtra.
     * @param estado   etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Nominas como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<NominaDto> listar(String periodo, String estado, Pageable pageable) {
        String periodoFiltro = (periodo == null || periodo.isBlank()) ? null : periodo.strip();
        EstadoNomina estadoFiltro =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return nominaRepository.buscarConFiltros(periodoFiltro, estadoFiltro, pageable)
                .map(NominaDto::de);
    }

    /**
     * Lista de forma paginada los Recibo_Nomina de una Nomina accesible del tenant
     * (Req 41.1). Verifica primero la accesibilidad de la Nomina (404 si no lo es).
     *
     * @param nominaId identificador de la Nomina.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Recibo_Nomina como DTOs.
     * @throws RecursoNoEncontradoException si la Nomina no es accesible (404).
     */
    @Transactional(readOnly = true)
    public Page<ReciboNominaDto> listarRecibos(UUID nominaId, Pageable pageable) {
        String actor = actorActual();
        Nomina nomina = cargarNomina(nominaId, actor);
        return reciboRepository.findByNominaId(nomina.getId(), pageable).map(ReciboNominaDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Nomina cargarNomina(UUID nominaId, String actor) {
        if (nominaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Nomina solicitada.");
        }
        return nominaRepository.findById(nominaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_NOMINA, nominaId);
                    throw new RecursoNoEncontradoException("No se encontro la Nomina solicitada.");
                });
    }

    /**
     * Valida la presencia de los datos fiscales del Empleado (Req 41.3): RFC, CURP y
     * NSS. Si falta alguno, rechaza el calculo nombrando el Empleado y el dato faltante.
     */
    private void validarDatosFiscales(Empleado empleado) {
        if (esVacio(empleado.getRfc())) {
            throw datoFiscalFaltante(empleado, "RFC");
        }
        if (esVacio(empleado.getCurp())) {
            throw datoFiscalFaltante(empleado, "CURP");
        }
        if (esVacio(empleado.getNss())) {
            throw datoFiscalFaltante(empleado, "NSS");
        }
    }

    private ContratoLaboral contratoVigente(Empleado empleado) {
        List<ContratoLaboral> contratos =
                contratoRepository.findByEmpleadoIdAndActivoTrue(empleado.getId());
        if (contratos.isEmpty()) {
            throw new ReglaNegocioException(
                    "No se puede calcular la Nomina: al Empleado '" + empleado.getNombre()
                            + "' (id=" + empleado.getId() + ") le falta un Contrato_Laboral vigente.");
        }
        return contratos.get(0);
    }

    private BigDecimal tiempoExtraDelPeriodo(UUID empleadoId, String periodo) {
        List<Incidencia> incidencias =
                incidenciaRepository.findByEmpleadoIdAndPeriodoNomina(empleadoId, periodo);
        BigDecimal total = BigDecimal.ZERO;
        for (Incidencia incidencia : incidencias) {
            if (incidencia.getTipo() == TipoIncidencia.TIEMPO_EXTRA && incidencia.getCantidad() != null) {
                total = total.add(incidencia.getCantidad());
            }
        }
        return TablasFiscalesNomina.redondear(total);
    }

    private int diasDelPeriodo(Periodicidad periodicidad) {
        return switch (periodicidad) {
            case SEMANAL -> DIAS_SEMANAL;
            case QUINCENAL -> DIAS_QUINCENAL;
            case MENSUAL -> DIAS_MENSUAL;
        };
    }

    private ReglaNegocioException datoFiscalFaltante(Empleado empleado, String dato) {
        return new ReglaNegocioException(
                "No se puede calcular la Nomina: al Empleado '" + empleado.getNombre()
                        + "' (id=" + empleado.getId() + ") le falta el dato fiscal " + dato + ".");
    }

    private EstadoNomina interpretarEstado(String etiqueta) {
        try {
            return EstadoNomina.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Nomina desconocido: " + etiqueta);
        }
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static BigDecimal valorOCero(BigDecimal valor) {
        return (valor == null) ? BigDecimal.ZERO : valor;
    }

    private void auditar(String actor, String accion, String recurso, UUID recursoId, String detalle,
                         String valorAnterior, String valorNuevo) {
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
