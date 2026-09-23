package com.dessti.crm.social.application;

import java.time.Clock;
import java.time.Instant;
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
import com.dessti.crm.social.adapter.out.meta.MetaProperties;
import com.dessti.crm.social.adapter.out.persistence.CuentaCanalSocialRepository;
import com.dessti.crm.social.adapter.out.persistence.IntentoPublicacionRepository;
import com.dessti.crm.social.adapter.out.persistence.PublicacionSocialRepository;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.CuentaCanalSocial;
import com.dessti.crm.social.domain.EstadoPublicacion;
import com.dessti.crm.social.domain.IntentoPublicacion;
import com.dessti.crm.social.domain.PublicacionSocial;

/**
 * Servicio de aplicacion de la Publicacion_Social (Req 65.1-65.6, 65.10, 65.11).
 * Orquesta la creacion con validacion de fecha, la programacion, la publicacion via
 * el {@link PublicacionSocialPort} con politica de reintentos y registro por
 * intento, las transiciones de estado manuales y el listado con filtros. Replica el
 * patron de {@link ServicioBandeja}.
 *
 * <h2>Publicacion con reintentos (Req 65.5, 65.6)</h2>
 * <p>{@link #publicar(UUID)} despacha por el puerto hasta
 * {@link MetaProperties#maxIntentosEfectivo()} veces; cada intento —exitoso o
 * fallido— se persiste como {@link IntentoPublicacion}. Al primer exito la
 * Publicacion_Social transita {@code programada -> publicada}; si se agotan los
 * intentos, transita {@code programada -> fallida} con el ultimo motivo.</p>
 *
 * <h2>Multi-tenant y auditoria (Req 23, 65.11)</h2>
 * <p>Todas las operaciones auditan (crear/programar/publicar/cambiar_estado) y un
 * acceso a una Publicacion_Social inexistente en el tenant registra
 * {@code acceso_denegado} y devuelve 404 (Req 23.3).</p>
 */
@Service
public class ServicioPublicaciones {

    /** Tipo de recurso de auditoria/RBAC de la Publicacion_Social. */
    static final String RECURSO_PUBLICACION = "publicacion_social";

    private final PublicacionSocialRepository publicacionRepository;
    private final IntentoPublicacionRepository intentoRepository;
    private final CuentaCanalSocialRepository cuentaRepository;
    private final PublicacionSocialPort publicacionSocial;
    private final AuditoriaPort auditoria;
    private final Clock clock;
    private final MetaProperties propiedades;

    public ServicioPublicaciones(PublicacionSocialRepository publicacionRepository,
                                 IntentoPublicacionRepository intentoRepository,
                                 CuentaCanalSocialRepository cuentaRepository,
                                 PublicacionSocialPort publicacionSocial,
                                 AuditoriaPort auditoria,
                                 Clock clock,
                                 MetaProperties propiedades) {
        this.publicacionRepository = publicacionRepository;
        this.intentoRepository = intentoRepository;
        this.cuentaRepository = cuentaRepository;
        this.publicacionSocial = publicacionSocial;
        this.auditoria = auditoria;
        this.clock = clock;
        this.propiedades = propiedades;
    }

    /**
     * Crea una Publicacion_Social en estado {@code borrador} validando el contenido
     * y que la fecha programada no sea anterior al momento actual (Req 65.1, 65.2).
     * El Canal_Social se toma de la Cuenta_Canal_Social indicada.
     *
     * @param comando datos de la publicacion; obligatorio.
     * @return el DTO de la Publicacion_Social creada.
     * @throws RecursoNoEncontradoException si la Cuenta_Canal_Social no es accesible (404).
     * @throws ReglaNegocioException si el comando es invalido (422, Req 65.2).
     */
    @Transactional
    public PublicacionSocialDto crearPublicacion(CrearPublicacionSocialCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("El comando de creacion de Publicacion_Social es obligatorio.");
        }
        CuentaCanalSocial cuenta = cargarCuenta(comando.cuentaCanalSocialId());

        Instant ahora = clock.instant();
        PublicacionSocial publicacion = PublicacionSocial.crear(
                cuenta.getId(), cuenta.getCanal(), comando.contenido(),
                comando.fechaProgramada(), ahora, actor);
        PublicacionSocial guardada = publicacionRepository.save(publicacion);

        auditar(actor, "crear", guardada.getId(),
                "Publicacion_Social en canal '" + guardada.getCanal().valorBd()
                        + "' programada para " + guardada.getFechaProgramada());
        return PublicacionSocialDto.de(guardada);
    }

    /**
     * Programa una Publicacion_Social: transicion {@code borrador -> programada}
     * (Req 65.3).
     *
     * @param publicacionId identificador de la publicacion; obligatorio.
     * @return el DTO de la publicacion {@code programada}.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public PublicacionSocialDto programar(UUID publicacionId) {
        String actor = actorActual();
        PublicacionSocial publicacion = cargarPublicacion(publicacionId, actor);
        publicacion.programar(actor);
        PublicacionSocial guardada = publicacionRepository.save(publicacion);
        auditar(actor, "programar", guardada.getId(), "Publicacion_Social programada");
        return PublicacionSocialDto.de(guardada);
    }

    /**
     * Publica una Publicacion_Social {@code programada} via el
     * {@link PublicacionSocialPort} con politica de reintentos, registrando cada
     * intento (Req 65.5, 65.6). Al primer exito transita a {@code publicada}; si se
     * agotan los intentos, transita a {@code fallida}.
     *
     * @param publicacionId identificador de la publicacion; obligatorio.
     * @return el DTO de la publicacion en su estado final ({@code publicada} o
     *         {@code fallida}).
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si la publicacion no esta {@code programada} (422).
     */
    @Transactional
    public PublicacionSocialDto publicar(UUID publicacionId) {
        String actor = actorActual();
        PublicacionSocial publicacion = cargarPublicacion(publicacionId, actor);
        if (publicacion.getEstado() != EstadoPublicacion.PROGRAMADA) {
            throw new ReglaNegocioException(
                    "Solo se puede publicar una Publicacion_Social en estado 'programada'; estado actual: '"
                            + publicacion.getEstado().valorBd() + "'.");
        }

        SolicitudPublicacion solicitud = new SolicitudPublicacion(
                publicacion.getCanal(), credencialesRefDe(publicacion), publicacion.getContenido());

        int maxIntentos = propiedades.maxIntentosEfectivo();
        ResultadoPublicacion resultado = null;
        for (int numeroIntento = 1; numeroIntento <= maxIntentos; numeroIntento++) {
            resultado = publicacionSocial.publicar(solicitud);
            Instant intentadoEn = clock.instant();
            intentoRepository.save(IntentoPublicacion.registrar(
                    publicacion.getId(), numeroIntento, resultado.exito(),
                    resultado.mensajeError(), intentadoEn, actor));

            if (resultado.exito()) {
                publicacion.marcarPublicada(resultado.externoId(), intentadoEn, actor);
                PublicacionSocial guardada = publicacionRepository.save(publicacion);
                auditar(actor, "publicar", guardada.getId(),
                        "publicada en intento " + numeroIntento + "/" + maxIntentos
                                + " externo_id='" + resultado.externoId() + "'");
                return PublicacionSocialDto.de(guardada);
            }
            auditar(actor, "reintento_publicacion", publicacion.getId(),
                    "intento " + numeroIntento + "/" + maxIntentos + " fallido: " + resultado.mensajeError());
        }

        // Agotados los reintentos: la publicacion se marca fallida (Req 65.6).
        String motivo = (resultado == null) ? "Sin resultado del proveedor." : resultado.mensajeError();
        publicacion.marcarFallida(motivo, actor);
        PublicacionSocial guardada = publicacionRepository.save(publicacion);
        auditar(actor, "fallar", guardada.getId(),
                "fallida tras " + maxIntentos + " intentos: " + motivo);
        return PublicacionSocialDto.de(guardada);
    }

    /**
     * Aplica una transicion de estado manual sobre la Publicacion_Social (Req 65.3,
     * 65.4). Admite {@code programada} (desde {@code borrador}); las transiciones a
     * {@code publicada}/{@code fallida} se realizan por {@link #publicar(UUID)}.
     *
     * @param publicacionId identificador de la publicacion; obligatorio.
     * @param destino       estado destino; obligatorio.
     * @return el DTO de la publicacion tras la transicion.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si el destino es nulo (422).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la
     *         transicion no esta permitida (409, Req 65.4).
     */
    @Transactional
    public PublicacionSocialDto cambiarEstado(UUID publicacionId, EstadoPublicacion destino) {
        String actor = actorActual();
        if (destino == null) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        PublicacionSocial publicacion = cargarPublicacion(publicacionId, actor);
        EstadoPublicacion anterior = publicacion.getEstado();

        switch (destino) {
            case PROGRAMADA -> publicacion.programar(actor);
            case PUBLICADA, FALLIDA -> throw new ReglaNegocioException(
                    "La transicion a '" + destino.valorBd() + "' se realiza al publicar la "
                            + "Publicacion_Social, no por cambio de estado manual.");
            case BORRADOR -> throw new ReglaNegocioException(
                    "No se admite volver al estado 'borrador'.");
        }

        PublicacionSocial guardada = publicacionRepository.save(publicacion);
        auditar(actor, "cambiar_estado", guardada.getId(),
                "de '" + anterior.valorBd() + "' a '" + guardada.getEstado().valorBd() + "'");
        return PublicacionSocialDto.de(guardada);
    }

    /**
     * Consulta puntual de una Publicacion_Social del tenant (Req 23.3).
     *
     * @param publicacionId identificador de la publicacion.
     * @return el DTO de la publicacion.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public PublicacionSocialDto consultar(UUID publicacionId) {
        String actor = actorActual();
        return PublicacionSocialDto.de(cargarPublicacion(publicacionId, actor));
    }

    /**
     * Listado paginado de Publicacion_Social del tenant con filtros opcionales por
     * Canal_Social y estado (Req 65.10).
     *
     * @param canal    Canal_Social a filtrar; {@code null} no filtra.
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de publicaciones como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<PublicacionSocialDto> listarPublicaciones(CanalSocial canal, EstadoPublicacion estado,
                                                          Pageable pageable) {
        return publicacionRepository.buscarConFiltros(canal, estado, pageable)
                .map(PublicacionSocialDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private String credencialesRefDe(PublicacionSocial publicacion) {
        return cuentaRepository.findById(publicacion.getCuentaCanalSocialId())
                .map(CuentaCanalSocial::getCredencialesRef)
                .orElse(null);
    }

    private CuentaCanalSocial cargarCuenta(UUID cuentaCanalSocialId) {
        if (cuentaCanalSocialId == null) {
            throw new ReglaNegocioException("La Publicacion_Social debe indicar la Cuenta_Canal_Social.");
        }
        return cuentaRepository.findById(cuentaCanalSocialId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Cuenta_Canal_Social indicada."));
    }

    private PublicacionSocial cargarPublicacion(UUID publicacionId, String actor) {
        if (publicacionId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Publicacion_Social solicitada.");
        }
        return publicacionRepository.findById(publicacionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, publicacionId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Publicacion_Social solicitada.");
                });
    }

    private void auditar(String actor, String accion, UUID id, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_PUBLICACION,
                detalle + " [id=" + id + "]", null, null));
    }

    private void auditarAccesoCruzado(String actor, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", RECURSO_PUBLICACION,
                "intento de acceso a Publicacion_Social no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
