package com.dessti.crm.calidad.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.calidad.adapter.out.persistence.ContextoOrganizacionRepository;
import com.dessti.crm.calidad.domain.ContextoOrganizacion;
import com.dessti.crm.calidad.domain.TipoContexto;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion del Contexto_Organizacion y el cambio climatico (Req 70.5,
 * clausulas 4.1 y 4.2). Determina y documenta cuestiones internas/externas pertinentes
 * al SGC -incluida la pertinencia del cambio climatico- conservando la justificacion aun
 * cuando la conclusion sea "no pertinente". Cada operacion se audita (Req 70.9).
 */
@Service
public class ServicioContextoOrganizacion {

    /** Tipo de recurso de auditoria/RBAC del Contexto_Organizacion. */
    static final String RECURSO = "contexto_organizacion";

    private final ContextoOrganizacionRepository contextoRepository;
    private final AuditoriaPort auditoria;

    public ServicioContextoOrganizacion(ContextoOrganizacionRepository contextoRepository,
                                        AuditoriaPort auditoria) {
        this.contextoRepository = contextoRepository;
        this.auditoria = auditoria;
    }

    /**
     * Determina y documenta una cuestion del contexto de la organizacion (Req 70.5).
     *
     * @param comando datos de la cuestion; obligatorio.
     * @return el DTO del Contexto_Organizacion determinado.
     * @throws ReglaNegocioException si el comando es invalido (422).
     */
    @Transactional
    public ContextoOrganizacionDto determinar(DeterminarContextoCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("El comando de determinacion de Contexto_Organizacion es obligatorio.");
        }
        ContextoOrganizacion contexto = ContextoOrganizacion.determinar(
                comando.cuestion(), comando.tipo(), comando.climaPertinente(),
                comando.justificacion(), comando.parteInteresada(), comando.expectativa(), actor);
        ContextoOrganizacion guardado = contextoRepository.save(contexto);
        auditar(actor, "crear", guardado.getId(),
                "contexto tipo '" + guardado.getTipo().valorBd()
                        + "' clima_pertinente=" + guardado.isClimaPertinente());
        return ContextoOrganizacionDto.de(guardado);
    }

    /**
     * Consulta puntual de un Contexto_Organizacion del tenant (Req 23.3).
     *
     * @param contextoId identificador del Contexto_Organizacion.
     * @return el DTO del Contexto_Organizacion.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ContextoOrganizacionDto consultar(UUID contextoId) {
        return ContextoOrganizacionDto.de(cargar(contextoId, actorActual()));
    }

    /**
     * Listado paginado de cuestiones de contexto del tenant con filtros opcionales
     * (Req 70.5, 12).
     *
     * @param tipo            tipo a filtrar; {@code null} no filtra.
     * @param climaPertinente pertinencia del clima a filtrar; {@code null} no filtra.
     * @param pageable        parametros de paginacion ya acotados.
     * @return la pagina de cuestiones de contexto como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ContextoOrganizacionDto> listar(TipoContexto tipo, Boolean climaPertinente,
                                                Pageable pageable) {
        return contextoRepository.buscar(tipo, climaPertinente, pageable)
                .map(ContextoOrganizacionDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private ContextoOrganizacion cargar(UUID id, String actor) {
        if (id == null) {
            throw new RecursoNoEncontradoException("No se encontro el Contexto_Organizacion solicitado.");
        }
        return contextoRepository.findById(id)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, id);
                    throw new RecursoNoEncontradoException("No se encontro el Contexto_Organizacion solicitado.");
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
                "intento de acceso a contexto_organizacion no disponible en el tenant [id="
                        + recursoId + "]", null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
