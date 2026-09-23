package com.dessti.crm.platform.giros.application;

import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.giros.GiroDto;
import com.dessti.crm.platform.giros.adapter.out.persistence.GiroRepository;
import com.dessti.crm.platform.giros.domain.Giro;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.vertical.ContratoVertical;
import com.dessti.crm.platform.vertical.RegistroVerticales;

/**
 * Servicio de aplicacion de <strong>plataforma</strong> que gobierna el catalogo
 * de Giros (verticales de negocio) administrado por el {@code super_admin}
 * (Req 1). El Giro es un dato de plataforma (sin {@code tenant_id}, sin RLS,
 * Req 8.4): un Giro no pertenece a ninguna Empresa, es compartido por todas.
 *
 * <h2>Operaciones (Req 1.1)</h2>
 * <ul>
 *   <li><strong>crear:</strong> da de alta un Giro con su clave canonica
 *       normalizada por el dominio ({@link Giro#normalizarClave(String)}),
 *       rechazando la clave duplicada e informando cual es (Req 1.3). Persiste y
 *       audita (Req 1.7).</li>
 *   <li><strong>activar / desactivar:</strong> cargan el Giro por id (404 si no
 *       existe), aplican el cambio de estado del dominio, guardan y auditan
 *       (Req 1.7). La desactivacion comprueba antes que el Giro no este en uso
 *       por ninguna Empresa (Req 1.5).</li>
 *   <li><strong>listar:</strong> listado paginado, opcionalmente filtrado por
 *       estado {@code activo} (Req 1.6). El acotado del tamano de pagina
 *       (20/100) lo aplica el controlador via
 *       {@code platform.web.pagination.PageRequestFactory}, exactamente como
 *       {@code ServicioEmpresas.listarEmpresas}; este servicio recibe un
 *       {@link Pageable} ya acotado.</li>
 * </ul>
 *
 * <h2>Convenciones reutilizadas del proyecto</h2>
 * <p>Este servicio replica <em>exactamente</em> el patron de
 * {@code platform.empresas.ServicioEmpresas} y
 * {@code platform.security.roles.ServicioRoles}:</p>
 * <ul>
 *   <li><strong>Excepciones:</strong> el conflicto de <em>clave duplicada</em> se
 *       traduce a {@link ConflictoUnicidadException} (HTTP 409), la misma que
 *       usan {@code ServicioEmpresas} (RFC duplicado) y {@code ServicioRoles}
 *       (nombre de rol duplicado) para toda colision de unicidad. Se prioriza
 *       esta consistencia sobre la mencion de 422 en el diseno para no introducir
 *       una traduccion distinta para un conflicto de unicidad ya establecido en
 *       el proyecto. El Giro inexistente produce {@link RecursoNoEncontradoException}
 *       (HTTP 404) y la violacion de una regla de negocio ("Giro en uso")
 *       produce {@link ReglaNegocioException} (HTTP 422).</li>
 *   <li><strong>Actor autenticado:</strong> se resuelve con el helper
 *       {@link AutenticacionActual#obtener()}, cayendo a {@code "sistema"} si no
 *       hay contexto de seguridad, igual que en los servicios de referencia.</li>
 *   <li><strong>Auditoria (Req 1.7):</strong> cada operacion se registra via
 *       {@link AuditoriaPort} como evento de <em>plataforma</em>
 *       ({@link EventoAuditoria#dePlataforma}), pues el Giro es recurso de
 *       plataforma (sin tenant), replicando {@code ServicioEmpresas}.</li>
 *   <li><strong>Transacciones:</strong> escritura {@code @Transactional};
 *       lectura {@code @Transactional(readOnly = true)}.</li>
 *   <li><strong>Autorizacion:</strong> el proyecto coloca {@code @PreAuthorize}
 *       en la capa de <em>controlador</em> (ver {@code EmpresaController},
 *       {@code PlanController}, {@code ServicioRoles} sin anotaciones de
 *       seguridad), no en el servicio. Por coherencia, este servicio <em>no</em>
 *       declara {@code @PreAuthorize}; la proteccion RBAC
 *       ({@code @autorizador.tiene('giro', ...)}) la aporta el
 *       {@code GiroController} (tarea 2.6).</li>
 * </ul>
 *
 * <h2>Giro en uso (Req 1.5)</h2>
 * <p>Antes de desactivar, se consulta {@link ConteoEmpresasPorGiroPort}: si el
 * conteo es mayor que cero, la desactivacion se rechaza informando el numero de
 * Empresas que aun lo usan. Mientras la relacion {@code empresa.giro_id} no
 * exista (llega en la tarea 4.x), el puerto usa su implementacion por defecto que
 * devuelve {@code 0}, por lo que ningun Giro se considera en uso todavia.</p>
 *
 * <h2>Completitud del Giro (Base vs Completo)</h2>
 * <p>Al proyectar cada Giro a {@link GiroDto}, el servicio lo enriquece con su
 * grado de completitud consultando el {@link RegistroVerticales} (la unica fuente
 * de verdad de que giros tienen un {@code ContratoVertical} programado):</p>
 * <ul>
 *   <li>{@code tieneReglasNegocio} = {@code true} si la clave del Giro esta en
 *       {@link RegistroVerticales#girosRegistrados()} (Giro "Completo"), es decir,
 *       existe un vertical que le aporta modulos y reglas de negocio propios.</li>
 *   <li>{@code modulosEspecificos} = numero de modulos atribuidos al Giro por su
 *       vertical (de {@link RegistroVerticales#porGiro(String)}), o {@code 0} si
 *       el Giro es "Base" (sin vertical registrado).</li>
 * </ul>
 * <p>La proyeccion enriquecida vive aqui (y no en {@code GiroDto.de(Giro)}, que es
 * pura) porque solo el servicio conoce el registro. Asi el {@code GiroController}
 * devuelve siempre DTOs con la completitud poblada, sin acoplarse al registro.</p>
 */
@Service
public class ServicioGiros {

    /** Recurso de auditoria/RBAC de nivel plataforma asociado al Giro (Req 1.7). */
    static final String RECURSO_GIRO = "giro";

    private final GiroRepository giroRepository;
    private final ConteoEmpresasPorGiroPort conteoEmpresasPorGiro;
    private final AuditoriaPort auditoria;
    private final RegistroVerticales registroVerticales;

    public ServicioGiros(GiroRepository giroRepository,
                         ConteoEmpresasPorGiroPort conteoEmpresasPorGiro,
                         AuditoriaPort auditoria,
                         RegistroVerticales registroVerticales) {
        this.giroRepository = giroRepository;
        this.conteoEmpresasPorGiro = conteoEmpresasPorGiro;
        this.auditoria = auditoria;
        this.registroVerticales = registroVerticales;
    }

    /**
     * Da de alta un Giro (Req 1.1). La clave se normaliza a su forma canonica
     * (minusculas, kebab) en el dominio ({@link Giro#crear}); si ya existe un
     * Giro con esa clave normalizada, se rechaza informando la clave duplicada
     * (Req 1.3).
     *
     * @param clave         clave canonica del vertical (p. ej.
     *                      {@code anuncios-luminosos}); obligatoria.
     * @param nombreVisible nombre visible del Giro; obligatorio.
     * @param descripcion   descripcion del vertical; opcional.
     * @return el Giro creado, proyectado a {@link GiroDto} y enriquecido con su
     *         completitud (siempre "Base" en el alta: un Giro recien creado no
     *         tiene aun vertical programado).
     * @throws ReglaNegocioException      si la clave o el nombre visible son
     *                                    invalidos (validado por el dominio).
     * @throws ConflictoUnicidadException si ya existe un Giro con esa clave
     *                                    (Req 1.3).
     */
    @Transactional
    public GiroDto crear(String clave, String nombreVisible, String descripcion) {
        String actor = actorActual();

        // El dominio valida y normaliza la clave a su forma canonica (Req 1.2);
        // se comprueba la unicidad sobre la clave ya normalizada (Req 1.3).
        Giro giro = Giro.crear(clave, nombreVisible, descripcion, actor);
        String claveNormalizada = giro.getClave();
        if (giroRepository.existsByClave(claveNormalizada)) {
            throw new ConflictoUnicidadException(
                    "Ya existe un Giro con la clave '" + claveNormalizada + "'.");
        }

        // Unicidad tambien por nombre visible (sin distinguir mayusculas), ademas
        // de por clave: el nombre visible ya normalizado por el dominio.
        String nombreVisibleNormalizado = giro.getNombreVisible();
        if (giroRepository.existsByNombreVisibleIgnoreCase(nombreVisibleNormalizado)) {
            throw new ConflictoUnicidadException(
                    "Ya existe un Giro con el nombre '" + nombreVisibleNormalizado + "'.");
        }

        Giro guardado = guardarTraduciendoUnicidad(giro, claveNormalizada, nombreVisibleNormalizado);

        auditar(actor, "crear",
                "creado giro '" + guardado.getNombreVisible() + "' (clave=" + guardado.getClave()
                        + ", id=" + guardado.getId() + ")");
        return enriquecer(guardado);
    }

    /**
     * Actualiza los datos EDITABLES de un Giro (bugfix edicion de Giro): nombre
     * visible (obligatorio) y descripcion (opcional). La clave canonica es
     * INMUTABLE y no se modifica. Valida la unicidad del nombre visible frente a
     * OTROS Giros (409). Persiste y audita.
     *
     * @param giroId        identificador del Giro a editar.
     * @param nombreVisible nuevo nombre visible; obligatorio.
     * @param descripcion   nueva descripcion; opcional.
     * @return el Giro actualizado, proyectado a {@link GiroDto} enriquecido.
     * @throws RecursoNoEncontradoException si el Giro no existe.
     * @throws ReglaNegocioException        si el nombre visible es invalido.
     * @throws ConflictoUnicidadException   si OTRO Giro ya usa ese nombre visible.
     */
    @Transactional
    public GiroDto actualizar(UUID giroId, String nombreVisible, String descripcion) {
        String actor = actorActual();
        Giro giro = cargar(giroId);

        // El dominio valida/normaliza el nombre visible; la unicidad se comprueba
        // sobre el valor normalizado, excluyendo el propio Giro.
        giro.actualizar(nombreVisible, descripcion, actor);
        String nombreNormalizado = giro.getNombreVisible();
        if (giroRepository.existsByNombreVisibleIgnoreCaseAndIdNot(nombreNormalizado, giro.getId())) {
            throw new ConflictoUnicidadException(
                    "Ya existe un Giro con el nombre '" + nombreNormalizado + "'.");
        }

        Giro guardado = giroRepository.save(giro);
        auditar(actor, "actualizar",
                "actualizado giro '" + guardado.getNombreVisible() + "' (clave=" + guardado.getClave()
                        + ", id=" + guardado.getId() + ")");
        return enriquecer(guardado);
    }

    /**
     * Activa un Giro (Req 1.1): queda disponible para asignarse a Empresas.
     *
     * @param giroId identificador del Giro.
     * @return el Giro activado, proyectado a {@link GiroDto} enriquecido con su
     *         completitud.
     * @throws RecursoNoEncontradoException si el Giro no existe.
     */
    @Transactional
    public GiroDto activar(UUID giroId) {
        String actor = actorActual();
        Giro giro = cargar(giroId);
        boolean anterior = giro.isActivo();
        giro.activar(actor);
        Giro guardado = giroRepository.save(giro);
        auditar(actor, "activar",
                "activado giro '" + guardado.getNombreVisible() + "' (clave=" + guardado.getClave()
                        + ", id=" + guardado.getId() + "); activo " + anterior + " -> " + guardado.isActivo());
        return enriquecer(guardado);
    }

    /**
     * Desactiva un Giro (baja logica, Req 1.1): deja de ofrecerse en el alta de
     * nuevas Empresas. Antes de desactivar comprueba que el Giro no este en uso
     * por ninguna Empresa; si lo esta, rechaza la operacion informando el numero
     * de Empresas afectadas (Req 1.5).
     *
     * @param giroId identificador del Giro.
     * @return el Giro desactivado, proyectado a {@link GiroDto} enriquecido con su
     *         completitud.
     * @throws RecursoNoEncontradoException si el Giro no existe.
     * @throws ReglaNegocioException        si el Giro esta en uso por al menos una
     *                                      Empresa (Req 1.5).
     */
    @Transactional
    public GiroDto desactivar(UUID giroId) {
        String actor = actorActual();
        Giro giro = cargar(giroId);

        long empresasEnUso = conteoEmpresasPorGiro.contarEmpresasPorGiro(giroId);
        if (empresasEnUso > 0) {
            throw new ReglaNegocioException(
                    "No se puede desactivar el Giro '" + giro.getClave() + "': aun lo usan "
                            + empresasEnUso + " Empresa(s).");
        }

        boolean anterior = giro.isActivo();
        giro.desactivar(actor);
        Giro guardado = giroRepository.save(giro);
        auditar(actor, "desactivar",
                "desactivado giro '" + guardado.getNombreVisible() + "' (clave=" + guardado.getClave()
                        + ", id=" + guardado.getId() + "); activo " + anterior + " -> " + guardado.isActivo());
        return enriquecer(guardado);
    }

    /**
     * Elimina definitivamente un Giro del catalogo de plataforma. La eliminacion
     * solo es posible para Giros "Base" y sin uso:
     * <ul>
     *   <li>si el Giro tiene <strong>reglas de negocio programadas</strong> (su
     *       clave esta registrada por un {@code ContratoVertical} en el
     *       {@link RegistroVerticales}, es decir es un Giro "Completo"), la
     *       eliminacion se rechaza con {@link ReglaNegocioException} (422): un
     *       Giro programado en codigo es definitivo;</li>
     *   <li>si al menos una Empresa lo usa
     *       ({@link ConteoEmpresasPorGiroPort} &gt; 0), se rechaza con 422
     *       informando el numero de Empresas afectadas.</li>
     * </ul>
     * En otro caso, borra el Giro y audita la operacion (evento de plataforma).
     *
     * <p>Ambas comprobaciones se mantienen explicitas por robustez, aunque en la
     * practica un Giro registrado en el {@link RegistroVerticales} esta programado
     * en codigo y la eliminacion solo aplica a Giros creados unicamente en BD (sin
     * vertical), que son el caso "no Completo".</p>
     *
     * @param giroId identificador del Giro a eliminar.
     * @throws RecursoNoEncontradoException si el Giro no existe (404).
     * @throws ReglaNegocioException        si el Giro tiene reglas de negocio
     *                                      programadas o lo usa alguna Empresa (422).
     */
    @Transactional
    public void eliminar(UUID giroId) {
        String actor = actorActual();
        Giro giro = cargar(giroId);

        // Regla A: un Giro con vertical programado (Completo) es definitivo.
        boolean tieneReglasNegocio = registroVerticales.girosRegistrados().contains(giro.getClave());
        if (tieneReglasNegocio) {
            throw new ReglaNegocioException(
                    "No se puede eliminar el Giro '" + giro.getNombreVisible()
                            + "': ya tiene reglas de negocio programadas y es definitivo.");
        }

        // Regla B: no se elimina un Giro en uso por alguna Empresa.
        long empresasEnUso = conteoEmpresasPorGiro.contarEmpresasPorGiro(giroId);
        if (empresasEnUso > 0) {
            throw new ReglaNegocioException(
                    "No se puede eliminar el Giro '" + giro.getNombreVisible()
                            + "': aun lo usan " + empresasEnUso + " Empresa(s).");
        }

        giroRepository.delete(giro);
        auditar(actor, "eliminar",
                "eliminado giro '" + giro.getNombreVisible() + "' (clave=" + giro.getClave()
                        + ", id=" + giro.getId() + ")");
    }

    /**
     * Listado paginado de Giros, opcionalmente filtrado por estado {@code activo}
     * (Req 1.6).
     *
     * @param activo   estado por el que filtrar; {@code null} lista todos los
     *                 Giros (activos e inactivos).
     * @param pageable parametros de paginacion ya acotados (20/100) por el
     *                 controlador via {@code PageRequestFactory}.
     * @return la pagina de Giros proyectada a {@link GiroDto}, cada uno enriquecido
     *         con su completitud (Base/Completo); los metadatos de paginacion se
     *         preservan.
     */
    @Transactional(readOnly = true)
    public Page<GiroDto> listar(Boolean activo, Pageable pageable) {
        Page<Giro> pagina = (activo == null)
                ? giroRepository.findAllBy(pageable)
                : giroRepository.findByActivo(activo, pageable);
        return pagina.map(this::enriquecer);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Proyecta un {@link Giro} a {@link GiroDto} enriquecido con su grado de
     * completitud, consultando el {@link RegistroVerticales}: {@code tieneReglasNegocio}
     * es {@code true} si la clave del Giro tiene un vertical programado; el conteo
     * {@code modulosEspecificos} son los modulos que aporta ese vertical (0 si no
     * hay vertical). Es una consulta barata (mapas inmutables ya indexados en el
     * arranque), coherente con {@code CatalogoModulosService}.
     *
     * @param giro entidad a proyectar.
     * @return el DTO enriquecido con la completitud del Giro.
     */
    private GiroDto enriquecer(Giro giro) {
        String clave = giro.getClave();
        boolean tieneReglasNegocio = registroVerticales.girosRegistrados().contains(clave);
        int modulosEspecificos = registroVerticales.porGiro(clave)
                .map(ContratoVertical::modulos)
                .map(Set::size)
                .orElse(0);
        return GiroDto.de(giro, tieneReglasNegocio, modulosEspecificos);
    }

    private Giro cargar(UUID giroId) {
        return giroRepository.findById(giroId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el Giro solicitado."));
    }

    /**
     * Persiste el Giro traduciendo una posible violacion de los indices unicos
     * {@code uq_giro_clave} (V50) o {@code uq_giro_nombre_visible} (V56) a
     * {@link ConflictoUnicidadException} (Req 1.3), por si dos peticiones
     * concurrentes superan las comprobaciones previas {@code existsByClave} /
     * {@code existsByNombreVisibleIgnoreCase}. Como la excepcion de integridad no
     * indica que indice se violo, se reconsulta el nombre visible para elegir un
     * mensaje preciso; si el nombre visible ya existe, el conflicto es por nombre,
     * en caso contrario se atribuye a la clave. Replica el patron de traduccion
     * de {@code ServicioEmpresas} y {@code ServicioRoles}.
     */
    private Giro guardarTraduciendoUnicidad(Giro giro, String clave, String nombreVisible) {
        try {
            return giroRepository.saveAndFlush(giro);
        } catch (DataIntegrityViolationException ex) {
            if (giroRepository.existsByNombreVisibleIgnoreCase(nombreVisible)) {
                throw new ConflictoUnicidadException(
                        "Ya existe un Giro con el nombre '" + nombreVisible + "'.");
            }
            throw new ConflictoUnicidadException(
                    "Ya existe un Giro con la clave '" + clave + "'.");
        }
    }

    private void auditar(String actor, String accion, String detalle) {
        auditoria.registrar(
                EventoAuditoria.dePlataforma(actor, accion, RECURSO_GIRO, detalle, null, null));
    }

    /**
     * Resuelve el identificador del actor autenticado ({@code super_admin}) para
     * la auditoria; si no hay contexto de seguridad, usa "sistema", igual que
     * {@code ServicioEmpresas} y {@code ServicioRoles}.
     */
    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
