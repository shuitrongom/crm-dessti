package com.dessti.crm.comercial.oportunidad.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.comercial.oportunidad.adapter.out.persistence.OportunidadRepository;
import com.dessti.crm.comercial.oportunidad.domain.EtapaOportunidad;
import com.dessti.crm.comercial.oportunidad.domain.Oportunidad;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de las {@link Oportunidad}
 * del pipeline comercial (Req 14). Replica el patron establecido por
 * {@code ServicioProductos}/{@code ServicioClientes}.
 *
 * <h2>Operaciones (Req 14)</h2>
 * <ul>
 *   <li><strong>crearOportunidad (Req 14.1):</strong> verifica que el Cliente
 *       exista y este activo en el tenant (via {@link ClienteExistentePort};
 *       404 si no), valida titulo/valor (422), fija etapa inicial {@code nuevo},
 *       persiste y audita.</li>
 *   <li><strong>asignarResponsable (Req 14.2):</strong> registra al Usuario de
 *       ventas responsable y audita.</li>
 *   <li><strong>cambiarEtapa (Req 14.3, 14.4, 14.9):</strong> aplica la maquina
 *       de estados pura (409 si la transicion es invalida) y audita la etapa
 *       anterior y la nueva.</li>
 *   <li><strong>convertirEnCotizacion (Req 14.5, 14.6):</strong> guarda que la
 *       etapa sea {@code ganado} (422 si no), delega la creacion en
 *       {@link CreacionCotizacionPort} (tarea 17.2) y audita el intento.</li>
 *   <li><strong>consultar (Req 4.3, 23.3):</strong> 404 + auditoria del intento
 *       si no es accesible.</li>
 *   <li><strong>listar (Req 14.7, 14.8):</strong> listado paginado (20/100) con
 *       filtros por Cliente, etapa y responsable.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 14.9)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se registra via
 * {@link AuditoriaPort} como evento de tenant con el actor derivado del contexto
 * de seguridad; el cambio de etapa incluye la etapa anterior y la nueva (Req 14.9).</p>
 */
@Service
public class ServicioOportunidades {

    /** Tipo de recurso de auditoria/RBAC de la Oportunidad. */
    static final String RECURSO_OPORTUNIDAD = "oportunidad";

    private final OportunidadRepository oportunidadRepository;
    private final ClienteExistentePort clienteExistente;
    private final CanalVentaExistentePort canalVentaExistente;
    private final AuditoriaPort auditoria;

    /**
     * Puerto de creacion de Cotizaciones (Req 14.5). Es <strong>opcional</strong>:
     * la tarea 17.2 aporta su implementacion. Mientras no exista un bean, la
     * conversion se rechaza de forma controlada (ver {@link #convertirEnCotizacion}).
     */
    private final Optional<CreacionCotizacionPort> creacionCotizacion;

    public ServicioOportunidades(OportunidadRepository oportunidadRepository,
                                 ClienteExistentePort clienteExistente,
                                 CanalVentaExistentePort canalVentaExistente,
                                 AuditoriaPort auditoria,
                                 Optional<CreacionCotizacionPort> creacionCotizacion) {
        this.oportunidadRepository = oportunidadRepository;
        this.clienteExistente = clienteExistente;
        this.canalVentaExistente = canalVentaExistente;
        this.auditoria = auditoria;
        this.creacionCotizacion = creacionCotizacion;
    }

    /**
     * Da de alta una Oportunidad asociada a un Cliente existente, en etapa
     * inicial {@code nuevo} (Req 14.1).
     *
     * @param comando datos de la Oportunidad a crear.
     * @return el DTO de la Oportunidad creada.
     * @throws RecursoNoEncontradoException si el Cliente no existe/activo en el
     *         tenant (404, Req 14.1, 23.3).
     * @throws ReglaNegocioException si faltan o son invalidos los datos (422).
     */
    @Transactional
    public OportunidadDto crearOportunidad(CrearOportunidadCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Oportunidad son obligatorios.");
        }
        if (comando.clienteId() == null) {
            throw new ReglaNegocioException("La Oportunidad debe asociarse a un Cliente existente.");
        }
        if (!clienteExistente.existeClienteActivo(comando.clienteId())) {
            auditarAccesoCruzado(actor, "cliente", comando.clienteId());
            throw new RecursoNoEncontradoException("No se encontro el Cliente indicado para la Oportunidad.");
        }
        Oportunidad oportunidad = Oportunidad.crear(
                comando.clienteId(), comando.titulo(), comando.valorEstimado(), actor);
        Oportunidad guardada = oportunidadRepository.save(oportunidad);
        auditar(actor, "crear", guardada.getId(),
                "creada oportunidad '" + guardada.getTitulo() + "' en etapa '"
                        + guardada.getEtapa().valorBd() + "'", null, null);
        return OportunidadDto.de(guardada);
    }

    /**
     * Registra al Usuario de ventas responsable de una Oportunidad (Req 14.2).
     *
     * @param oportunidadId identificador de la Oportunidad.
     * @param usuarioId     identificador del Usuario responsable; obligatorio.
     * @return el DTO de la Oportunidad actualizada.
     * @throws RecursoNoEncontradoException si la Oportunidad no es accesible (404).
     * @throws ReglaNegocioException si {@code usuarioId} es nulo (422).
     */
    @Transactional
    public OportunidadDto asignarResponsable(UUID oportunidadId, UUID usuarioId) {
        String actor = actorActual();
        Oportunidad oportunidad = cargar(oportunidadId, actor);
        oportunidad.asignarResponsable(usuarioId, actor);
        Oportunidad guardada = oportunidadRepository.save(oportunidad);
        auditar(actor, "asignar_responsable", guardada.getId(),
                "asignado responsable [usuario=" + usuarioId + "]", null, null);
        return OportunidadDto.de(guardada);
    }

    /**
     * Clasifica una Oportunidad por canal de venta, asigna o modifica el canal, o
     * lo limpia (Req 63.1). Si se indica un canal, se verifica que exista y este
     * activo en el tenant (via {@link CanalVentaExistentePort}); si no existe, se
     * responde 404 y se audita el intento de acceso cruzado (Req 23.3). La
     * asignacion/modificacion se audita con el actor, la accion, el recurso y la
     * marca temporal, registrando el canal anterior y el nuevo (Req 63.3).
     *
     * @param oportunidadId identificador de la Oportunidad.
     * @param canalVentaId  identificador del Canal_Venta; {@code null} para limpiar
     *                      la clasificacion.
     * @return el DTO de la Oportunidad con su canal actualizado.
     * @throws RecursoNoEncontradoException si la Oportunidad no es accesible (404),
     *         o si el canal indicado no existe/activo en el tenant (404, Req 63.1).
     */
    @Transactional
    public OportunidadDto asignarCanalVenta(UUID oportunidadId, UUID canalVentaId) {
        String actor = actorActual();
        Oportunidad oportunidad = cargar(oportunidadId, actor);
        if (canalVentaId != null && !canalVentaExistente.existeCanalVentaActivo(canalVentaId)) {
            auditarAccesoCruzado(actor, "canal_venta", canalVentaId);
            throw new RecursoNoEncontradoException(
                    "No se encontro el canal de venta indicado para la Oportunidad.");
        }
        UUID anterior = oportunidad.getCanalVentaId();
        oportunidad.asignarCanalVenta(canalVentaId, actor);
        Oportunidad guardada = oportunidadRepository.save(oportunidad);
        auditar(actor, "asignar_canal", guardada.getId(),
                "clasificada por canal de venta [canal=" + canalVentaId + "]",
                anterior == null ? null : anterior.toString(),
                canalVentaId == null ? null : canalVentaId.toString());
        return OportunidadDto.de(guardada);
    }

    /**
     * Cambia la etapa de una Oportunidad aplicando la maquina de estados pura del
     * pipeline (Req 14.3, 14.4) y auditando la etapa anterior y la nueva (Req 14.9).
     *
     * @param oportunidadId identificador de la Oportunidad.
     * @param nuevaEtapa    etiqueta de la etapa destino; obligatoria (Req 14.3).
     * @return el DTO de la Oportunidad con su nueva etapa.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si la etapa es nula/desconocida (422).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la
     *         transicion no esta permitida (409, Req 14.4).
     */
    @Transactional
    public OportunidadDto cambiarEtapa(UUID oportunidadId, String nuevaEtapa) {
        String actor = actorActual();
        EtapaOportunidad destino = interpretarEtapa(nuevaEtapa);
        Oportunidad oportunidad = cargar(oportunidadId, actor);
        EtapaOportunidad anterior = oportunidad.getEtapa();
        oportunidad.cambiarEtapa(destino, actor);
        Oportunidad guardada = oportunidadRepository.save(oportunidad);
        auditar(actor, "cambiar_etapa", guardada.getId(),
                "cambio de etapa '" + anterior.valorBd() + "' -> '" + destino.valorBd() + "'",
                anterior.valorBd(), destino.valorBd());
        return OportunidadDto.de(guardada);
    }

    /**
     * Convierte una Oportunidad en etapa {@code ganado} en una Cotizacion
     * (Req 14.5, 14.6). Aplica la guarda de etapa y delega la creacion de la
     * Cotizacion en {@link CreacionCotizacionPort}. Audita el intento de
     * conversion.
     *
     * @param oportunidadId identificador de la Oportunidad.
     * @return el identificador de la Cotizacion creada.
     * @throws RecursoNoEncontradoException si la Oportunidad no es accesible (404).
     * @throws ReglaNegocioException si la etapa no es {@code ganado} (Req 14.6) o
     *         si la conversion aun no esta disponible (tarea 17.2 pendiente).
     */
    @Transactional
    public UUID convertirEnCotizacion(UUID oportunidadId) {
        String actor = actorActual();
        Oportunidad oportunidad = cargar(oportunidadId, actor);
        if (!oportunidad.esConvertible()) {
            auditar(actor, "convertir_rechazada", oportunidad.getId(),
                    "conversion rechazada: etapa '" + oportunidad.getEtapa().valorBd()
                            + "' no es 'ganado'", null, null);
            throw new ReglaNegocioException(
                    "Se requiere una Oportunidad en etapa 'ganado' para convertirla en Cotizacion.");
        }
        CreacionCotizacionPort puerto = creacionCotizacion.orElseThrow(() -> new ReglaNegocioException(
                "La conversion a Cotizacion aun no esta disponible."));
        UUID cotizacionId = puerto.crearDesdeOportunidad(
                oportunidad.getId(), oportunidad.getClienteId(), actor);
        oportunidad.marcarConvertida(cotizacionId, actor);
        oportunidadRepository.save(oportunidad);
        auditar(actor, "convertir", oportunidad.getId(),
                "convertida en cotizacion [cotizacion=" + cotizacionId + "]", null, null);
        return cotizacionId;
    }

    /**
     * Consulta puntual de una Oportunidad del tenant (Req 4.3, 23.3).
     *
     * @param oportunidadId identificador de la Oportunidad.
     * @return el DTO de la Oportunidad.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public OportunidadDto consultarOportunidad(UUID oportunidadId) {
        String actor = actorActual();
        return OportunidadDto.de(cargar(oportunidadId, actor));
    }

    /**
     * Listado paginado de Oportunidades del tenant con filtros opcionales por
     * Cliente, etapa y responsable (Req 14.7, 14.8). Un filtro nulo no restringe.
     *
     * @param clienteId     Cliente a filtrar; {@code null} no filtra.
     * @param etapa         etiqueta de etapa a filtrar; {@code null}/blanco no filtra.
     * @param responsableId Usuario responsable a filtrar; {@code null} no filtra.
     * @param canalVentaId  canal de venta a filtrar; {@code null} no filtra (Req 63.2).
     * @param pageable      parametros de paginacion ya acotados (20/100).
     * @return la pagina de Oportunidades como DTOs.
     * @throws ReglaNegocioException si la etiqueta de etapa es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<OportunidadDto> listarOportunidades(UUID clienteId, String etapa,
                                                    UUID responsableId, UUID canalVentaId,
                                                    Pageable pageable) {
        EtapaOportunidad etapaFiltro = (etapa == null || etapa.isBlank()) ? null : interpretarEtapa(etapa);
        return oportunidadRepository.buscarConFiltros(clienteId, etapaFiltro, responsableId, canalVentaId, pageable)
                .map(OportunidadDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Oportunidad cargar(UUID oportunidadId, String actor) {
        if (oportunidadId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Oportunidad solicitada.");
        }
        return oportunidadRepository.findById(oportunidadId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_OPORTUNIDAD, oportunidadId);
                    throw new RecursoNoEncontradoException("No se encontro la Oportunidad solicitada.");
                });
    }

    private EtapaOportunidad interpretarEtapa(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("La etapa destino es obligatoria.");
        }
        try {
            return EtapaOportunidad.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Etapa de Oportunidad desconocida: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, UUID oportunidadId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_OPORTUNIDAD,
                detalle + " [id=" + oportunidadId + "]", valorAnterior, valorNuevo));
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
