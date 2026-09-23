package com.dessti.crm.calidad.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.calidad.adapter.out.persistence.OportunidadCalidadRepository;
import com.dessti.crm.calidad.adapter.out.persistence.RiesgoRepository;
import com.dessti.crm.calidad.domain.EstadoOportunidadCalidad;
import com.dessti.crm.calidad.domain.EstadoRiesgo;
import com.dessti.crm.calidad.domain.NivelRiesgo;
import com.dessti.crm.calidad.domain.OportunidadCalidad;
import com.dessti.crm.calidad.domain.Riesgo;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion de los registros separados de {@link Riesgo} y
 * {@link OportunidadCalidad} (Req 70.3, clausulas 6.1.2 y 6.1.3). Son agregados
 * distintos con acciones propias; el nivel del Riesgo lo deriva el dominio (solo
 * lectura). Cada operacion se audita (Req 70.9).
 */
@Service
public class ServicioRiesgosOportunidades {

    /** Tipo de recurso de auditoria/RBAC del Riesgo. */
    static final String RECURSO_RIESGO = "riesgo";

    /** Tipo de recurso de auditoria/RBAC de la Oportunidad_Calidad. */
    static final String RECURSO_OPORTUNIDAD = "oportunidad_calidad";

    private final RiesgoRepository riesgoRepository;
    private final OportunidadCalidadRepository oportunidadRepository;
    private final AuditoriaPort auditoria;

    public ServicioRiesgosOportunidades(RiesgoRepository riesgoRepository,
                                        OportunidadCalidadRepository oportunidadRepository,
                                        AuditoriaPort auditoria) {
        this.riesgoRepository = riesgoRepository;
        this.oportunidadRepository = oportunidadRepository;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------
    // Riesgo (clausula 6.1.2)
    // ------------------------------------------------------------------

    /**
     * Identifica un Riesgo nuevo con su nivel derivado (Req 70.3).
     *
     * @param comando datos del riesgo; obligatorio.
     * @return el DTO del Riesgo identificado.
     * @throws ReglaNegocioException si el comando es invalido (422).
     */
    @Transactional
    public RiesgoDto identificarRiesgo(IdentificarRiesgoCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("El comando de identificacion de Riesgo es obligatorio.");
        }
        Riesgo riesgo = Riesgo.identificar(
                comando.descripcion(), comando.probabilidad(), comando.impacto(),
                comando.acciones(), actor);
        Riesgo guardado = riesgoRepository.save(riesgo);
        auditar(actor, "crear", RECURSO_RIESGO, guardado.getId(),
                "riesgo nivel '" + guardado.getNivelDerivado().valorBd() + "'");
        return RiesgoDto.de(guardado);
    }

    /**
     * Cambia el estado de un Riesgo validando la transicion (Req 70.3).
     *
     * @param riesgoId Riesgo; obligatorio.
     * @param destino  estado destino; obligatorio.
     * @return el DTO del Riesgo actualizado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public RiesgoDto cambiarEstadoRiesgo(UUID riesgoId, EstadoRiesgo destino) {
        String actor = actorActual();
        Riesgo riesgo = cargarRiesgo(riesgoId, actor);
        riesgo.cambiarEstado(destino, actor);
        Riesgo guardado = riesgoRepository.save(riesgo);
        auditar(actor, "cambiar_estado", RECURSO_RIESGO, guardado.getId(),
                "estado -> '" + guardado.getEstado().valorBd() + "'");
        return RiesgoDto.de(guardado);
    }

    /**
     * Consulta puntual de un Riesgo del tenant (Req 23.3).
     *
     * @param riesgoId identificador del Riesgo.
     * @return el DTO del Riesgo.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public RiesgoDto consultarRiesgo(UUID riesgoId) {
        return RiesgoDto.de(cargarRiesgo(riesgoId, actorActual()));
    }

    /**
     * Listado paginado de Riesgos del tenant con filtros opcionales (Req 70.3, 12).
     *
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param nivel    nivel derivado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados.
     * @return la pagina de Riesgos como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<RiesgoDto> listarRiesgos(EstadoRiesgo estado, NivelRiesgo nivel, Pageable pageable) {
        return riesgoRepository.buscar(estado, nivel, pageable).map(RiesgoDto::de);
    }

    // ------------------------------------------------------------------
    // Oportunidad_Calidad (clausula 6.1.3)
    // ------------------------------------------------------------------

    /**
     * Identifica una Oportunidad_Calidad nueva (Req 70.3).
     *
     * @param comando datos de la oportunidad; obligatorio.
     * @return el DTO de la Oportunidad_Calidad identificada.
     * @throws ReglaNegocioException si el comando es invalido (422).
     */
    @Transactional
    public OportunidadCalidadDto identificarOportunidad(IdentificarOportunidadCalidadCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("El comando de identificacion de Oportunidad_Calidad es obligatorio.");
        }
        OportunidadCalidad oportunidad = OportunidadCalidad.identificar(
                comando.descripcion(), comando.beneficioEsperado(), comando.acciones(), actor);
        OportunidadCalidad guardada = oportunidadRepository.save(oportunidad);
        auditar(actor, "crear", RECURSO_OPORTUNIDAD, guardada.getId(), "oportunidad de calidad identificada");
        return OportunidadCalidadDto.de(guardada);
    }

    /**
     * Cambia el estado de una Oportunidad_Calidad validando la transicion (Req 70.3).
     *
     * @param oportunidadId Oportunidad_Calidad; obligatorio.
     * @param destino       estado destino; obligatorio.
     * @return el DTO de la Oportunidad_Calidad actualizada.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public OportunidadCalidadDto cambiarEstadoOportunidad(UUID oportunidadId,
                                                          EstadoOportunidadCalidad destino) {
        String actor = actorActual();
        OportunidadCalidad oportunidad = cargarOportunidad(oportunidadId, actor);
        oportunidad.cambiarEstado(destino, actor);
        OportunidadCalidad guardada = oportunidadRepository.save(oportunidad);
        auditar(actor, "cambiar_estado", RECURSO_OPORTUNIDAD, guardada.getId(),
                "estado -> '" + guardada.getEstado().valorBd() + "'");
        return OportunidadCalidadDto.de(guardada);
    }

    /**
     * Consulta puntual de una Oportunidad_Calidad del tenant (Req 23.3).
     *
     * @param oportunidadId identificador de la Oportunidad_Calidad.
     * @return el DTO de la Oportunidad_Calidad.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public OportunidadCalidadDto consultarOportunidad(UUID oportunidadId) {
        return OportunidadCalidadDto.de(cargarOportunidad(oportunidadId, actorActual()));
    }

    /**
     * Listado paginado de Oportunidades de calidad del tenant con filtro opcional
     * (Req 70.3, 12).
     *
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados.
     * @return la pagina de Oportunidades de calidad como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<OportunidadCalidadDto> listarOportunidades(EstadoOportunidadCalidad estado,
                                                           Pageable pageable) {
        return oportunidadRepository.buscar(estado, pageable).map(OportunidadCalidadDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Riesgo cargarRiesgo(UUID id, String actor) {
        if (id == null) {
            throw new RecursoNoEncontradoException("No se encontro el Riesgo solicitado.");
        }
        return riesgoRepository.findById(id)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_RIESGO, id);
                    throw new RecursoNoEncontradoException("No se encontro el Riesgo solicitado.");
                });
    }

    private OportunidadCalidad cargarOportunidad(UUID id, String actor) {
        if (id == null) {
            throw new RecursoNoEncontradoException("No se encontro la Oportunidad_Calidad solicitada.");
        }
        return oportunidadRepository.findById(id)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_OPORTUNIDAD, id);
                    throw new RecursoNoEncontradoException("No se encontro la Oportunidad_Calidad solicitada.");
                });
    }

    private void auditar(String actor, String accion, String recurso, UUID id, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, recurso,
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
