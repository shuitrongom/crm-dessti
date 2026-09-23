package com.dessti.crm.operacion.proyecto.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.ProyectoRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.SitioRepository;
import com.dessti.crm.operacion.proyecto.domain.AvanceFasesSitio;
import com.dessti.crm.operacion.proyecto.domain.DerivacionEstadoProyecto;
import com.dessti.crm.operacion.proyecto.domain.EstadoConsolidadoProyecto;
import com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro;
import com.dessti.crm.operacion.proyecto.domain.Proyecto;
import com.dessti.crm.operacion.proyecto.domain.Sitio;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de los {@link Proyecto} y
 * sus {@link Sitio Sitios} (Req 21). Replica el patron establecido por
 * {@code ServicioOrdenesFabricacion}.
 *
 * <h2>Operaciones (Req 21)</h2>
 * <ul>
 *   <li><strong>crear (Req 21.1, 4.1-4.5):</strong> crea un Proyecto asociado a un
 *       Cliente con nombre 1..200 y audita. La existencia del Cliente en el tenant se
 *       verifica <strong>antes de persistir</strong> mediante {@link ClienteExistentePort}
 *       (ver DECISION de existencia mas abajo): {@code clienteId} nulo &rarr; 422 sin
 *       persistir; Cliente inexistente/no accesible &rarr; 404 + auditoria de acceso
 *       cruzado con recurso {@code cliente}.</li>
 *   <li><strong>agregarSitio (Req 21.2):</strong> 404 si el Proyecto no es
 *       accesible en el tenant; agrega el Sitio vinculado al Proyecto y audita.</li>
 *   <li><strong>consultar (Req 21.4, 23.3, 3.1/3.2/3.5):</strong> carga el Proyecto
 *       y sus Sitios; resuelve el {@link PerfilFasesGiro} del giro del tenant via
 *       {@link PerfilFasesGiroPort}; segun el perfil elige el adaptador de avance
 *       ({@link AvanceSitioPort}): el de anuncios (cuatro fases) para el giro
 *       {@code anuncios-luminosos} o el de produccion del Nucleo (solo produccion)
 *       para el resto de giros; computa el {@link AvanceFasesSitio} de cada Sitio y
 *       deriva el estado consolidado con
 *       {@link DerivacionEstadoProyecto#derivar(PerfilFasesGiro, List)}; 404 +
 *       auditoria si no es accesible.</li>
 *   <li><strong>listar (Req 21.5):</strong> listado paginado (20/100) con filtro
 *       por Cliente; proyeccion de resumen (sin derivar avance por Proyecto).</li>
 * </ul>
 *
 * <h2>Existencia del Cliente (Req 4.1-4.5)</h2>
 * <p>La creacion del Proyecto <strong>verifica la existencia del Cliente</strong> en
 * el tenant mediante el puerto de solo lectura {@link ClienteExistentePort}
 * (definido en el Nucleo, implementado por el modulo comercial) <em>antes de
 * persistir</em>, en lugar de apoyarse en la clave foranea {@code fk_proyecto_cliente}
 * de V25. Esto traduce el caso de un Cliente inexistente o de otro tenant a un
 * <strong>404</strong> claro ({@link RecursoNoEncontradoException}) acompanado de
 * auditoria del <strong>acceso cruzado</strong> con recurso {@code cliente} (Req 4.2),
 * evitando el error de integridad referencial opaco. Un {@code clienteId} nulo se
 * rechaza con <strong>422</strong> ({@link ReglaNegocioException}) sin persistir
 * (Req 4.3). Solo cuando el Cliente existe y es accesible se crea el Proyecto en
 * estado {@code SIN_SITIOS} y se audita la creacion (Req 4.4). El aislamiento por
 * tenant lo garantizan el filtro global de Hibernate y la RLS de PostgreSQL, de modo
 * que un Cliente de otro tenant no se considera existente.</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 21.6)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion de creacion/modificacion se registra via
 * {@link AuditoriaPort} con el actor, la accion, el recurso afectado y la marca
 * temporal (Req 21.6).</p>
 */
@Service
public class ServicioProyectos {

    /** Tipo de recurso de auditoria/RBAC del Proyecto. */
    static final String RECURSO_PROYECTO = "proyecto";

    /**
     * Recurso de auditoria del Sitio. No es un recurso RBAC (no existe en V5): las
     * operaciones de Sitio se autorizan con los permisos de {@code proyecto} (el
     * Sitio se gestiona como parte del agregado Proyecto). Se usa solo como
     * etiqueta de recurso afectado en la auditoria (Req 21.6).
     */
    static final String RECURSO_SITIO = "sitio";

    /** Recurso de auditoria del acceso cruzado al Cliente (Req 4.2). */
    static final String RECURSO_CLIENTE = "cliente";

    private final ProyectoRepository proyectoRepository;
    private final SitioRepository sitioRepository;
    private final ClienteExistentePort clienteExistente;
    private final PerfilFasesGiroPort perfilFasesGiro;
    private final AvanceSitioPort avanceProduccion;
    private final AvanceSitioPort avanceSitioAnuncios;
    private final AuditoriaPort auditoria;

    /**
     * Construye el servicio inyectando <strong>ambos</strong> adaptadores de avance
     * por su nombre de bean (evitando la ambiguedad de {@link AvanceSitioPort}, ya
     * que hay dos implementaciones):
     * <ul>
     *   <li>{@code avanceProduccionAdapter} — Nucleo, solo la fase de produccion
     *       (giros genericos).</li>
     *   <li>{@code avanceSitioAnunciosAdapter} — vertical de anuncios, las cuatro
     *       fases (giro {@code anuncios-luminosos}).</li>
     * </ul>
     * La seleccion en tiempo de ejecucion la resuelve {@link #consultar(UUID)} segun
     * el {@link PerfilFasesGiro} devuelto por {@link PerfilFasesGiroPort}: asi el
     * cableado de Spring es explicito y sin ambiguedad, y la logica de que fases
     * aplican permanece en el dominio/servicio del Nucleo (Decision D5, &sect;A3).
     */
    public ServicioProyectos(ProyectoRepository proyectoRepository,
                             SitioRepository sitioRepository,
                             ClienteExistentePort clienteExistente,
                             PerfilFasesGiroPort perfilFasesGiro,
                             @Qualifier("avanceProduccionAdapter") AvanceSitioPort avanceProduccion,
                             @Qualifier("avanceSitioAnunciosAdapter") AvanceSitioPort avanceSitioAnuncios,
                             AuditoriaPort auditoria) {
        this.proyectoRepository = proyectoRepository;
        this.sitioRepository = sitioRepository;
        this.clienteExistente = clienteExistente;
        this.perfilFasesGiro = perfilFasesGiro;
        this.avanceProduccion = avanceProduccion;
        this.avanceSitioAnuncios = avanceSitioAnuncios;
        this.auditoria = auditoria;
    }

    /**
     * Crea un Proyecto asociado a un Cliente existente con nombre 1..200 (Req 21.1)
     * y audita la creacion (Req 21.6). La existencia del Cliente se verifica antes de
     * persistir mediante {@link ClienteExistentePort} (Req 4.1):
     * <ol>
     *   <li>{@code clienteId} nulo &rarr; 422 sin persistir (Req 4.3).</li>
     *   <li>Cliente inexistente/no accesible en el tenant &rarr; 404 + auditoria de
     *       acceso cruzado con recurso {@code cliente} (Req 4.2).</li>
     *   <li>Cliente accesible &rarr; crea el Proyecto en {@code SIN_SITIOS} y audita
     *       (Req 4.4).</li>
     * </ol>
     *
     * @param comando datos de creacion (Cliente y nombre).
     * @return el DTO detallado del Proyecto creado (sin Sitios todavia; estado
     *         consolidado {@code SIN_SITIOS}).
     * @throws ReglaNegocioException si falta el Cliente o el nombre es invalido (422).
     * @throws RecursoNoEncontradoException si el Cliente no existe o no es accesible
     *         en el tenant (404, Req 4.2).
     */
    @Transactional
    public ProyectoDto crear(CrearProyectoCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.clienteId() == null) {
            throw new ReglaNegocioException(
                    "El Proyecto debe asociarse a un Cliente existente.");
        }

        // Verificacion de existencia del Cliente (Req 4.1/4.2): 404 + auditoria de
        // acceso cruzado con recurso 'cliente' antes de persistir, en lugar de
        // apoyarse en la FK (que produce un error de integridad opaco).
        if (!clienteExistente.existeEnTenant(comando.clienteId())) {
            auditarAccesoCruzado(actor, RECURSO_CLIENTE, comando.clienteId());
            throw new RecursoNoEncontradoException(
                    "No se encontro el Cliente indicado para el Proyecto.");
        }

        Proyecto proyecto = Proyecto.crear(comando.clienteId(), comando.nombre(), actor);
        Proyecto guardado = proyectoRepository.save(proyecto);
        auditar(actor, "crear", RECURSO_PROYECTO, guardado.getId(),
                "creado Proyecto '" + guardado.getNombre() + "' [cliente="
                        + guardado.getClienteId() + "]");
        // Proyecto recien creado: aun no tiene Sitios (Req 21.1).
        return ProyectoDto.consolidado(
                guardado, EstadoConsolidadoProyecto.SIN_SITIOS, List.of());
    }

    /**
     * Agrega un Sitio a un Proyecto existente del tenant (Req 21.2) y audita la
     * modificacion (Req 21.6).
     *
     * @param comando datos del Sitio (Proyecto, nombre y direccion opcional).
     * @return el DTO del Sitio creado.
     * @throws RecursoNoEncontradoException si el Proyecto no es accesible (404,
     *         Req 23.3).
     * @throws ReglaNegocioException si los datos del Sitio son invalidos (422).
     */
    @Transactional
    public SitioDto agregarSitio(AgregarSitioCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.proyectoId() == null) {
            throw new RecursoNoEncontradoException("No se encontro el Proyecto solicitado.");
        }
        Proyecto proyecto = cargarProyecto(comando.proyectoId(), actor);
        Sitio sitio = Sitio.paraProyecto(
                proyecto.getId(), comando.nombre(), comando.direccion(), actor);
        Sitio guardado = sitioRepository.save(sitio);
        auditar(actor, "agregar_sitio", RECURSO_SITIO, guardado.getId(),
                "agregado Sitio '" + guardado.getNombre() + "' al Proyecto ["
                        + proyecto.getId() + "]");
        return SitioDto.de(guardado);
    }

    /**
     * Consulta un Proyecto del tenant devolviendo su estado consolidado derivado del
     * avance de sus Sitios (Req 21.4). Para cada Sitio computa su
     * {@link AvanceFasesSitio} via {@link AvanceSitioPort} y deriva el estado
     * consolidado con {@link DerivacionEstadoProyecto}.
     *
     * @param proyectoId identificador del Proyecto.
     * @return el DTO detallado del Proyecto (estado consolidado + Sitios con avance).
     * @throws RecursoNoEncontradoException si no es accesible (404, Req 23.3).
     */
    @Transactional(readOnly = true)
    public ProyectoDto consultar(UUID proyectoId) {
        String actor = actorActual();
        Proyecto proyecto = cargarProyecto(proyectoId, actor);
        List<Sitio> sitios = sitioRepository.findByProyectoIdOrderByCreatedAtAsc(proyecto.getId());

        // Resuelve el perfil de fases del giro del tenant y, con el, el adaptador de
        // avance adecuado: ANUNCIOS -> las cuatro fases; GENERICO -> solo produccion
        // (Decision D5/D5-b, Req 3.1/3.2/3.5). La derivacion se parametriza tambien
        // por el perfil para que el estado consolidado considere solo las fases
        // aplicables al giro.
        PerfilFasesGiro perfil = perfilFasesGiro.perfilDelTenant();
        AvanceSitioPort avanceSitio = adaptadorDeAvance(perfil);

        List<AvanceFasesSitio> avances = new ArrayList<>(sitios.size());
        List<SitioAvanceDto> sitiosDto = new ArrayList<>(sitios.size());
        for (Sitio sitio : sitios) {
            AvanceFasesSitio avance = avanceSitio.avanceDe(sitio.getId());
            avances.add(avance);
            sitiosDto.add(SitioAvanceDto.de(sitio, avance));
        }
        EstadoConsolidadoProyecto estado = DerivacionEstadoProyecto.derivar(perfil, avances);
        return ProyectoDto.consolidado(proyecto, estado, sitiosDto);
    }

    /**
     * Selecciona el adaptador de {@link AvanceSitioPort} segun el perfil de fases del
     * giro (Decision D5-b, &sect;A3): si el perfil incluye fases exclusivas de
     * anuncios (Levantamiento, Permiso o Instalacion) usa el adaptador de anuncios
     * (las cuatro fases); en caso contrario (perfil generico, solo produccion) usa el
     * adaptador de produccion del Nucleo. Esto evita que un Proyecto generico dependa
     * de los puertos de anuncios y desambigua explicitamente entre los dos beans
     * {@link AvanceSitioPort}.
     *
     * @param perfil perfil de fases del giro; nunca {@code null}.
     * @return el adaptador de avance correspondiente al perfil.
     */
    private AvanceSitioPort adaptadorDeAvance(PerfilFasesGiro perfil) {
        boolean requiereFasesAnuncios =
                perfil.aplica(com.dessti.crm.operacion.proyecto.domain.FaseProyecto.LEVANTAMIENTO)
                        || perfil.aplica(com.dessti.crm.operacion.proyecto.domain.FaseProyecto.PERMISO)
                        || perfil.aplica(com.dessti.crm.operacion.proyecto.domain.FaseProyecto.INSTALACION);
        return requiereFasesAnuncios ? avanceSitioAnuncios : avanceProduccion;
    }

    /**
     * Listado paginado de Proyectos del tenant con filtro opcional por Cliente
     * (Req 21.5). Un {@code clienteId} nulo no restringe; sin coincidencias se
     * devuelve una pagina vacia con total 0. Devuelve la proyeccion de resumen (sin
     * derivar el avance por Proyecto).
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Proyectos como DTOs de resumen.
     */
    @Transactional(readOnly = true)
    public Page<ProyectoDto> listar(UUID clienteId, Pageable pageable) {
        return proyectoRepository.buscarConFiltros(clienteId, pageable)
                .map(ProyectoDto::resumen);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Proyecto cargarProyecto(UUID proyectoId, String actor) {
        if (proyectoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Proyecto solicitado.");
        }
        return proyectoRepository.findById(proyectoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_PROYECTO, proyectoId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el Proyecto solicitado.");
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
                "intento de acceso a " + recurso + " no disponible en el tenant [id="
                        + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
