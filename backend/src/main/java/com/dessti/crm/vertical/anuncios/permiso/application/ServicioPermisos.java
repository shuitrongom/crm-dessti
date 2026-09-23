package com.dessti.crm.vertical.anuncios.permiso.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.vertical.anuncios.permiso.adapter.out.persistence.PermisoInstalacionRepository;
import com.dessti.crm.vertical.anuncios.permiso.domain.EstadoPermisoInstalacion;
import com.dessti.crm.vertical.anuncios.permiso.domain.PermisoInstalacion;
import com.dessti.crm.vertical.anuncios.permiso.domain.TipoPermisoInstalacion;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de los
 * {@link PermisoInstalacion} (Req 17). Replica el patron establecido por
 * {@code ServicioLevantamientos} y {@code ServicioOrdenesFabricacion}.
 *
 * <h2>Operaciones (Req 17)</h2>
 * <ul>
 *   <li><strong>crear (Req 17.1, 17.7):</strong> valida los datos obligatorios
 *       (tipo y fecha de vencimiento) e interpreta la etiqueta de tipo, crea el
 *       permiso en {@code solicitado}, persiste y audita.</li>
 *   <li><strong>aprobar/rechazar (Req 17.2, 17.3, 17.7):</strong> aplican
 *       {@code solicitado -> {aprobado|rechazado}} fijando actor y marca UTC (Clock
 *       inyectado); 409 si el permiso ya esta en un estado final; auditan el estado
 *       anterior y el nuevo.</li>
 *   <li><strong>consultar (Req 23.3):</strong> 404 + auditoria del intento si no es
 *       accesible.</li>
 *   <li><strong>listar (Req 17.6):</strong> listado paginado (20/100) con filtros
 *       por Sitio, tipo y estado; sin coincidencias devuelve una pagina vacia.</li>
 *   <li><strong>notificarVencimientosProximos (Req 17.5):</strong> localiza los
 *       permisos {@code aprobado} que venceran en los proximos 30 dias (segun el
 *       Clock inyectado) y emite una Notificacion por cada uno via el
 *       {@link NotificadorPermisoPort}. Es un metodo invocable (no cablea un
 *       planificador): el bloque 49/43 podra programarlo periodicamente.</li>
 * </ul>
 *
 * <h2>Guarda de programacion de instalacion (Req 17.4)</h2>
 * <p>La guarda que exige un Permiso_Instalacion {@code aprobado} para programar la
 * instalacion de un Sitio la expone el {@link PermisoAprobadoPort} (implementado en
 * este submodulo) que consumira el bloque 22 (tarea 22.1).</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 17.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la peticion,
 * Req 23.4). Cada operacion relevante se registra via {@link AuditoriaPort} como
 * evento de tenant con el actor derivado del contexto; el cambio de estado incluye
 * el estado anterior y el nuevo (Req 17.7). Los instantes UTC de la decision y la
 * ventana de vencimiento usan el {@link Clock} inyectado para ser deterministas en
 * pruebas.</p>
 */
@Service
public class ServicioPermisos {

    /** Tipo de recurso de auditoria/RBAC del Permiso_Instalacion. */
    static final String RECURSO_PERMISO = "permiso_instalacion";

    /** Ventana de anticipacion (dias) para notificar el vencimiento proximo (Req 17.5). */
    static final int DIAS_AVISO_VENCIMIENTO = 30;

    private final PermisoInstalacionRepository permisoRepository;
    private final NotificadorPermisoPort notificador;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioPermisos(PermisoInstalacionRepository permisoRepository,
                            NotificadorPermisoPort notificador,
                            AuditoriaPort auditoria,
                            Clock clock) {
        this.permisoRepository = permisoRepository;
        this.notificador = notificador;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Crea un Permiso_Instalacion con los datos obligatorios y estado inicial
     * {@code solicitado} (Req 17.1). Audita el alta (Req 17.7).
     *
     * @param comando datos del permiso a crear; obligatorio.
     * @return el DTO del permiso creado (estado {@code solicitado}).
     * @throws ReglaNegocioException si el comando es nulo o falta/es invalido un
     *         dato obligatorio (422, Req 17.1).
     */
    @Transactional
    public PermisoInstalacionDto crear(CrearPermisoInstalacionCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del Permiso_Instalacion son obligatorios.");
        }
        TipoPermisoInstalacion tipo = interpretarTipo(comando.tipo());

        PermisoInstalacion permiso = PermisoInstalacion.crear(
                tipo, comando.fechaVencimiento(), comando.sitioId(), actor);
        PermisoInstalacion guardado = permisoRepository.save(permiso);

        auditar(actor, "crear", guardado.getId(),
                "creado Permiso_Instalacion tipo '" + guardado.getTipo().valorBd()
                        + "' en estado '" + guardado.getEstado().valorBd()
                        + "' [sitio=" + guardado.getSitioId()
                        + ", vence=" + guardado.getFechaVencimiento() + "]",
                null, guardado.getEstado().valorBd());
        return PermisoInstalacionDto.de(guardado);
    }

    /**
     * Aprueba un Permiso_Instalacion, aplicando {@code solicitado -> aprobado},
     * fijando el actor y el instante UTC de la decision (Req 17.2) y auditando el
     * estado anterior y el nuevo (Req 17.7).
     *
     * @param permisoId identificador del Permiso_Instalacion.
     * @return el DTO del permiso aprobado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si el
     *         permiso no esta {@code solicitado} (409, Req 17.3).
     */
    @Transactional
    public PermisoInstalacionDto aprobar(UUID permisoId) {
        return cambiarEstado(permisoId, EstadoPermisoInstalacion.APROBADO);
    }

    /**
     * Rechaza un Permiso_Instalacion, aplicando {@code solicitado -> rechazado},
     * fijando el actor y el instante UTC de la decision (Req 17.2) y auditando el
     * estado anterior y el nuevo (Req 17.7).
     *
     * @param permisoId identificador del Permiso_Instalacion.
     * @return el DTO del permiso rechazado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si el
     *         permiso no esta {@code solicitado} (409, Req 17.3).
     */
    @Transactional
    public PermisoInstalacionDto rechazar(UUID permisoId) {
        return cambiarEstado(permisoId, EstadoPermisoInstalacion.RECHAZADO);
    }

    private PermisoInstalacionDto cambiarEstado(UUID permisoId, EstadoPermisoInstalacion destino) {
        String actor = actorActual();
        PermisoInstalacion permiso = cargar(permisoId, actor);
        EstadoPermisoInstalacion anterior = permiso.getEstado();
        if (destino == EstadoPermisoInstalacion.APROBADO) {
            permiso.aprobar(actor, clock);
        } else {
            permiso.rechazar(actor, clock);
        }
        PermisoInstalacion guardado = permisoRepository.save(permiso);
        auditar(actor, "cambiar_estado", guardado.getId(),
                "Permiso_Instalacion cambia de '" + anterior.valorBd()
                        + "' a '" + guardado.getEstado().valorBd() + "' por " + actor,
                anterior.valorBd(), guardado.getEstado().valorBd());
        return PermisoInstalacionDto.de(guardado);
    }

    /**
     * Consulta puntual de un Permiso_Instalacion del tenant (Req 23.3).
     *
     * @param permisoId identificador del Permiso_Instalacion.
     * @return el DTO del permiso.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public PermisoInstalacionDto consultar(UUID permisoId) {
        String actor = actorActual();
        return PermisoInstalacionDto.de(cargar(permisoId, actor));
    }

    /**
     * Listado paginado de permisos del tenant con filtros opcionales por Sitio,
     * tipo y estado (Req 17.6). Un filtro nulo/blanco no restringe; sin
     * coincidencias se devuelve una pagina vacia con total 0.
     *
     * @param sitioId  Sitio a filtrar; {@code null} no filtra.
     * @param tipo     etiqueta de tipo a filtrar; {@code null}/blanco no filtra.
     * @param estado   etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de permisos como DTOs.
     * @throws ReglaNegocioException si la etiqueta de tipo o estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<PermisoInstalacionDto> listar(UUID sitioId, String tipo, String estado,
                                              Pageable pageable) {
        TipoPermisoInstalacion filtroTipo =
                (tipo == null || tipo.isBlank()) ? null : interpretarTipo(tipo);
        EstadoPermisoInstalacion filtroEstado =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return permisoRepository.buscarConFiltros(sitioId, filtroTipo, filtroEstado, pageable)
                .map(PermisoInstalacionDto::de);
    }

    /**
     * Localiza los permisos {@code aprobado} del tenant vigente que venceran en los
     * proximos {@value #DIAS_AVISO_VENCIMIENTO} dias (segun el {@link Clock}
     * inyectado) y emite una Notificacion de vencimiento proximo por cada uno via el
     * {@link NotificadorPermisoPort} (Req 17.5). Es un metodo invocable (endpoint o
     * llamada de un planificador); no cablea el planificador aqui: el bloque 49/43
     * podra programarlo periodicamente.
     *
     * @return el numero de permisos por vencer notificados.
     */
    @Transactional(readOnly = true)
    public int notificarVencimientosProximos() {
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate limite = hoy.plusDays(DIAS_AVISO_VENCIMIENTO);
        List<PermisoInstalacion> porVencer =
                permisoRepository.buscarAprobadosVenciendoEntre(hoy, limite);

        UUID tenant = TenantContext.require();
        for (PermisoInstalacion permiso : porVencer) {
            long diasParaVencer = hoy.until(permiso.getFechaVencimiento()).getDays();
            notificador.notificarVencimientoProximo(new NotificacionVencimientoPermiso(
                    tenant,
                    permiso.getId(),
                    permiso.getSitioId(),
                    permiso.getTipo().valorBd(),
                    permiso.getFechaVencimiento(),
                    diasParaVencer));
        }
        return porVencer.size();
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private PermisoInstalacion cargar(UUID permisoId, String actor) {
        if (permisoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Permiso_Instalacion solicitado.");
        }
        return permisoRepository.findById(permisoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_PERMISO, permisoId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el Permiso_Instalacion solicitado.");
                });
    }

    private TipoPermisoInstalacion interpretarTipo(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException(
                    "El tipo del Permiso_Instalacion es obligatorio (municipal o arrendador).");
        }
        try {
            return TipoPermisoInstalacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Tipo de Permiso_Instalacion desconocido: " + etiqueta);
        }
    }

    private EstadoPermisoInstalacion interpretarEstado(String etiqueta) {
        try {
            return EstadoPermisoInstalacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Permiso_Instalacion desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, UUID permisoId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_PERMISO,
                detalle + " [id=" + permisoId + "]", valorAnterior, valorNuevo));
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
