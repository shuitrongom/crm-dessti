package com.dessti.crm.calidad.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.calidad.adapter.out.persistence.AccionCorrectivaRepository;
import com.dessti.crm.calidad.adapter.out.persistence.QuejaClienteRepository;
import com.dessti.crm.calidad.domain.AccionCorrectiva;
import com.dessti.crm.calidad.domain.EstadoQuejaCliente;
import com.dessti.crm.calidad.domain.OrigenQueja;
import com.dessti.crm.calidad.domain.QuejaCliente;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion de la Queja_Cliente (Req 70.1, 70.8, clausula 10.2). Registra
 * reclamaciones del Cliente, permite vincularlas -sin obligar- a una Accion_Correctiva,
 * atenderlas y listarlas con filtros. Cada operacion se audita (Req 70.9). Replica el
 * patron de {@code ServicioBandeja}.
 *
 * <h2>Origen social sin ciclo de modulos (Req 70.1, 70.8)</h2>
 * <p>La creacion desde una Conversacion social se realiza pasando {@code origen = social}
 * y el {@code canalSocialId} (referencia debil) junto al {@code clienteId} ya resuelto
 * por el modulo {@code social}; este servicio no importa internals de {@code social}.</p>
 */
@Service
public class ServicioQuejasCliente {

    /** Tipo de recurso de auditoria/RBAC de la Queja_Cliente. */
    static final String RECURSO = "queja_cliente";

    private final QuejaClienteRepository quejaRepository;
    private final AccionCorrectivaRepository accionCorrectivaRepository;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioQuejasCliente(QuejaClienteRepository quejaRepository,
                                 AccionCorrectivaRepository accionCorrectivaRepository,
                                 AuditoriaPort auditoria,
                                 Clock clock) {
        this.quejaRepository = quejaRepository;
        this.accionCorrectivaRepository = accionCorrectivaRepository;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Registra una Queja_Cliente nueva (Req 70.1, 70.8) con la marca temporal UTC del
     * reloj del servicio. Una queja social se registra con {@code origen = social} y el
     * {@code canalSocialId} de la Conversacion.
     *
     * @param comando datos de la queja; obligatorio.
     * @return el DTO de la Queja_Cliente registrada.
     * @throws ReglaNegocioException si el comando es invalido (422).
     */
    @Transactional
    public QuejaClienteDto registrar(RegistrarQuejaClienteCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("El comando de registro de Queja_Cliente es obligatorio.");
        }
        QuejaCliente queja = QuejaCliente.registrar(
                comando.clienteId(), comando.origen(), comando.canalSocialId(),
                comando.descripcion(), clock.instant(), actor);
        QuejaCliente guardada = quejaRepository.save(queja);
        auditar(actor, "crear", guardada.getId(),
                "queja origen '" + guardada.getOrigen().valorBd() + "' cliente " + guardada.getClienteId());
        return QuejaClienteDto.de(guardada);
    }

    /**
     * Vincula -sin obligar- la Queja_Cliente a una Accion_Correctiva existente,
     * transitando la queja a {@code vinculada} (Req 70.1, clausula 10.2).
     *
     * @param quejaId            Queja_Cliente; obligatorio.
     * @param accionCorrectivaId Accion_Correctiva a vincular; obligatorio.
     * @return el DTO de la Queja_Cliente vinculada.
     * @throws RecursoNoEncontradoException si la queja o la Accion_Correctiva no son accesibles (404).
     */
    @Transactional
    public QuejaClienteDto vincularAccionCorrectiva(UUID quejaId, UUID accionCorrectivaId) {
        String actor = actorActual();
        if (accionCorrectivaId == null) {
            throw new ReglaNegocioException("El vinculo debe indicar la Accion_Correctiva.");
        }
        QuejaCliente queja = cargar(quejaId, actor);
        AccionCorrectiva accion = accionCorrectivaRepository.findById(accionCorrectivaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Accion_Correctiva solicitada."));
        queja.vincularAccionCorrectiva(accion.getId(), actor);
        QuejaCliente guardada = quejaRepository.save(queja);
        auditar(actor, "cambiar_estado", guardada.getId(),
                "vinculada a Accion_Correctiva " + accion.getId());
        return QuejaClienteDto.de(guardada);
    }

    /**
     * Marca la Queja_Cliente como atendida (Req 70.1).
     *
     * @param quejaId Queja_Cliente; obligatorio.
     * @return el DTO de la Queja_Cliente atendida.
     * @throws RecursoNoEncontradoException si la queja no es accesible (404).
     */
    @Transactional
    public QuejaClienteDto atender(UUID quejaId) {
        String actor = actorActual();
        QuejaCliente queja = cargar(quejaId, actor);
        queja.atender(actor);
        QuejaCliente guardada = quejaRepository.save(queja);
        auditar(actor, "cambiar_estado", guardada.getId(), "queja atendida");
        return QuejaClienteDto.de(guardada);
    }

    /**
     * Consulta puntual de una Queja_Cliente del tenant (Req 23.3).
     *
     * @param quejaId identificador de la Queja_Cliente.
     * @return el DTO de la Queja_Cliente.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public QuejaClienteDto consultar(UUID quejaId) {
        return QuejaClienteDto.de(cargar(quejaId, actorActual()));
    }

    /**
     * Listado paginado de Quejas del tenant con filtros opcionales (Req 70.1, 12).
     *
     * @param origen    origen a filtrar; {@code null} no filtra.
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param estado    estado a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados.
     * @return la pagina de Quejas como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<QuejaClienteDto> listar(OrigenQueja origen, UUID clienteId,
                                        EstadoQuejaCliente estado, Pageable pageable) {
        return quejaRepository.buscar(origen, clienteId, estado, pageable).map(QuejaClienteDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private QuejaCliente cargar(UUID quejaId, String actor) {
        if (quejaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Queja_Cliente solicitada.");
        }
        return quejaRepository.findById(quejaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, quejaId);
                    throw new RecursoNoEncontradoException("No se encontro la Queja_Cliente solicitada.");
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
                "intento de acceso a queja_cliente no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
