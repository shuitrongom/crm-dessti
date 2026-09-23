package com.dessti.crm.vertical.anuncios.pruebadiseno.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.vertical.anuncios.pruebadiseno.adapter.out.persistence.PruebaDisenoRepository;
import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.EstadoPruebaDiseno;
import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.PruebaDiseno;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de las {@link PruebaDiseno}
 * (Req 15). Replica el patron establecido por {@code ServicioCotizaciones}.
 *
 * <h2>Operaciones (Req 15)</h2>
 * <ul>
 *   <li><strong>generar (Req 15.1):</strong> verifica que la Cotizacion exista en
 *       el tenant (via {@link CotizacionExistentePort}; 404 si no), crea la
 *       version 1 en {@code pendiente}, persiste y audita.</li>
 *   <li><strong>aprobar (Req 15.2, 15.7):</strong> aplica {@code pendiente ->
 *       aprobada} (409 si no esta pendiente), fija el actor y el instante UTC
 *       (Clock) de la decision, y audita version y estados.</li>
 *   <li><strong>rechazar (Req 15.3, 15.7):</strong> aplica {@code pendiente ->
 *       rechazada}, fija actor y UTC, y <em>genera automaticamente</em> una nueva
 *       Prueba_Diseno con numero de version = max(numero_version de la
 *       Cotizacion) + 1 en {@code pendiente} (Property 8); audita ambas.</li>
 *   <li><strong>listar (Req 15.6):</strong> listado paginado (20/100) de las
 *       Prueba_Diseno de una Cotizacion, mas reciente primero.</li>
 * </ul>
 *
 * <h2>Inmutabilidad del historial (Req 15.4)</h2>
 * <p>El servicio NO ofrece operaciones de modificacion ni de borrado de las
 * Prueba_Diseno historicas. El unico cambio permitido es la transicion de estado
 * {@code pendiente -> {aprobada|rechazada}} sobre la version pendiente vigente,
 * gobernada por la maquina de estados pura del dominio; decidir una prueba ya
 * decidida se rechaza con 409. Asi se conserva el historial completo de versiones.</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 15.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada cambio de estado se registra via {@link AuditoriaPort}
 * como evento de tenant con el actor, la version, el estado anterior y el nuevo
 * (Req 15.7). El instante UTC de la decision usa el {@link Clock} inyectado para
 * ser determinista en pruebas.</p>
 */
@Service
public class ServicioPruebasDiseno {

    /** Tipo de recurso de auditoria/RBAC de la Prueba_Diseno. */
    static final String RECURSO_PRUEBA_DISENO = "prueba_diseno";

    private final PruebaDisenoRepository pruebaDisenoRepository;
    private final CotizacionExistentePort cotizacionExistente;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioPruebasDiseno(PruebaDisenoRepository pruebaDisenoRepository,
                                 CotizacionExistentePort cotizacionExistente,
                                 AuditoriaPort auditoria,
                                 Clock clock) {
        this.pruebaDisenoRepository = pruebaDisenoRepository;
        this.cotizacionExistente = cotizacionExistente;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Genera la Prueba_Diseno inicial (version 1, estado {@code pendiente}) de una
     * Cotizacion existente (Req 15.1). Verifica la Cotizacion en el tenant (404 +
     * auditoria del acceso cruzado si no existe, Req 23.3).
     *
     * @param cotizacionId identificador de la Cotizacion existente.
     * @return el DTO de la Prueba_Diseno creada (version 1, {@code pendiente}).
     * @throws RecursoNoEncontradoException si la Cotizacion no existe en el tenant (404).
     * @throws ReglaNegocioException si falta la Cotizacion (422).
     */
    @Transactional
    public PruebaDisenoDto generar(UUID cotizacionId) {
        String actor = actorActual();
        if (cotizacionId == null) {
            throw new ReglaNegocioException(
                    "La Prueba_Diseno debe asociarse a una Cotizacion existente.");
        }
        if (!cotizacionExistente.existeCotizacion(cotizacionId)) {
            auditarAccesoCruzado(actor, "cotizacion", cotizacionId);
            throw new RecursoNoEncontradoException(
                    "No se encontro la Cotizacion indicada para la Prueba_Diseno.");
        }
        PruebaDiseno prueba = PruebaDiseno.generarInicial(cotizacionId, actor);
        PruebaDiseno guardada = pruebaDisenoRepository.save(prueba);
        auditar(actor, "generar", guardada,
                "generada Prueba_Diseno version " + guardada.getNumeroVersion()
                        + " en estado '" + guardada.getEstado().valorBd()
                        + "' [cotizacion=" + cotizacionId + "]",
                null, guardada.getEstado().valorBd());
        return PruebaDisenoDto.de(guardada);
    }

    /**
     * Aprueba una Prueba_Diseno en estado {@code pendiente}, registrando el actor y
     * el instante UTC de la decision (Req 15.2) y auditando la version y los
     * estados (Req 15.7).
     *
     * @param pruebaId identificador de la Prueba_Diseno.
     * @return el DTO de la Prueba_Diseno aprobada.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si no esta
     *         en {@code pendiente} (409, Req 15.4).
     */
    @Transactional
    public PruebaDisenoDto aprobar(UUID pruebaId) {
        String actor = actorActual();
        PruebaDiseno prueba = cargar(pruebaId, actor);
        EstadoPruebaDiseno anterior = prueba.getEstado();
        prueba.aprobar(actor, clock);
        PruebaDiseno guardada = pruebaDisenoRepository.save(prueba);
        auditar(actor, "aprobar", guardada,
                "aprobada Prueba_Diseno version " + guardada.getNumeroVersion(),
                anterior.valorBd(), guardada.getEstado().valorBd());
        return PruebaDisenoDto.de(guardada);
    }

    /**
     * Rechaza una Prueba_Diseno en estado {@code pendiente} y genera
     * <strong>automaticamente</strong> una nueva Prueba_Diseno con numero de
     * version incrementado en 1 y estado {@code pendiente} (Req 15.3, Property 8).
     * Audita tanto el rechazo como la nueva version (Req 15.7).
     *
     * <p>El numero de la nueva version se calcula como
     * {@code max(numero_version de la Cotizacion) + 1}, consultado tras persistir
     * el rechazo, de modo que el versionado sea estrictamente creciente
     * (versionado monotono). La unicidad
     * {@code (tenant_id, cotizacion_id, numero_version)} de V16 lo refuerza en BD.</p>
     *
     * @param pruebaId identificador de la Prueba_Diseno a rechazar.
     * @return el resultado con la version rechazada y la nueva version pendiente.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si no esta
     *         en {@code pendiente} (409, Req 15.4).
     */
    @Transactional
    public ResultadoRechazoPruebaDiseno rechazar(UUID pruebaId) {
        String actor = actorActual();
        PruebaDiseno prueba = cargar(pruebaId, actor);
        UUID cotizacionId = prueba.getCotizacionId();
        EstadoPruebaDiseno anterior = prueba.getEstado();

        prueba.rechazar(actor, clock);
        PruebaDiseno rechazada = pruebaDisenoRepository.save(prueba);
        auditar(actor, "rechazar", rechazada,
                "rechazada Prueba_Diseno version " + rechazada.getNumeroVersion(),
                anterior.valorBd(), rechazada.getEstado().valorBd());

        int siguienteNumero = siguienteNumeroVersion(cotizacionId);
        PruebaDiseno nueva = PruebaDiseno.siguienteVersion(cotizacionId, siguienteNumero, actor);
        PruebaDiseno guardadaNueva = pruebaDisenoRepository.save(nueva);
        auditar(actor, "generar_por_rechazo", guardadaNueva,
                "generada nueva Prueba_Diseno version " + guardadaNueva.getNumeroVersion()
                        + " en estado '" + guardadaNueva.getEstado().valorBd()
                        + "' por rechazo de la version " + rechazada.getNumeroVersion(),
                null, guardadaNueva.getEstado().valorBd());

        return new ResultadoRechazoPruebaDiseno(
                PruebaDisenoDto.de(rechazada), PruebaDisenoDto.de(guardadaNueva));
    }

    /**
     * Listado paginado de las Prueba_Diseno de una Cotizacion (Req 15.6), mas
     * reciente (mayor numero de version) primero.
     *
     * @param cotizacionId Cotizacion cuyas pruebas se listan; obligatorio.
     * @param pageable     parametros de paginacion ya acotados (20/100).
     * @return la pagina de Prueba_Diseno como DTOs.
     * @throws ReglaNegocioException si falta la Cotizacion (422).
     */
    @Transactional(readOnly = true)
    public Page<PruebaDisenoDto> listar(UUID cotizacionId, Pageable pageable) {
        if (cotizacionId == null) {
            throw new ReglaNegocioException("La Cotizacion es obligatoria para listar sus Prueba_Diseno.");
        }
        return pruebaDisenoRepository
                .findByCotizacionIdOrderByNumeroVersionDesc(cotizacionId, pageable)
                .map(PruebaDisenoDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Calcula el numero de la siguiente version para una Cotizacion como
     * {@code max(numero_version) + 1} (Property 8). Si aun no hubiera pruebas
     * (caso teorico, pues el rechazo parte de una existente), arranca en
     * {@link PruebaDiseno#VERSION_INICIAL} + 1.
     */
    private int siguienteNumeroVersion(UUID cotizacionId) {
        Integer maximo = pruebaDisenoRepository.findMaxNumeroVersionByCotizacionId(cotizacionId);
        int base = (maximo == null) ? PruebaDiseno.VERSION_INICIAL : maximo;
        return base + 1;
    }

    private PruebaDiseno cargar(UUID pruebaId, String actor) {
        if (pruebaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Prueba_Diseno solicitada.");
        }
        return pruebaDisenoRepository.findById(pruebaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_PRUEBA_DISENO, pruebaId);
                    throw new RecursoNoEncontradoException("No se encontro la Prueba_Diseno solicitada.");
                });
    }

    private void auditar(String actor, String accion, PruebaDiseno prueba, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_PRUEBA_DISENO,
                detalle + " [id=" + prueba.getId() + ", version=" + prueba.getNumeroVersion() + "]",
                valorAnterior, valorNuevo));
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
