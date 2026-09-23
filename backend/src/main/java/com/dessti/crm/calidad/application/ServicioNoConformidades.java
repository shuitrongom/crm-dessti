package com.dessti.crm.calidad.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.calidad.adapter.out.persistence.AccionCorrectivaRepository;
import com.dessti.crm.calidad.adapter.out.persistence.NoConformidadRepository;
import com.dessti.crm.calidad.domain.AccionCorrectiva;
import com.dessti.crm.calidad.domain.EstadoAccionCorrectiva;
import com.dessti.crm.calidad.domain.EstadoNoConformidad;
import com.dessti.crm.calidad.domain.NoConformidad;
import com.dessti.crm.calidad.domain.OrigenNoConformidad;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion de la No_Conformidad y su Accion_Correctiva (Req 70.2,
 * clausula 10.2). Administra ambos agregados: registro y cambio de estado de la
 * No_Conformidad; apertura, avance, verificacion de eficacia y cierre de la
 * Accion_Correctiva. El cierre exige eficacia verificada (<strong>Property 43</strong>),
 * invariante que aplica el dominio ({@link AccionCorrectiva#cerrar}). Cada operacion se
 * audita (Req 70.9).
 */
@Service
public class ServicioNoConformidades {

    /** Tipo de recurso de auditoria/RBAC de la No_Conformidad. */
    static final String RECURSO_NC = "no_conformidad";

    /** Tipo de recurso de auditoria/RBAC de la Accion_Correctiva. */
    static final String RECURSO_AC = "accion_correctiva";

    private final NoConformidadRepository noConformidadRepository;
    private final AccionCorrectivaRepository accionCorrectivaRepository;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioNoConformidades(NoConformidadRepository noConformidadRepository,
                                   AccionCorrectivaRepository accionCorrectivaRepository,
                                   AuditoriaPort auditoria,
                                   Clock clock) {
        this.noConformidadRepository = noConformidadRepository;
        this.accionCorrectivaRepository = accionCorrectivaRepository;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // No_Conformidad
    // ------------------------------------------------------------------

    /**
     * Registra una No_Conformidad nueva (Req 70.2) con la marca temporal UTC del reloj.
     *
     * @param comando datos de la No_Conformidad; obligatorio.
     * @return el DTO de la No_Conformidad registrada.
     * @throws ReglaNegocioException si el comando es invalido (422).
     */
    @Transactional
    public NoConformidadDto registrarNoConformidad(RegistrarNoConformidadCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("El comando de registro de No_Conformidad es obligatorio.");
        }
        NoConformidad noConformidad = NoConformidad.registrar(
                comando.origen(), comando.descripcion(), comando.procesoAfectado(),
                clock.instant(), actor);
        NoConformidad guardada = noConformidadRepository.save(noConformidad);
        auditar(actor, "crear", RECURSO_NC, guardada.getId(),
                "no conformidad origen '" + guardada.getOrigen().valorBd() + "'");
        return NoConformidadDto.de(guardada);
    }

    /**
     * Cambia el estado de una No_Conformidad validando la transicion (Req 70.2).
     *
     * @param noConformidadId No_Conformidad; obligatorio.
     * @param destino         estado destino; obligatorio.
     * @return el DTO de la No_Conformidad actualizada.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public NoConformidadDto cambiarEstadoNoConformidad(UUID noConformidadId, EstadoNoConformidad destino) {
        String actor = actorActual();
        NoConformidad noConformidad = cargarNoConformidad(noConformidadId, actor);
        noConformidad.cambiarEstado(destino, actor);
        NoConformidad guardada = noConformidadRepository.save(noConformidad);
        auditar(actor, "cambiar_estado", RECURSO_NC, guardada.getId(),
                "estado -> '" + guardada.getEstado().valorBd() + "'");
        return NoConformidadDto.de(guardada);
    }

    /**
     * Consulta puntual de una No_Conformidad del tenant (Req 23.3).
     *
     * @param noConformidadId identificador de la No_Conformidad.
     * @return el DTO de la No_Conformidad.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public NoConformidadDto consultarNoConformidad(UUID noConformidadId) {
        return NoConformidadDto.de(cargarNoConformidad(noConformidadId, actorActual()));
    }

    /**
     * Listado paginado de No_Conformidades del tenant con filtros opcionales (Req 70.2, 12).
     *
     * @param origen   origen a filtrar; {@code null} no filtra.
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados.
     * @return la pagina de No_Conformidades como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<NoConformidadDto> listarNoConformidades(OrigenNoConformidad origen,
                                                        EstadoNoConformidad estado, Pageable pageable) {
        return noConformidadRepository.buscar(origen, estado, pageable).map(NoConformidadDto::de);
    }

    // ------------------------------------------------------------------
    // Accion_Correctiva
    // ------------------------------------------------------------------

    /**
     * Abre una Accion_Correctiva nueva (Req 70.2). Si indica una No_Conformidad de
     * origen, verifica que sea accesible en el tenant.
     *
     * @param comando datos de la Accion_Correctiva; obligatorio.
     * @return el DTO de la Accion_Correctiva abierta.
     * @throws ReglaNegocioException        si el comando es invalido (422).
     * @throws RecursoNoEncontradoException si la No_Conformidad indicada no es accesible (404).
     */
    @Transactional
    public AccionCorrectivaDto abrirAccionCorrectiva(AbrirAccionCorrectivaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("El comando de apertura de Accion_Correctiva es obligatorio.");
        }
        if (comando.noConformidadId() != null) {
            cargarNoConformidad(comando.noConformidadId(), actor);
        }
        AccionCorrectiva accion = AccionCorrectiva.abrir(
                comando.noConformidadId(), comando.responsableId(),
                comando.causaRaiz(), comando.accionesPlanificadas(), actor);
        AccionCorrectiva guardada = accionCorrectivaRepository.save(accion);
        auditar(actor, "crear", RECURSO_AC, guardada.getId(),
                "accion correctiva responsable " + guardada.getResponsableId());
        return AccionCorrectivaDto.de(guardada);
    }

    /**
     * Avanza el estado de una Accion_Correctiva por la maquina de estados, sin alcanzar
     * el cierre (el cierre pasa por {@link #cerrarAccionCorrectiva(UUID)}) (Req 70.2).
     *
     * @param accionCorrectivaId Accion_Correctiva; obligatorio.
     * @param destino            estado destino; obligatorio y distinto de {@code cerrada}.
     * @return el DTO de la Accion_Correctiva actualizada.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public AccionCorrectivaDto avanzarAccionCorrectiva(UUID accionCorrectivaId,
                                                       EstadoAccionCorrectiva destino) {
        String actor = actorActual();
        AccionCorrectiva accion = cargarAccion(accionCorrectivaId, actor);
        accion.avanzar(destino, actor);
        AccionCorrectiva guardada = accionCorrectivaRepository.save(accion);
        auditar(actor, "cambiar_estado", RECURSO_AC, guardada.getId(),
                "estado -> '" + guardada.getEstado().valorBd() + "'");
        return AccionCorrectivaDto.de(guardada);
    }

    /**
     * Registra la verificacion de la eficacia de la Accion_Correctiva, requisito previo
     * del cierre (Req 70.2; Property 43).
     *
     * @param accionCorrectivaId Accion_Correctiva; obligatorio.
     * @param evidencia          evidencia de la verificacion; opcional.
     * @return el DTO de la Accion_Correctiva actualizada.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public AccionCorrectivaDto verificarEficacia(UUID accionCorrectivaId, String evidencia) {
        String actor = actorActual();
        AccionCorrectiva accion = cargarAccion(accionCorrectivaId, actor);
        accion.verificarEficacia(evidencia, actor);
        AccionCorrectiva guardada = accionCorrectivaRepository.save(accion);
        auditar(actor, "cambiar_estado", RECURSO_AC, guardada.getId(), "eficacia verificada");
        return AccionCorrectivaDto.de(guardada);
    }

    /**
     * Cierra una Accion_Correctiva. La <strong>guarda de cierre (Property 43)</strong> del
     * dominio rechaza el cierre si la eficacia no esta verificada (Req 70.2).
     *
     * @param accionCorrectivaId Accion_Correctiva; obligatorio.
     * @return el DTO de la Accion_Correctiva cerrada.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException        si la eficacia no esta verificada (422; Property 43).
     */
    @Transactional
    public AccionCorrectivaDto cerrarAccionCorrectiva(UUID accionCorrectivaId) {
        String actor = actorActual();
        AccionCorrectiva accion = cargarAccion(accionCorrectivaId, actor);
        accion.cerrar(actor);
        AccionCorrectiva guardada = accionCorrectivaRepository.save(accion);
        auditar(actor, "cambiar_estado", RECURSO_AC, guardada.getId(),
                "accion correctiva cerrada con eficacia verificada");
        return AccionCorrectivaDto.de(guardada);
    }

    /**
     * Consulta puntual de una Accion_Correctiva del tenant (Req 23.3).
     *
     * @param accionCorrectivaId identificador de la Accion_Correctiva.
     * @return el DTO de la Accion_Correctiva.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public AccionCorrectivaDto consultarAccionCorrectiva(UUID accionCorrectivaId) {
        return AccionCorrectivaDto.de(cargarAccion(accionCorrectivaId, actorActual()));
    }

    /**
     * Listado paginado de Acciones_Correctivas del tenant con filtros opcionales
     * (Req 70.2, 12).
     *
     * @param estado          estado a filtrar; {@code null} no filtra.
     * @param noConformidadId No_Conformidad a filtrar; {@code null} no filtra.
     * @param pageable        parametros de paginacion ya acotados.
     * @return la pagina de Acciones_Correctivas como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<AccionCorrectivaDto> listarAccionesCorrectivas(EstadoAccionCorrectiva estado,
                                                               UUID noConformidadId, Pageable pageable) {
        return accionCorrectivaRepository.buscar(estado, noConformidadId, pageable)
                .map(AccionCorrectivaDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private NoConformidad cargarNoConformidad(UUID id, String actor) {
        if (id == null) {
            throw new RecursoNoEncontradoException("No se encontro la No_Conformidad solicitada.");
        }
        return noConformidadRepository.findById(id)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_NC, id);
                    throw new RecursoNoEncontradoException("No se encontro la No_Conformidad solicitada.");
                });
    }

    private AccionCorrectiva cargarAccion(UUID id, String actor) {
        if (id == null) {
            throw new RecursoNoEncontradoException("No se encontro la Accion_Correctiva solicitada.");
        }
        return accionCorrectivaRepository.findById(id)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_AC, id);
                    throw new RecursoNoEncontradoException("No se encontro la Accion_Correctiva solicitada.");
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
