package com.dessti.crm.contabilidad.polizas.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.CuentaContableRepository;
import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.PolizaContableRepository;
import com.dessti.crm.contabilidad.polizas.domain.CuentaContable;
import com.dessti.crm.contabilidad.polizas.domain.MovimientoPoliza;
import com.dessti.crm.contabilidad.polizas.domain.PolizaContable;
import com.dessti.crm.contabilidad.polizas.domain.TipoPoliza;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el catalogo de Cuentas_Contables y las
 * Polizas_Contables balanceadas (Req 38). Implementa {@link PolizaContablePort}, la
 * frontera hexagonal que los productores de eventos contables invocan para generar
 * polizas (Req 38.2). Replica el patron de {@code ServicioCuentasPorCobrar}.
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>crearCuenta / listarCuentas (Req 38.1):</strong> mantiene el
 *       catalogo de Cuenta_Contable por tenant; el codigo es unico por tenant (409
 *       si se duplica). Audita la creacion (Req 38.7).</li>
 *   <li><strong>registrarPoliza (Req 38.2, 38.3, 38.4):</strong> valida el balance
 *       (regla pura del dominio, Property 16) y persiste la poliza; si no esta
 *       balanceada, rechaza con 422 informando la diferencia y no persiste nada
 *       (Req 38.4). Audita (Req 38.7).</li>
 *   <li><strong>reversarPoliza (Req 38.5):</strong> crea la poliza de reverso
 *       (espejo) de una poliza existente, preservando la inmutabilidad contable
 *       (nunca se modifica ni se borra la poliza original). Audita.</li>
 *   <li><strong>generarPolizaDeEvento (Req 38.2):</strong> capacidad de generacion
 *       automatica de polizas para los productores de eventos.</li>
 *   <li><strong>listarPolizas (Req 38.6):</strong> listado paginado (20/100) con
 *       filtros por rango de fechas y por Cuenta_Contable.</li>
 *   <li><strong>consultar (Req 23.3):</strong> 404 + auditoria del intento si no es
 *       accesible.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 38.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la peticion,
 * Req 23.4). La creacion de Cuenta_Contable y de Poliza_Contable se audita via
 * {@link AuditoriaPort}.</p>
 */
@Service
public class ServicioContabilidad implements PolizaContablePort {

    /** Tipo de recurso de auditoria/RBAC de la Cuenta_Contable. */
    static final String RECURSO_CUENTA = "cuenta_contable";

    /** Tipo de recurso de auditoria/RBAC de la Poliza_Contable. */
    static final String RECURSO_POLIZA = "poliza_contable";

    private final CuentaContableRepository cuentaContableRepository;
    private final PolizaContableRepository polizaContableRepository;
    private final AuditoriaPort auditoria;

    public ServicioContabilidad(CuentaContableRepository cuentaContableRepository,
                                PolizaContableRepository polizaContableRepository,
                                AuditoriaPort auditoria) {
        this.cuentaContableRepository = cuentaContableRepository;
        this.polizaContableRepository = polizaContableRepository;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------
    // Cuenta_Contable (Req 38.1)
    // ------------------------------------------------------------------

    /**
     * Crea una Cuenta_Contable en el catalogo del tenant (Req 38.1). El codigo es
     * unico por tenant: si ya existe, se rechaza con 409.
     *
     * @param comando datos de la cuenta a crear.
     * @return el DTO de la cuenta creada.
     * @throws ReglaNegocioException   si faltan datos o son invalidos (422).
     * @throws ConflictoUnicidadException si el codigo ya existe en el tenant (409).
     */
    @Transactional
    public CuentaContableDto crearCuenta(CrearCuentaContableCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Cuenta_Contable son obligatorios.");
        }
        CuentaContable cuenta = CuentaContable.crear(
                comando.codigo(), comando.nombre(), comando.tipo(), comando.naturaleza(), actor);
        if (cuentaContableRepository.existsByCodigo(cuenta.getCodigo())) {
            throw new ConflictoUnicidadException(
                    "Ya existe una Cuenta_Contable con el codigo '" + cuenta.getCodigo()
                            + "' en la Empresa.");
        }
        CuentaContable guardada;
        try {
            guardada = cuentaContableRepository.save(cuenta);
        } catch (DataIntegrityViolationException ex) {
            // Segunda capa de defensa ante concurrencia (UNIQUE (tenant_id, codigo)).
            throw new ConflictoUnicidadException(
                    "Ya existe una Cuenta_Contable con el codigo '" + cuenta.getCodigo()
                            + "' en la Empresa.");
        }
        auditar(actor, "crear", RECURSO_CUENTA, guardada.getId(),
                "creada Cuenta_Contable [codigo=" + guardada.getCodigo() + ", tipo="
                        + guardada.getTipo().valorBd() + "]", null, null);
        return CuentaContableDto.de(guardada);
    }

    /**
     * Consulta puntual de una Cuenta_Contable del tenant (Req 23.3).
     *
     * @param cuentaId identificador de la cuenta.
     * @return el DTO de la cuenta.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public CuentaContableDto consultarCuenta(UUID cuentaId) {
        String actor = actorActual();
        return CuentaContableDto.de(cargarCuenta(cuentaId, actor));
    }

    /**
     * Listado paginado de Cuentas_Contables del tenant con filtro opcional por
     * bandera de actividad (Req 38.1).
     *
     * @param activa   filtro de actividad; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de cuentas como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<CuentaContableDto> listarCuentas(Boolean activa, Pageable pageable) {
        return cuentaContableRepository.buscarConFiltros(activa, pageable)
                .map(CuentaContableDto::de);
    }

    // ------------------------------------------------------------------
    // Poliza_Contable (Req 38.2 - 38.6)
    // ------------------------------------------------------------------

    /**
     * Registra una Poliza_Contable balanceada (Req 38.2, 38.3, 38.4; Property 16). El
     * balance lo valida la fabrica pura del dominio: si la poliza no esta balanceada,
     * se rechaza con 422 informando la diferencia y no se persiste nada (Req 38.4).
     * Audita el registro (Req 38.7).
     *
     * @param comando datos de la poliza y sus renglones.
     * @return el DTO de la poliza registrada.
     * @throws ReglaNegocioException si faltan datos, un renglon es invalido o la
     *         poliza no esta balanceada (422).
     */
    @Transactional
    public PolizaContableDto registrarPoliza(RegistrarPolizaCommand comando) {
        String actor = actorActual();
        PolizaContable poliza = construirPoliza(comando, actor);
        PolizaContable guardada = polizaContableRepository.save(poliza);
        auditar(actor, "crear", RECURSO_POLIZA, guardada.getId(),
                "registrada Poliza_Contable balanceada [fecha=" + guardada.getFecha()
                        + ", tipo=" + guardada.getTipo().valorBd() + ", total="
                        + guardada.getTotalCargos().toPlainString() + "]", null, null);
        return PolizaContableDto.de(guardada);
    }

    /**
     * Crea la poliza de reverso de una poliza existente (Req 38.5): una nueva poliza
     * espejo que intercambia cargos y abonos, preservando la inmutabilidad contable
     * (la poliza original no se modifica ni se borra). Audita.
     *
     * @param polizaId identificador de la poliza a reversar.
     * @param fecha    fecha contable del reverso; {@code null} usa la fecha de hoy (UTC).
     * @return el DTO de la poliza de reverso.
     * @throws RecursoNoEncontradoException si la poliza no es accesible (404).
     */
    @Transactional
    public PolizaContableDto reversarPoliza(UUID polizaId, LocalDate fecha) {
        String actor = actorActual();
        PolizaContable original = cargarPoliza(polizaId, actor);
        LocalDate fechaReverso = (fecha != null) ? fecha : original.getFecha();
        PolizaContable reverso = original.reversar(fechaReverso, actor);
        PolizaContable guardada = polizaContableRepository.save(reverso);
        auditar(actor, "reversar", RECURSO_POLIZA, guardada.getId(),
                "registrada Poliza_Contable de reverso [poliza_revertida="
                        + original.getId() + ", total=" + guardada.getTotalCargos().toPlainString()
                        + "]", null, null);
        return PolizaContableDto.de(guardada);
    }

    /**
     * Genera y persiste una Poliza_Contable balanceada ante un evento contable
     * (Req 38.2), invocable por los productores de eventos via
     * {@link PolizaContablePort}. Reutiliza {@link #registrarPoliza} garantizando el
     * balance (Property 16) y la auditoria (Req 38.7).
     */
    @Override
    @Transactional
    public UUID generarPolizaDeEvento(LocalDate fecha, TipoPoliza tipo, String concepto,
                                      String origen, UUID origenId,
                                      List<RenglonPolizaCommand> renglones) {
        PolizaContableDto dto = registrarPoliza(
                new RegistrarPolizaCommand(fecha, tipo, concepto, origen, origenId, renglones));
        return dto.id();
    }

    /**
     * Consulta puntual de una Poliza_Contable del tenant con su desglose (Req 23.3).
     *
     * @param polizaId identificador de la poliza.
     * @return el DTO de la poliza.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public PolizaContableDto consultarPoliza(UUID polizaId) {
        String actor = actorActual();
        return PolizaContableDto.de(cargarPoliza(polizaId, actor));
    }

    /**
     * Listado paginado de Polizas_Contables del tenant con filtros opcionales por
     * rango de fechas y por Cuenta_Contable (Req 38.6).
     *
     * @param desde            fecha minima (inclusiva); {@code null} no filtra.
     * @param hasta            fecha maxima (inclusiva); {@code null} no filtra.
     * @param cuentaContableId Cuenta_Contable a filtrar; {@code null} no filtra.
     * @param pageable         parametros de paginacion ya acotados (20/100).
     * @return la pagina de polizas como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<PolizaContableDto> listarPolizas(LocalDate desde, LocalDate hasta,
                                                 UUID cuentaContableId, Pageable pageable) {
        return polizaContableRepository.buscarConFiltros(desde, hasta, cuentaContableId, pageable)
                .map(PolizaContableDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Construye la {@link PolizaContable} a partir del comando, traduciendo cada
     * {@link RenglonPolizaCommand} a un renglon de cargo o de abono. La fabrica pura
     * del dominio valida el balance (Property 16, Req 38.3, 38.4).
     */
    private PolizaContable construirPoliza(RegistrarPolizaCommand comando, String actor) {
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Poliza_Contable son obligatorios.");
        }
        if (comando.renglones() == null || comando.renglones().isEmpty()) {
            throw new ReglaNegocioException(
                    "La Poliza_Contable debe tener al menos un cargo y un abono.");
        }
        List<MovimientoPoliza> renglones = new ArrayList<>();
        for (RenglonPolizaCommand linea : comando.renglones()) {
            renglones.add(traducirRenglon(linea, actor));
        }
        return PolizaContable.crear(comando.fecha(), comando.tipo(), comando.concepto(),
                comando.origen(), comando.origenId(), renglones, actor);
    }

    /**
     * Traduce un renglon del comando a un {@link MovimientoPoliza} de cargo o de
     * abono, exigiendo cargo XOR abono (exactamente uno positivo).
     */
    private MovimientoPoliza traducirRenglon(RenglonPolizaCommand linea, String actor) {
        if (linea == null || linea.cuentaContableId() == null) {
            throw new ReglaNegocioException(
                    "Cada renglon de la Poliza_Contable debe referenciar una Cuenta_Contable.");
        }
        boolean tieneCargo = linea.cargo() != null && linea.cargo().signum() > 0;
        boolean tieneAbono = linea.abono() != null && linea.abono().signum() > 0;
        if (tieneCargo == tieneAbono) {
            throw new ReglaNegocioException(
                    "Cada renglon de la Poliza_Contable debe ser un cargo o un abono, no ambos ni ninguno.");
        }
        return tieneCargo
                ? MovimientoPoliza.cargo(linea.cuentaContableId(), linea.cargo(), actor)
                : MovimientoPoliza.abono(linea.cuentaContableId(), linea.abono(), actor);
    }

    private CuentaContable cargarCuenta(UUID cuentaId, String actor) {
        if (cuentaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Cuenta_Contable solicitada.");
        }
        return cuentaContableRepository.findById(cuentaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CUENTA, cuentaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Cuenta_Contable solicitada.");
                });
    }

    private PolizaContable cargarPoliza(UUID polizaId, String actor) {
        if (polizaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Poliza_Contable solicitada.");
        }
        return polizaContableRepository.findById(polizaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_POLIZA, polizaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Poliza_Contable solicitada.");
                });
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
