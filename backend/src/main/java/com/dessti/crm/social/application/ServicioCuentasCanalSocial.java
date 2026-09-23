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
import com.dessti.crm.social.adapter.out.persistence.CuentaCanalSocialRepository;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.CuentaCanalSocial;

/**
 * Servicio de aplicacion que gobierna las {@link CuentaCanalSocial} (Req 64.1,
 * 64.2, 11). Replica el patron de {@code ServicioOrdenesFabricacion}.
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>crear:</strong> registra la conexion de la Empresa a un Canal_Social
 *       guardando solo la referencia a las credenciales (NUNCA el valor, Req 11);
 *       409 si ya existe la cuenta para el canal e identificador.</li>
 *   <li><strong>consultar/listar:</strong> 404 si no es accesible (Req 23.3);
 *       listado paginado (20/100) con filtro por canal.</li>
 * </ul>
 *
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (Req 23.4) y cada
 * operacion relevante se audita (Req 64.15).</p>
 */
@Service
public class ServicioCuentasCanalSocial {

    /** Tipo de recurso de auditoria/RBAC de la Cuenta_Canal_Social. */
    static final String RECURSO_CUENTA = "cuenta_canal_social";

    private final CuentaCanalSocialRepository cuentaRepository;
    private final AuditoriaPort auditoria;

    public ServicioCuentasCanalSocial(CuentaCanalSocialRepository cuentaRepository,
                                      AuditoriaPort auditoria) {
        this.cuentaRepository = cuentaRepository;
        this.auditoria = auditoria;
    }

    /**
     * Registra una Cuenta_Canal_Social nueva (Req 64.1, 64.2). Guarda solo la
     * referencia a las credenciales (Req 11).
     *
     * @param canal                 Canal_Social; obligatorio.
     * @param identificadorExterno  identificador en el canal; obligatorio.
     * @param nombre                nombre descriptivo; obligatorio.
     * @param credencialesRef       referencia al secreto (NUNCA el valor, Req 11).
     * @return el DTO de la cuenta creada.
     * @throws ReglaNegocioException      si algun dato obligatorio falta/es invalido (422).
     * @throws ConflictoUnicidadException si ya existe la cuenta para canal/identificador (409).
     */
    @Transactional
    public CuentaCanalSocialDto crear(CanalSocial canal, String identificadorExterno,
                                      String nombre, String credencialesRef) {
        String actor = actorActual();
        if (canal == null) {
            throw new ReglaNegocioException("La Cuenta_Canal_Social debe indicar el Canal_Social.");
        }
        String idExterno = (identificadorExterno == null) ? null : identificadorExterno.strip();
        if (idExterno != null && !idExterno.isBlank()
                && cuentaRepository.existsByCanalAndIdentificadorExterno(canal, idExterno)) {
            throw new ConflictoUnicidadException(
                    "ya existe una Cuenta_Canal_Social para ese canal e identificador.");
        }

        CuentaCanalSocial cuenta =
                CuentaCanalSocial.crear(canal, identificadorExterno, nombre, credencialesRef, actor);
        CuentaCanalSocial guardada;
        try {
            guardada = cuentaRepository.saveAndFlush(cuenta);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoUnicidadException(
                    "ya existe una Cuenta_Canal_Social para ese canal e identificador.");
        }
        auditar(actor, "crear", guardada.getId(),
                "registrada Cuenta_Canal_Social canal '" + guardada.getCanal().valorBd()
                        + "' identificador '" + guardada.getIdentificadorExterno() + "'");
        return CuentaCanalSocialDto.de(guardada);
    }

    /**
     * Consulta puntual de una Cuenta_Canal_Social del tenant (Req 23.3).
     *
     * @param cuentaId identificador de la cuenta.
     * @return el DTO de la cuenta.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public CuentaCanalSocialDto consultar(UUID cuentaId) {
        String actor = actorActual();
        return CuentaCanalSocialDto.de(cargar(cuentaId, actor));
    }

    /**
     * Listado paginado de cuentas del tenant con filtro opcional por canal
     * (Req 64.1).
     *
     * @param canal    Canal_Social a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de cuentas como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<CuentaCanalSocialDto> listar(CanalSocial canal, Pageable pageable) {
        return cuentaRepository.buscarConFiltros(canal, pageable).map(CuentaCanalSocialDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    CuentaCanalSocial cargar(UUID cuentaId, String actor) {
        if (cuentaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Cuenta_Canal_Social solicitada.");
        }
        return cuentaRepository.findById(cuentaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CUENTA, cuentaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Cuenta_Canal_Social solicitada.");
                });
    }

    private void auditar(String actor, String accion, UUID id, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_CUENTA,
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
