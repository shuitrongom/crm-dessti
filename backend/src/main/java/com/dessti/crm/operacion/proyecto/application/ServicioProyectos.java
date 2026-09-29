package com.dessti.crm.operacion.proyecto.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.AvanceSitioRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.ProyectoRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.SitioRepository;
import com.dessti.crm.operacion.proyecto.domain.AvanceFasesSitio;
import com.dessti.crm.operacion.proyecto.domain.AvanceSitio;
import com.dessti.crm.operacion.proyecto.domain.DerivacionEstadoMultisitio;
import com.dessti.crm.operacion.proyecto.domain.DerivacionEstadoProyecto;
import com.dessti.crm.operacion.proyecto.domain.EstadoConsolidadoMultisitio;
import com.dessti.crm.operacion.proyecto.domain.EstadoConsolidadoProyecto;
import com.dessti.crm.operacion.proyecto.domain.FaseProyecto;
import com.dessti.crm.operacion.proyecto.domain.FaseSitioGenerica;
import com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro;
import com.dessti.crm.operacion.proyecto.domain.Proyecto;
import com.dessti.crm.operacion.proyecto.domain.Sitio;
import com.dessti.crm.operacion.proyecto.application.evidencia.EvidenciaAvanceConsultaPort;
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
    private final AvanceSitioRepository avanceSitioRepository;
    private final ClienteExistentePort clienteExistente;
    private final PerfilFasesGiroPort perfilFasesGiro;
    private final AvanceSitioPort avanceProduccion;
    private final AvanceSitioPort avanceSitioAnuncios;
    private final PrecondicionesFaseSitioPort precondicionesGenerico;
    private final PrecondicionesFaseSitioPort precondicionesAnuncios;
    private final EvidenciaAvanceConsultaPort evidenciaConsulta;
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
                             AvanceSitioRepository avanceSitioRepository,
                             ClienteExistentePort clienteExistente,
                             PerfilFasesGiroPort perfilFasesGiro,
                             @Qualifier("avanceProduccionAdapter") AvanceSitioPort avanceProduccion,
                             @Qualifier("avanceSitioAnunciosAdapter") AvanceSitioPort avanceSitioAnuncios,
                             @Qualifier("precondicionesFaseSitioGenericoAdapter")
                             PrecondicionesFaseSitioPort precondicionesGenerico,
                             @Qualifier("precondicionesFaseSitioAnunciosAdapter")
                             PrecondicionesFaseSitioPort precondicionesAnuncios,
                             EvidenciaAvanceConsultaPort evidenciaConsulta,
                             AuditoriaPort auditoria) {
        this.proyectoRepository = proyectoRepository;
        this.sitioRepository = sitioRepository;
        this.avanceSitioRepository = avanceSitioRepository;
        this.clienteExistente = clienteExistente;
        this.perfilFasesGiro = perfilFasesGiro;
        this.avanceProduccion = avanceProduccion;
        this.avanceSitioAnuncios = avanceSitioAnuncios;
        this.precondicionesGenerico = precondicionesGenerico;
        this.precondicionesAnuncios = precondicionesAnuncios;
        this.evidenciaConsulta = evidenciaConsulta;
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
     * Edita el nombre de un Proyecto existente del tenant (Req 21.1, 21.6) y audita.
     * No altera el Cliente asociado (inmutable). Devuelve el Proyecto detallado tras el
     * cambio, consistente con {@link #consultar(UUID)}.
     *
     * @param proyectoId identificador del Proyecto.
     * @param nombre     nuevo nombre; obligatorio (1..200).
     * @return el DTO detallado del Proyecto tras el cambio.
     * @throws RecursoNoEncontradoException si el Proyecto no es accesible (404).
     * @throws ReglaNegocioException si el nombre es invalido (422).
     */
    @Transactional
    public ProyectoDto editarProyecto(UUID proyectoId, String nombre) {
        String actor = actorActual();
        Proyecto proyecto = cargarProyecto(proyectoId, actor);
        String antes = proyecto.getNombre();
        proyecto.renombrar(nombre, actor);
        Proyecto guardado = proyectoRepository.save(proyecto);
        auditar(actor, "actualizar", RECURSO_PROYECTO, guardado.getId(),
                "renombrado Proyecto de '" + antes + "' a '" + guardado.getNombre() + "'");
        return consultar(guardado.getId());
    }

    /**
     * Edita los datos descriptivos de un Sitio (nombre y direccion) de un Proyecto del
     * tenant (Req 21.2, 21.6) y audita. Verifica que el Sitio pertenezca al Proyecto.
     * Devuelve el Proyecto detallado tras el cambio.
     *
     * @param proyectoId Proyecto al que pertenece el Sitio (para verificar acceso).
     * @param sitioId    identificador del Sitio.
     * @param nombre     nuevo nombre del Sitio; obligatorio (1..200).
     * @param direccion  nueva direccion; opcional.
     * @return el DTO detallado del Proyecto tras el cambio.
     * @throws RecursoNoEncontradoException si el Proyecto o el Sitio no son accesibles (404).
     * @throws ReglaNegocioException si los datos del Sitio son invalidos (422).
     */
    @Transactional
    public ProyectoDto editarSitio(UUID proyectoId, UUID sitioId, String nombre, String direccion) {
        String actor = actorActual();
        Proyecto proyecto = cargarProyecto(proyectoId, actor);
        Sitio sitio = sitioRepository.findById(sitioId)
                .filter(s -> s.getProyectoId().equals(proyecto.getId()))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el Sitio solicitado en el Proyecto."));
        sitio.editar(nombre, direccion, actor);
        Sitio guardado = sitioRepository.save(sitio);
        auditar(actor, "editar_sitio", RECURSO_SITIO, guardado.getId(),
                "editado Sitio '" + guardado.getNombre() + "' del Proyecto ["
                        + proyecto.getId() + "]");
        return consultar(proyecto.getId());
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

        // Bifurcacion por giro (Req 3.1/3.2): el giro anuncios usa el pipeline clasico
        // de cuatro fases DERIVADAS de otros modulos; el resto de giros usan el
        // pipeline multi-sitio GENERICO con la fase operativa MATERIALIZADA y editable
        // de cada Sitio (avance_sitio, V78). Ambos pipelines son independientes.
        PerfilFasesGiro perfil = perfilFasesGiro.perfilDelTenant();
        if (perfil.aplica(FaseProyecto.LEVANTAMIENTO)
                || perfil.aplica(FaseProyecto.PERMISO)
                || perfil.aplica(FaseProyecto.INSTALACION)) {
            return consultarAnuncios(proyecto, sitios, perfil);
        }
        return consultarMultisitio(proyecto, sitios);
    }

    /** Consulta detallada para el giro anuncios (cuatro fases derivadas). */
    private ProyectoDto consultarAnuncios(Proyecto proyecto, List<Sitio> sitios,
                                          PerfilFasesGiro perfil) {
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
     * Consulta detallada multi-sitio para giros genericos (Req 3.2): compone la fase
     * operativa materializada de cada Sitio (avance_sitio; PENDIENTE si aun no existe)
     * y deriva el estado consolidado con {@link DerivacionEstadoMultisitio}.
     */
    private ProyectoDto consultarMultisitio(Proyecto proyecto, List<Sitio> sitios) {
        List<UUID> ids = new ArrayList<>(sitios.size());
        for (Sitio sitio : sitios) {
            ids.add(sitio.getId());
        }
        // Carga los avances existentes de todos los Sitios en una sola consulta.
        Map<UUID, AvanceSitio> avancePorSitio = new HashMap<>();
        if (!ids.isEmpty()) {
            for (AvanceSitio avance : avanceSitioRepository.findBySitioIdIn(ids)) {
                avancePorSitio.put(avance.getSitioId(), avance);
            }
        }

        List<FaseSitioGenerica> fases = new ArrayList<>(sitios.size());
        List<SitioFaseDto> sitiosDto = new ArrayList<>(sitios.size());
        for (Sitio sitio : sitios) {
            AvanceSitio avance = avancePorSitio.get(sitio.getId());
            fases.add(avance == null ? FaseSitioGenerica.PENDIENTE : avance.getFase());
            sitiosDto.add(SitioFaseDto.de(sitio, avance));
        }
        EstadoConsolidadoMultisitio estado = DerivacionEstadoMultisitio.derivar(fases);
        return ProyectoDto.multisitio(proyecto, estado, sitiosDto);
    }

    /**
     * Avanza la fase operativa generica de un Sitio de un Proyecto multi-sitio siguiendo
     * la maquina lineal (Req 3.2) y aplicando las precondiciones de negocio de la fase
     * destino (Req 3-bis). Operacion de uso comun ({@code proyecto:actualizar}).
     * Idempotente en la creacion del avance: si el Sitio aun no tiene fila en
     * {@code avance_sitio}, se crea en {@code PENDIENTE} y luego se transita.
     *
     * <p>Segun el {@link PerfilFasesGiro} del tenant, exige: para {@code en_preparacion}
     * un Levantamiento_Sitio completado; para {@code en_instalacion} ademas un
     * Permiso_Instalacion aprobado y vigente; para {@code entregado} (desde
     * {@code en_instalacion}) una evidencia aprobada de instalacion. En giros genericos
     * (sin esas fases) solo rige la maquina lineal (Req 3-bis.4).</p>
     *
     * @param proyectoId   Proyecto al que pertenece el Sitio (para verificar acceso).
     * @param sitioId      Sitio cuya fase se avanza.
     * @param destino      fase destino (posterior o igual a la actual).
     * @param nota         nota opcional del avance.
     * @param evidenciaUrl referencia opcional a la evidencia que respalda la fase.
     * @return el DTO detallado multi-sitio del Proyecto tras el cambio.
     * @throws RecursoNoEncontradoException si el Proyecto o el Sitio no son accesibles (404).
     * @throws ReglaNegocioException si no se cumple una precondicion de la fase destino
     *         (422, Req 3-bis.1/2/3); el estado se conserva.
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la transicion
     *         de fase no respeta la secuencia lineal (409).
     */
    @Transactional
    public ProyectoDto avanzarAvanceSitio(UUID proyectoId, UUID sitioId,
                                          FaseSitioGenerica destino, String nota,
                                          String evidenciaUrl) {
        return cambiarAvanceSitio(proyectoId, sitioId, destino, nota, evidenciaUrl, false);
    }

    /**
     * Corrige (incluido RETROCESO) la fase de un Sitio de un Proyecto multi-sitio
     * (Req 3.2). Operacion administrativa gobernada por {@code proyecto:cambiar_estado};
     * no aplica la restriccion de la maquina lineal ni las precondiciones de negocio del
     * avance (Req 3-bis.5), para deshacer avances marcados por error. Exige un
     * <strong>motivo</strong> (nota) no vacio que explique el ajuste, y se audita como
     * correccion.
     *
     * @param proyectoId   Proyecto al que pertenece el Sitio.
     * @param sitioId      Sitio cuya fase se corrige.
     * @param destino      fase destino (puede ser anterior a la actual).
     * @param nota         motivo de la correccion; obligatorio (no vacio, Req 3-bis.5).
     * @param evidenciaUrl referencia opcional a la evidencia.
     * @return el DTO detallado multi-sitio del Proyecto tras la correccion.
     * @throws RecursoNoEncontradoException si el Proyecto o el Sitio no son accesibles (404).
     * @throws ReglaNegocioException si falta el motivo de la correccion (422, Req 3-bis.5).
     */
    @Transactional
    public ProyectoDto corregirAvanceSitio(UUID proyectoId, UUID sitioId,
                                           FaseSitioGenerica destino, String nota,
                                           String evidenciaUrl) {
        return cambiarAvanceSitio(proyectoId, sitioId, destino, nota, evidenciaUrl, true);
    }

    /**
     * Nucleo compartido de avanzar/corregir la fase de un Sitio. Verifica el acceso al
     * Proyecto y al Sitio (404), crea el avance en {@code PENDIENTE} si aun no existe
     * (idempotente), aplica el cambio via el dominio (que valida la maquina de estados
     * salvo en correccion), persiste y audita.
     *
     * @param esCorreccion {@code true} permite retroceder/saltar fases (correccion);
     *                     {@code false} exige avance lineal.
     */
    private ProyectoDto cambiarAvanceSitio(UUID proyectoId, UUID sitioId,
                                           FaseSitioGenerica destino, String nota,
                                           String evidenciaUrl, boolean esCorreccion) {
        String actor = actorActual();
        Proyecto proyecto = cargarProyecto(proyectoId, actor);
        Sitio sitio = sitioRepository.findById(sitioId)
                .filter(s -> s.getProyectoId().equals(proyecto.getId()))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el Sitio solicitado en el Proyecto."));

        AvanceSitio avance = avanceSitioRepository.findBySitioId(sitio.getId())
                .orElseGet(() -> AvanceSitio.inicial(sitio.getId(), actor));

        if (esCorreccion) {
            // Correccion administrativa (Req 3-bis.5): exige un motivo (nota) no vacio
            // para dejar auditada la razon del ajuste. No aplica las precondiciones de
            // avance (permite retroceder/saltar fases para deshacer errores), pero
            // siempre queda registrada con su motivo.
            if (nota == null || nota.isBlank()) {
                throw new ReglaNegocioException(
                        "La correccion de fase requiere un motivo que explique el ajuste.");
            }
            avance.corregirFase(destino, nota, evidenciaUrl, actor);
        } else {
            // Avance de uso comun (Req 3-bis.1/2/3): aplica las precondiciones de
            // negocio de la fase destino, respetando el perfil de giro (las de
            // levantamiento/permiso solo cuando el giro las habilita). El estado se
            // conserva ante cualquier rechazo (la excepcion aborta antes de mutar).
            validarPrecondicionesAvance(sitio.getId(), avance, destino);
            avance.avanzarFase(destino, nota, evidenciaUrl, actor);
        }
        avanceSitioRepository.save(avance);
        auditar(actor, esCorreccion ? "corregir_avance_sitio" : "avanzar_avance_sitio",
                RECURSO_SITIO, sitio.getId(),
                "fase del Sitio " + (esCorreccion ? "corregida" : "avanzada") + " a '"
                        + destino.valorBd() + "' en el Proyecto [" + proyecto.getId() + "]");
        return consultarMultisitio(proyecto,
                sitioRepository.findByProyectoIdOrderByCreatedAtAsc(proyecto.getId()));
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
     * Selecciona el adaptador de {@link PrecondicionesFaseSitioPort} segun el perfil
     * de fases del giro (patron D5-b, &sect;A3-bis): el de anuncios (que consulta
     * levantamiento y permiso vigente reales) cuando el perfil incluye fases de
     * anuncios; el generico (que no bloquea) en cualquier otro caso.
     *
     * @param perfil perfil de fases del giro; nunca {@code null}.
     * @return el adaptador de precondiciones correspondiente al perfil.
     */
    private PrecondicionesFaseSitioPort precondicionesDe(PerfilFasesGiro perfil) {
        boolean requiereFasesAnuncios =
                perfil.aplica(com.dessti.crm.operacion.proyecto.domain.FaseProyecto.LEVANTAMIENTO)
                        || perfil.aplica(com.dessti.crm.operacion.proyecto.domain.FaseProyecto.PERMISO)
                        || perfil.aplica(com.dessti.crm.operacion.proyecto.domain.FaseProyecto.INSTALACION);
        return requiereFasesAnuncios ? precondicionesAnuncios : precondicionesGenerico;
    }

    /**
     * Aplica las precondiciones de negocio del <strong>avance de uso comun</strong> de
     * la fase de un Sitio (Req 3-bis.1/2/3), respetando el perfil de giro del tenant.
     * Solo se invoca desde {@code avanzarAvanceSitio} (no desde la correccion):
     * <ul>
     *   <li>destino {@code en_preparacion} + el perfil habilita Levantamiento &rarr;
     *       exige Levantamiento_Sitio completado (Req 3-bis.1).</li>
     *   <li>destino {@code en_instalacion} + el perfil habilita Permiso/Instalacion
     *       &rarr; exige Levantamiento completado y Permiso_Instalacion aprobado y
     *       <strong>vigente</strong> (Req 3-bis.2).</li>
     *   <li>destino {@code entregado} desde {@code en_instalacion} &rarr; exige al menos
     *       una evidencia aprobada de la fase de instalacion (Req 3-bis.3).</li>
     * </ul>
     * Con perfil generico las precondiciones de levantamiento/permiso no aplican (el
     * adaptador generico devuelve {@code true}); el avance solo respeta la maquina
     * lineal. Ante un requisito no cumplido lanza {@link ReglaNegocioException} (422)
     * antes de mutar la fase, de modo que el estado se conserva (Req 3-bis.6).
     *
     * @param sitioId identificador del Sitio (para consultar precondiciones por Sitio).
     * @param avance  avance actual del Sitio (para conocer la fase de origen).
     * @param destino fase destino pretendida.
     * @throws ReglaNegocioException si falta alguna precondicion de la fase destino (422).
     */
    private void validarPrecondicionesAvance(UUID sitioId, AvanceSitio avance,
                                             FaseSitioGenerica destino) {
        PerfilFasesGiro perfil = perfilFasesGiro.perfilDelTenant();
        boolean giroConLevantamiento =
                perfil.aplica(com.dessti.crm.operacion.proyecto.domain.FaseProyecto.LEVANTAMIENTO);
        boolean giroConPermisoInstalacion =
                perfil.aplica(com.dessti.crm.operacion.proyecto.domain.FaseProyecto.PERMISO)
                        || perfil.aplica(com.dessti.crm.operacion.proyecto.domain.FaseProyecto.INSTALACION);
        PrecondicionesFaseSitioPort precondiciones = precondicionesDe(perfil);

        if (destino == FaseSitioGenerica.EN_PREPARACION && giroConLevantamiento
                && !precondiciones.sitioTieneLevantamientoCompletado(sitioId)) {
            throw new ReglaNegocioException(
                    "Para preparar la sucursal se requiere un Levantamiento_Sitio completado. "
                            + "Completa el levantamiento del sitio antes de avanzar, o usa la "
                            + "correccion de fase si necesitas ajustar administrativamente.");
        }

        if (destino == FaseSitioGenerica.EN_INSTALACION && giroConPermisoInstalacion) {
            if (!precondiciones.sitioTieneLevantamientoCompletado(sitioId)) {
                throw new ReglaNegocioException(
                        "Para pasar la sucursal a instalacion se requiere un Levantamiento_Sitio "
                                + "completado. Completa el levantamiento del sitio antes de avanzar, "
                                + "o usa la correccion de fase si necesitas ajustar administrativamente.");
            }
            if (!precondiciones.sitioTienePermisoVigente(sitioId)) {
                throw new ReglaNegocioException(
                        "Para pasar la sucursal a instalacion se requiere un Permiso_Instalacion "
                                + "aprobado y vigente (no vencido). Tramita o renueva el permiso antes "
                                + "de avanzar, o usa la correccion de fase si necesitas ajustar "
                                + "administrativamente.");
            }
        }

        if (destino == FaseSitioGenerica.ENTREGADO
                && avance.getFase() == FaseSitioGenerica.EN_INSTALACION
                && !evidenciaConsulta.tieneEvidenciaAprobada(
                        avance.getId(), FaseSitioGenerica.EN_INSTALACION)) {
            throw new ReglaNegocioException(
                    "Para entregar la sucursal se requiere al menos una evidencia aprobada de la "
                            + "fase de instalacion. Sube la evidencia y espera su aprobacion, o usa la "
                            + "correccion de fase si necesitas ajustar administrativamente.");
        }
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
