package com.dessti.crm.social.application;

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
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.social.adapter.out.persistence.PlantillaMensajeRepository;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.PlantillaMensaje;

/**
 * Servicio de aplicacion que gobierna las {@link PlantillaMensaje} (Req 64.7).
 * Replica el patron de {@code ServicioCuentasCanalSocial}. Las plantillas aprobadas
 * habilitan el envio FUERA de la Ventana_Servicio (Req 64.7).
 */
@Service
public class ServicioPlantillas {

    /** Tipo de recurso de auditoria/RBAC de la Plantilla_Mensaje. */
    static final String RECURSO_PLANTILLA = "plantilla_mensaje";

    private final PlantillaMensajeRepository plantillaRepository;
    private final AuditoriaPort auditoria;

    public ServicioPlantillas(PlantillaMensajeRepository plantillaRepository,
                              AuditoriaPort auditoria) {
        this.plantillaRepository = plantillaRepository;
        this.auditoria = auditoria;
    }

    /**
     * Crea una Plantilla_Mensaje (Req 64.7). 409 si ya existe por canal y nombre.
     *
     * @param canal     Canal_Social; obligatorio.
     * @param nombre    nombre de la plantilla; obligatorio.
     * @param contenido contenido; obligatorio.
     * @param aprobada  {@code true} si ya esta aprobada por el proveedor.
     * @return el DTO de la plantilla creada.
     * @throws ReglaNegocioException      si algun dato obligatorio falta/es invalido (422).
     * @throws ConflictoUnicidadException si ya existe la plantilla por canal/nombre (409).
     */
    @Transactional
    public PlantillaMensajeDto crear(CanalSocial canal, String nombre, String contenido,
                                     boolean aprobada) {
        String actor = actorActual();
        if (canal == null) {
            throw new ReglaNegocioException("La Plantilla_Mensaje debe indicar el Canal_Social.");
        }
        PlantillaMensaje plantilla = PlantillaMensaje.crear(canal, nombre, contenido, aprobada, actor);
        PlantillaMensaje guardada;
        try {
            guardada = plantillaRepository.saveAndFlush(plantilla);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoUnicidadException(
                    "ya existe una Plantilla_Mensaje para ese canal y nombre.");
        }
        auditar(actor, "crear", guardada.getId(),
                "creada Plantilla_Mensaje '" + guardada.getNombre() + "' canal '"
                        + guardada.getCanal().valorBd() + "' aprobada=" + guardada.isAprobada());
        return PlantillaMensajeDto.de(guardada);
    }

    /**
     * Consulta puntual de una Plantilla_Mensaje del tenant (Req 23.3).
     *
     * @param plantillaId identificador de la plantilla.
     * @return el DTO de la plantilla.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public PlantillaMensajeDto consultar(UUID plantillaId) {
        String actor = actorActual();
        return PlantillaMensajeDto.de(cargar(plantillaId, actor));
    }

    /**
     * Listado paginado de plantillas del tenant con filtro opcional por canal
     * (Req 64.7).
     *
     * @param canal    Canal_Social a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de plantillas como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<PlantillaMensajeDto> listar(CanalSocial canal, Pageable pageable) {
        return plantillaRepository.buscarConFiltros(canal, pageable).map(PlantillaMensajeDto::de);
    }

    private PlantillaMensaje cargar(UUID plantillaId, String actor) {
        if (plantillaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Plantilla_Mensaje solicitada.");
        }
        return plantillaRepository.findById(plantillaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_PLANTILLA, plantillaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Plantilla_Mensaje solicitada.");
                });
    }

    private void auditar(String actor, String accion, UUID id, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_PLANTILLA,
                detalle + " [id=" + id + "]", null, null));
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
