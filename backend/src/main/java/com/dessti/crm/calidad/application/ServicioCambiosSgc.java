package com.dessti.crm.calidad.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.calidad.adapter.out.persistence.CambioSgcRepository;
import com.dessti.crm.calidad.domain.CambioSgc;
import com.dessti.crm.calidad.domain.EstadoCambioSgc;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion de la gestion del cambio del SGC (Req 70.4, clausula 6.3).
 * Registra un Cambio_SGC exigiendo proposito, consecuencias potenciales, recursos
 * necesarios y responsable antes de aprobar; la aprobacion queda en auditoria con actor
 * y marca temporal UTC. Cada operacion se audita (Req 70.9).
 */
@Service
public class ServicioCambiosSgc {

    /** Tipo de recurso de auditoria/RBAC del Cambio_SGC. */
    static final String RECURSO = "cambio_sgc";

    private final CambioSgcRepository cambioRepository;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioCambiosSgc(CambioSgcRepository cambioRepository,
                              AuditoriaPort auditoria,
                              Clock clock) {
        this.cambioRepository = cambioRepository;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Propone un Cambio_SGC nuevo (Req 70.4). El dominio exige desde el inicio los
     * campos requeridos para poder aprobar luego.
     *
     * @param comando datos del cambio; obligatorio.
     * @return el DTO del Cambio_SGC propuesto.
     * @throws ReglaNegocioException si el comando es invalido (422).
     */
    @Transactional
    public CambioSgcDto proponer(ProponerCambioSgcCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("El comando de propuesta de Cambio_SGC es obligatorio.");
        }
        CambioSgc cambio = CambioSgc.proponer(
                comando.titulo(), comando.proposito(), comando.consecuenciasPotenciales(),
                comando.recursosNecesarios(), comando.responsableId(), actor);
        CambioSgc guardado = cambioRepository.save(cambio);
        auditar(actor, "crear", guardado.getId(), "cambio SGC propuesto: '" + guardado.getTitulo() + "'");
        return CambioSgcDto.de(guardado);
    }

    /**
     * Aprueba un Cambio_SGC registrando el actor y la marca temporal UTC de la
     * aprobacion (Req 70.4). La guarda de campos obligatorios la impone el dominio.
     *
     * @param cambioId Cambio_SGC; obligatorio.
     * @return el DTO del Cambio_SGC aprobado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public CambioSgcDto aprobar(UUID cambioId) {
        String actor = actorActual();
        CambioSgc cambio = cargar(cambioId, actor);
        cambio.aprobar(actor, clock.instant());
        CambioSgc guardado = cambioRepository.save(cambio);
        auditar(actor, "cambiar_estado", guardado.getId(),
                "cambio SGC aprobado por '" + guardado.getAprobadoPor() + "' en " + guardado.getAprobadoEn());
        return CambioSgcDto.de(guardado);
    }

    /**
     * Rechaza un Cambio_SGC (estado final alterno, Req 70.4).
     *
     * @param cambioId Cambio_SGC; obligatorio.
     * @return el DTO del Cambio_SGC rechazado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public CambioSgcDto rechazar(UUID cambioId) {
        String actor = actorActual();
        CambioSgc cambio = cargar(cambioId, actor);
        cambio.rechazar(actor);
        CambioSgc guardado = cambioRepository.save(cambio);
        auditar(actor, "cambiar_estado", guardado.getId(), "cambio SGC rechazado");
        return CambioSgcDto.de(guardado);
    }

    /**
     * Marca un Cambio_SGC como implementado (Req 70.4).
     *
     * @param cambioId Cambio_SGC; obligatorio.
     * @return el DTO del Cambio_SGC implementado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public CambioSgcDto implementar(UUID cambioId) {
        String actor = actorActual();
        CambioSgc cambio = cargar(cambioId, actor);
        cambio.implementar(actor);
        CambioSgc guardado = cambioRepository.save(cambio);
        auditar(actor, "cambiar_estado", guardado.getId(), "cambio SGC implementado");
        return CambioSgcDto.de(guardado);
    }

    /**
     * Consulta puntual de un Cambio_SGC del tenant (Req 23.3).
     *
     * @param cambioId identificador del Cambio_SGC.
     * @return el DTO del Cambio_SGC.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public CambioSgcDto consultar(UUID cambioId) {
        return CambioSgcDto.de(cargar(cambioId, actorActual()));
    }

    /**
     * Listado paginado de Cambios_SGC del tenant con filtro opcional (Req 70.4, 12).
     *
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados.
     * @return la pagina de Cambios_SGC como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<CambioSgcDto> listar(EstadoCambioSgc estado, Pageable pageable) {
        return cambioRepository.buscar(estado, pageable).map(CambioSgcDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private CambioSgc cargar(UUID id, String actor) {
        if (id == null) {
            throw new RecursoNoEncontradoException("No se encontro el Cambio_SGC solicitado.");
        }
        return cambioRepository.findById(id)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, id);
                    throw new RecursoNoEncontradoException("No se encontro el Cambio_SGC solicitado.");
                });
    }

    private void auditar(String actor, String accion, UUID id, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO,
                detalle + " [id=" + id + "]", null, null));
    }

    private void auditarAccesoCruzado(String actor, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", RECURSO,
                "intento de acceso a cambio_sgc no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
