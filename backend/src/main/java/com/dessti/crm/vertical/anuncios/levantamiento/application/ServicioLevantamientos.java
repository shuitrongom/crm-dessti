package com.dessti.crm.vertical.anuncios.levantamiento.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.vertical.anuncios.levantamiento.adapter.out.persistence.LevantamientoFotoRepository;
import com.dessti.crm.vertical.anuncios.levantamiento.adapter.out.persistence.LevantamientoSitioRepository;
import com.dessti.crm.vertical.anuncios.levantamiento.domain.EstadoLevantamiento;
import com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoFoto;
import com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoSitio;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de los
 * {@link LevantamientoSitio} (Req 16). Replica el patron establecido por
 * {@code ServicioOrdenesFabricacion} y {@code ServicioPruebasDiseno}.
 *
 * <h2>Operaciones (Req 16)</h2>
 * <ul>
 *   <li><strong>crear (Req 16.1, 16.2, 16.7):</strong> valida los datos
 *       obligatorios (dominio), verifica que los vinculos opcionales
 *       (Cotizacion/Orden_Fabricacion) existan en el tenant si se proporcionan
 *       (404 + auditoria del acceso cruzado si no, Req 23.3), crea el
 *       Levantamiento en {@code en_proceso}, persiste y audita.</li>
 *   <li><strong>agregarFotos (Req 16.3, 16.7):</strong> adjunta fotografias al
 *       Levantamiento (404 si no es accesible) y audita.</li>
 *   <li><strong>completar (Req 16.4, 16.7):</strong> aplica {@code en_proceso ->
 *       completado} fijando actor y marca UTC (Clock inyectado); 409 si ya estaba
 *       completado; audita el estado anterior y el nuevo.</li>
 *   <li><strong>consultar (Req 23.3):</strong> 404 + auditoria del intento si no
 *       es accesible.</li>
 *   <li><strong>listar (Req 16.6):</strong> listado paginado (20/100) con filtros
 *       por estado y por Sitio; sin coincidencias devuelve una pagina vacia.</li>
 * </ul>
 *
 * <h2>Guarda de programacion de instalacion (Req 16.5)</h2>
 * <p>La guarda que exige un Levantamiento {@code completado} para programar la
 * instalacion de un Sitio la expone el {@link LevantamientoCompletadoPort}
 * (implementado en este modulo) que consumira el bloque 22 (tarea 22.1).</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 16.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se registra via
 * {@link AuditoriaPort} como evento de tenant con el actor derivado del contexto;
 * el completar incluye el estado anterior y el nuevo (Req 16.7). El instante UTC
 * de la finalizacion usa el {@link Clock} inyectado para ser determinista en
 * pruebas.</p>
 */
@Service
public class ServicioLevantamientos {

    /** Tipo de recurso de auditoria/RBAC del Levantamiento_Sitio. */
    static final String RECURSO_LEVANTAMIENTO = "levantamiento_sitio";

    private final LevantamientoSitioRepository levantamientoSitioRepository;
    private final LevantamientoFotoRepository levantamientoFotoRepository;
    private final EnlacesLevantamientoPort enlaces;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioLevantamientos(LevantamientoSitioRepository levantamientoSitioRepository,
                                  LevantamientoFotoRepository levantamientoFotoRepository,
                                  EnlacesLevantamientoPort enlaces,
                                  AuditoriaPort auditoria,
                                  Clock clock) {
        this.levantamientoSitioRepository = levantamientoSitioRepository;
        this.levantamientoFotoRepository = levantamientoFotoRepository;
        this.enlaces = enlaces;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Crea un Levantamiento_Sitio con los datos obligatorios y estado inicial
     * {@code en_proceso} (Req 16.1). Si se proporcionan vinculos opcionales de
     * Cotizacion u Orden_Fabricacion, verifica que existan en el tenant (404 +
     * auditoria del acceso cruzado si no, Req 16.2, 23.3). Audita el alta (Req 16.7).
     *
     * @param comando datos del levantamiento a crear; obligatorio.
     * @return el DTO del Levantamiento creado (estado {@code en_proceso}).
     * @throws ReglaNegocioException si el comando es nulo o falta un dato
     *         obligatorio (422, Req 16.1).
     * @throws RecursoNoEncontradoException si un vinculo proporcionado no existe en
     *         el tenant (404, Req 16.2, 23.3).
     */
    @Transactional
    public LevantamientoSitioDto crear(CrearLevantamientoCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del Levantamiento_Sitio son obligatorios.");
        }

        // Verificacion de existencia de los vinculos opcionales en el tenant (Req 16.2).
        if (comando.cotizacionId() != null && !enlaces.existeCotizacion(comando.cotizacionId())) {
            auditarAccesoCruzado(actor, "cotizacion", comando.cotizacionId());
            throw new RecursoNoEncontradoException(
                    "No se encontro la Cotizacion indicada para el Levantamiento_Sitio.");
        }
        if (comando.ordenFabricacionId() != null
                && !enlaces.existeOrdenFabricacion(comando.ordenFabricacionId())) {
            auditarAccesoCruzado(actor, "orden_fabricacion", comando.ordenFabricacionId());
            throw new RecursoNoEncontradoException(
                    "No se encontro la Orden_Fabricacion indicada para el Levantamiento_Sitio.");
        }

        LevantamientoSitio levantamiento = LevantamientoSitio.crear(
                comando.mediciones(), comando.tipoSuperficie(), comando.condicionesElectricas(),
                comando.sitioId(), comando.cotizacionId(), comando.ordenFabricacionId(), actor);
        LevantamientoSitio guardado = levantamientoSitioRepository.save(levantamiento);

        auditar(actor, "crear", guardado.getId(),
                "creado Levantamiento_Sitio en estado '" + guardado.getEstado().valorBd()
                        + "' [sitio=" + guardado.getSitioId()
                        + ", cotizacion=" + guardado.getCotizacionId()
                        + ", orden_fabricacion=" + guardado.getOrdenFabricacionId() + "]",
                null, guardado.getEstado().valorBd());
        return LevantamientoSitioDto.de(guardado);
    }

    /**
     * Adjunta una o mas fotografias a un Levantamiento_Sitio (Req 16.3) y audita la
     * accion (Req 16.7). Las fotografias se conservan vinculadas al Levantamiento.
     *
     * @param levantamientoId identificador del Levantamiento_Sitio.
     * @param referencias     URLs o claves de las fotografias; obligatorio y no vacio.
     * @return la lista de DTOs de las fotografias adjuntadas.
     * @throws RecursoNoEncontradoException si el Levantamiento no es accesible (404).
     * @throws ReglaNegocioException si no se proporciona ninguna referencia (422).
     */
    @Transactional
    public List<LevantamientoFotoDto> agregarFotos(UUID levantamientoId, List<String> referencias) {
        String actor = actorActual();
        if (referencias == null || referencias.isEmpty()) {
            throw new ReglaNegocioException(
                    "Debe proporcionarse al menos una fotografia para adjuntar.");
        }
        LevantamientoSitio levantamiento = cargar(levantamientoId, actor);

        List<LevantamientoFoto> guardadas = referencias.stream()
                .map(ref -> levantamientoFotoRepository.save(
                        LevantamientoFoto.paraLevantamiento(levantamiento, ref, actor)))
                .toList();

        auditar(actor, "agregar_fotos", levantamiento.getId(),
                "adjuntadas " + guardadas.size() + " fotografia(s) al Levantamiento_Sitio",
                null, null);
        return guardadas.stream().map(LevantamientoFotoDto::de).toList();
    }

    /**
     * Marca un Levantamiento_Sitio como {@code completado}, fijando el actor y el
     * instante UTC de la finalizacion (Req 16.4) y auditando el estado anterior y
     * el nuevo (Req 16.7).
     *
     * @param levantamientoId identificador del Levantamiento_Sitio.
     * @return el DTO del Levantamiento completado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si ya
     *         estaba completado (409, Req 16.4).
     */
    @Transactional
    public LevantamientoSitioDto completar(UUID levantamientoId) {
        String actor = actorActual();
        LevantamientoSitio levantamiento = cargar(levantamientoId, actor);
        EstadoLevantamiento anterior = levantamiento.getEstado();
        levantamiento.completar(actor, clock);
        LevantamientoSitio guardado = levantamientoSitioRepository.save(levantamiento);
        auditar(actor, "completar", guardado.getId(),
                "completado Levantamiento_Sitio por " + actor,
                anterior.valorBd(), guardado.getEstado().valorBd());
        return LevantamientoSitioDto.de(guardado);
    }

    /**
     * Consulta puntual de un Levantamiento_Sitio del tenant (Req 23.3).
     *
     * @param levantamientoId identificador del Levantamiento_Sitio.
     * @return el DTO del Levantamiento_Sitio.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public LevantamientoSitioDto consultar(UUID levantamientoId) {
        String actor = actorActual();
        return LevantamientoSitioDto.de(cargar(levantamientoId, actor));
    }

    /**
     * Consulta puntual de un Levantamiento_Sitio del tenant enriquecida con sus
     * fotografias vinculadas (Req 7.1, 7.2; diseno §C1). La lista de fotos es vacia
     * si el Levantamiento no tiene ninguna. 404 + auditoria del acceso cruzado si no
     * es accesible (Req 7.3, 23.3).
     *
     * @param levantamientoId identificador del Levantamiento_Sitio.
     * @return el DTO de detalle con las fotos vinculadas (lista vacia si no hay).
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public LevantamientoSitioDetalleDto consultarDetalle(UUID levantamientoId) {
        String actor = actorActual();
        LevantamientoSitio levantamiento = cargar(levantamientoId, actor);
        List<LevantamientoFotoDto> fotos = fotosVinculadas(levantamiento.getId());
        return LevantamientoSitioDetalleDto.de(levantamiento, fotos);
    }

    /**
     * Devuelve las fotografias vinculadas a un Levantamiento_Sitio accesible del
     * tenant (Req 7.2; diseno §C1). Verifica primero el acceso al Levantamiento
     * (404 + auditoria del acceso cruzado si no es accesible, Req 7.3, 23.3) y luego
     * lee el {@link LevantamientoFotoRepository} acotado al tenant vigente. Devuelve
     * una lista vacia cuando el Levantamiento no tiene fotos.
     *
     * @param levantamientoId identificador del Levantamiento_Sitio.
     * @return la lista de DTOs de las fotografias vinculadas (posiblemente vacia).
     * @throws RecursoNoEncontradoException si el Levantamiento no es accesible (404).
     */
    @Transactional(readOnly = true)
    public List<LevantamientoFotoDto> fotosDe(UUID levantamientoId) {
        String actor = actorActual();
        LevantamientoSitio levantamiento = cargar(levantamientoId, actor);
        return fotosVinculadas(levantamiento.getId());
    }

    private List<LevantamientoFotoDto> fotosVinculadas(UUID levantamientoId) {
        return levantamientoFotoRepository
                .findByLevantamientoIdOrderByCreatedAtAsc(levantamientoId).stream()
                .map(LevantamientoFotoDto::de)
                .toList();
    }

    /**
     * Listado paginado de Levantamientos del tenant con filtros opcionales por
     * estado y por Sitio (Req 16.6). Un filtro nulo no restringe; sin coincidencias
     * se devuelve una pagina vacia con total 0.
     *
     * @param estado   etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param sitioId  Sitio a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Levantamientos como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<LevantamientoSitioDto> listar(String estado, UUID sitioId, Pageable pageable) {
        EstadoLevantamiento filtro =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return levantamientoSitioRepository.buscarConFiltros(filtro, sitioId, pageable)
                .map(LevantamientoSitioDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private LevantamientoSitio cargar(UUID levantamientoId, String actor) {
        if (levantamientoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Levantamiento_Sitio solicitado.");
        }
        return levantamientoSitioRepository.findById(levantamientoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_LEVANTAMIENTO, levantamientoId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el Levantamiento_Sitio solicitado.");
                });
    }

    private EstadoLevantamiento interpretarEstado(String etiqueta) {
        try {
            return EstadoLevantamiento.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Levantamiento_Sitio desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, UUID levantamientoId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_LEVANTAMIENTO,
                detalle + " [id=" + levantamientoId + "]", valorAnterior, valorNuevo));
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
