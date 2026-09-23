package com.dessti.crm.social.application;

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
import com.dessti.crm.social.adapter.out.persistence.CampanaPublicitariaRepository;
import com.dessti.crm.social.domain.CampanaPublicitaria;
import com.dessti.crm.social.domain.CanalSocial;

/**
 * Servicio de aplicacion de la Campaña_Publicitaria (Req 65.7-65.11). Orquesta la
 * creacion con validacion de presupuesto y periodo (Property 39), la consulta del
 * estado externo de SOLO LECTURA via el {@link PublicacionSocialPort} y el listado
 * con filtro por Canal_Social. Replica el patron de {@link ServicioBandeja}.
 *
 * <h2>Estado externo de SOLO LECTURA (Req 65.9)</h2>
 * <p>El estado operativo de la campaña reside en la Marketing API de Meta;
 * {@link #consultarEstadoExterno(UUID)} lo obtiene via el adaptador y lo presenta
 * sin persistirlo como autoritativo (a lo sumo, guarda una instantanea de
 * diagnostico). El CRM nunca modifica ese estado.</p>
 *
 * <h2>Multi-tenant y auditoria (Req 23, 65.11)</h2>
 * <p>La creacion audita y un acceso a una Campaña_Publicitaria inexistente en el
 * tenant registra {@code acceso_denegado} y devuelve 404 (Req 23.3).</p>
 */
@Service
public class ServicioCampanas {

    /** Tipo de recurso de auditoria/RBAC de la Campaña_Publicitaria. */
    static final String RECURSO_CAMPANA = "campana_publicitaria";

    private final CampanaPublicitariaRepository campanaRepository;
    private final PublicacionSocialPort publicacionSocial;
    private final AuditoriaPort auditoria;

    public ServicioCampanas(CampanaPublicitariaRepository campanaRepository,
                            PublicacionSocialPort publicacionSocial,
                            AuditoriaPort auditoria) {
        this.campanaRepository = campanaRepository;
        this.publicacionSocial = publicacionSocial;
        this.auditoria = auditoria;
    }

    /**
     * Crea una Campaña_Publicitaria validando su presupuesto (rango
     * [0.01, 999,999,999.99]) y su periodo ({@code fecha_fin >= fecha_inicio}) via
     * {@link com.dessti.crm.social.domain.ValidacionCampana} (Req 65.7, 65.8;
     * Property 39). Un dato invalido se rechaza (422) y no se persiste.
     *
     * @param comando datos de la campaña; obligatorio.
     * @return el DTO de la Campaña_Publicitaria creada.
     * @throws ReglaNegocioException si el nombre falta o el presupuesto/periodo son
     *         invalidos (422, Req 65.8).
     */
    @Transactional
    public CampanaPublicitariaDto crearCampana(CrearCampanaPublicitariaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException(
                    "El comando de creacion de Campaña_Publicitaria es obligatorio.");
        }
        CampanaPublicitaria campana = CampanaPublicitaria.crear(
                comando.cuentaCanalSocialId(), comando.canal(), comando.nombre(),
                comando.presupuesto(), comando.fechaInicio(), comando.fechaFin(),
                comando.externoId(), actor);
        CampanaPublicitaria guardada = campanaRepository.save(campana);

        auditar(actor, "crear", guardada.getId(),
                "Campaña_Publicitaria '" + guardada.getNombre() + "' presupuesto "
                        + guardada.getPresupuesto().toPlainString() + " periodo "
                        + guardada.getFechaInicio() + ".." + guardada.getFechaFin());
        return CampanaPublicitariaDto.de(guardada);
    }

    /**
     * Consulta el estado externo de una Campaña_Publicitaria en la Marketing API de
     * Meta via el adaptador, como instantanea de SOLO LECTURA (Req 65.9). Si la
     * campaña tiene id externo, guarda ademas una instantanea de diagnostico (no
     * autoritativa).
     *
     * @param campanaId identificador de la campaña; obligatorio.
     * @return el DTO de estado externo de SOLO LECTURA.
     * @throws RecursoNoEncontradoException si la campaña no es accesible (404).
     */
    @Transactional
    public EstadoCampanaExternoDto consultarEstadoExterno(UUID campanaId) {
        String actor = actorActual();
        CampanaPublicitaria campana = cargarCampana(campanaId, actor);

        EstadoCampanaExterno estado = publicacionSocial.consultarEstadoCampana(
                campana.getExternoId(), campana.getCanal());
        if (estado.disponible()) {
            campana.registrarInstantaneaEstadoExterno(estado.estado(), actor);
            campanaRepository.save(campana);
        }
        auditar(actor, "consultar_estado_externo", campana.getId(),
                "estado externo (solo lectura) disponible=" + estado.disponible()
                        + " estado='" + estado.estado() + "'");
        return EstadoCampanaExternoDto.de(campana.getId(), estado);
    }

    /**
     * Consulta puntual de una Campaña_Publicitaria del tenant (Req 23.3).
     *
     * @param campanaId identificador de la campaña.
     * @return el DTO de la campaña.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public CampanaPublicitariaDto consultar(UUID campanaId) {
        String actor = actorActual();
        return CampanaPublicitariaDto.de(cargarCampana(campanaId, actor));
    }

    /**
     * Listado paginado de Campaña_Publicitaria del tenant con filtro opcional por
     * Canal_Social (Req 65.10).
     *
     * @param canal    Canal_Social a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de campañas como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<CampanaPublicitariaDto> listarCampanas(CanalSocial canal, Pageable pageable) {
        return campanaRepository.buscarConFiltros(canal, pageable)
                .map(CampanaPublicitariaDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private CampanaPublicitaria cargarCampana(UUID campanaId, String actor) {
        if (campanaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Campaña_Publicitaria solicitada.");
        }
        return campanaRepository.findById(campanaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, campanaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Campaña_Publicitaria solicitada.");
                });
    }

    private void auditar(String actor, String accion, UUID id, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_CAMPANA,
                detalle + " [id=" + id + "]", null, null));
    }

    private void auditarAccesoCruzado(String actor, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", RECURSO_CAMPANA,
                "intento de acceso a Campaña_Publicitaria no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
