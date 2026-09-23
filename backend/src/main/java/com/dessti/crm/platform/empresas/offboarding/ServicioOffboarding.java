package com.dessti.crm.platform.empresas.offboarding;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.empresas.Empresa;
import com.dessti.crm.platform.empresas.EmpresaRepository;
import com.dessti.crm.platform.empresas.EstadoEmpresa;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Servicio de aplicacion que orquesta el <strong>offboarding y la portabilidad
 * del tenant</strong> (Req 69, tarea 14.4): exportacion de datos por
 * {@code tenant_id} (Req 69.1), cancelacion con Periodo_Gracia configurable
 * (Req 69.2) y eliminacion/anonimizacion definitiva acotada por tenant tras la
 * gracia (Req 69.3), preservando los comprobantes fiscales (Req 69.4) y
 * auditando el alcance de cada operacion (Req 69.6).
 *
 * <h2>Marco extensible (Req 69, diseno)</h2>
 * <p>El servicio <em>no</em> conoce las tablas de negocio: inyecta la lista de
 * todos los beans {@link RecursoTenantOffboarding} y los itera. Como los modulos
 * de negocio (clientes, cotizaciones, facturas, etc.) aun no existen (bloques
 * 15+), la lista puede contener solo el exportador de metadatos de plataforma
 * ({@code ExportadorMetadatosEmpresa}); el marco funciona igualmente y cada
 * modulo futuro se integra registrando un bean, sin modificar este servicio.</p>
 *
 * <h2>Aislamiento reforzado por RLS (Req 69.5, 23)</h2>
 * <p>Tanto la exportacion como la eliminacion se ejecutan <strong>bajo el ambito
 * RLS del tenant objetivo</strong>: dentro de la transaccion se fija
 * {@code app.current_tenant = tenantId} mediante
 * {@link TenantSessionInitializer#applyTenant(UUID)}, de modo que las politicas
 * de Row-Level Security de PostgreSQL refuerzan que solo se alcancen filas de esa
 * Empresa. Nunca se opera sin un {@code tenantId} no nulo (fail-closed).</p>
 *
 * <h2>Autorizacion (Req 69.1, 69.3)</h2>
 * <ul>
 *   <li><strong>exportar:</strong> permitido al {@code super_admin} (permiso de
 *       plataforma {@code offboarding:exportar}) o a la propia Empresa via su
 *       {@code admin_empresa} (permiso de nivel empresa
 *       {@code offboarding_propio:exportar}, V10). El {@code admin_empresa} solo
 *       puede exportar <em>su</em> tenant: si el {@code id} de la ruta no coincide
 *       con el tenant del contexto, se responde 404 (Req 23.3, anti-enumeracion).</li>
 *   <li><strong>eliminarDefinitivamente:</strong> exclusivo del
 *       {@code super_admin} (permiso {@code offboarding:cambiar_estado}); el
 *       control de acceso lo impone el controlador REST.</li>
 * </ul>
 */
@Service
public class ServicioOffboarding {

    /** Recurso de auditoria de nivel plataforma asociado al offboarding. */
    static final String RECURSO_OFFBOARDING = "offboarding";

    /** Permiso de plataforma que distingue al super_admin del admin_empresa. */
    static final String RECURSO_PERMISO_PLATAFORMA = "offboarding";
    static final String OPERACION_EXPORTAR = "exportar";

    private final EmpresaRepository empresaRepository;
    private final List<RecursoTenantOffboarding> recursos;
    private final TenantSessionInitializer tenantSession;
    private final AuditoriaPort auditoria;
    private final Autorizador autorizador;
    private final OffboardingProperties propiedades;
    private final Clock clock;

    public ServicioOffboarding(EmpresaRepository empresaRepository,
                               List<RecursoTenantOffboarding> recursos,
                               TenantSessionInitializer tenantSession,
                               AuditoriaPort auditoria,
                               Autorizador autorizador,
                               OffboardingProperties propiedades,
                               Clock clock) {
        this.empresaRepository = empresaRepository;
        this.recursos = recursos;
        this.tenantSession = tenantSession;
        this.auditoria = auditoria;
        this.autorizador = autorizador;
        this.propiedades = propiedades;
        this.clock = clock;
    }

    /**
     * Exporta los datos de negocio de una Empresa, estrictamente limitados a su
     * {@code tenant_id}, en una estructura procesable (Req 69.1).
     *
     * <p>Permitido al {@code super_admin} o al {@code admin_empresa} de la propia
     * Empresa. Para un {@code admin_empresa} (que carece del permiso de
     * plataforma {@code offboarding:exportar}), se exige que {@code tenantId}
     * coincida con el tenant del contexto autenticado; en caso contrario se
     * responde 404 para no revelar la existencia de otra Empresa (Req 23.3).</p>
     *
     * @param tenantId identificador de la Empresa a exportar; no {@code null}.
     * @return la exportacion estructurada por recurso (Req 69.1).
     * @throws ReglaNegocioException        si {@code tenantId} es {@code null}.
     * @throws RecursoNoEncontradoException si la Empresa no existe (o el
     *                                      admin_empresa intenta exportar otro tenant).
     */
    @Transactional(readOnly = true)
    public ExportacionTenantDto exportar(UUID tenantId) {
        exigirTenant(tenantId);
        String actor = actorActual();

        // Autorizacion de propietario (Req 69.1): el admin_empresa solo puede
        // exportar SU tenant. El super_admin (permiso de plataforma) opera
        // cross-tenant. Se comprueba antes de revelar la existencia de la Empresa.
        boolean esPlataforma = autorizador.tiene(RECURSO_PERMISO_PLATAFORMA, OPERACION_EXPORTAR);
        if (!esPlataforma) {
            UUID tenantDelContexto = TenantContext.getCurrent().orElse(null);
            if (tenantDelContexto == null || !tenantDelContexto.equals(tenantId)) {
                // No revelar existencia de recurso de otro tenant (Req 23.3).
                throw new RecursoNoEncontradoException("No se encontro la Empresa solicitada.");
            }
        }

        // La Empresa (tenant) debe existir. La entidad empresa NO es RLS-protegida.
        if (!empresaRepository.existsById(tenantId)) {
            throw new RecursoNoEncontradoException("No se encontro la Empresa solicitada.");
        }

        // Refuerzo RLS: fijar app.current_tenant al tenant objetivo (Req 69.5).
        tenantSession.applyTenant(tenantId);

        Map<String, Object> porRecurso = new LinkedHashMap<>();
        for (RecursoTenantOffboarding recurso : recursos) {
            Object datos = recurso.exportar(tenantId);
            porRecurso.put(recurso.nombreRecurso(), datos);
        }

        Instant ahora = Instant.now(clock);
        auditar(actor, "exportar", tenantId,
                "exportacion de datos por tenant_id=" + tenantId + "; recursos="
                        + porRecurso.keySet());
        return new ExportacionTenantDto(tenantId, ahora, porRecurso);
    }

    /**
     * Cancela una Empresa e inicia su Periodo_Gracia configurable (Req 69.2):
     * fija {@code estado=CANCELADA}, {@code fecha_cancelacion=ahora} y
     * {@code fin_periodo_gracia=ahora + periodoGracia}. Durante la gracia los
     * datos se conservan y el acceso queda restringido conforme al estado
     * (Req 24.4/69.2, aplicado por {@code ServicioAutenticacion}).
     *
     * <p>Operacion de plataforma (super_admin). Es idempotente sobre una Empresa
     * ya cancelada (no reinicia la gracia).</p>
     *
     * @param tenantId identificador de la Empresa a cancelar; no {@code null}.
     * @return la Empresa tras la cancelacion.
     * @throws ReglaNegocioException        si {@code tenantId} es {@code null}.
     * @throws RecursoNoEncontradoException si la Empresa no existe.
     */
    @Transactional
    public Empresa cancelarEIniciarGracia(UUID tenantId) {
        exigirTenant(tenantId);
        String actor = actorActual();
        Empresa empresa = cargar(tenantId);
        Duration gracia = propiedades.periodoGracia();
        Instant ahora = Instant.now(clock);
        empresa.cancelar(ahora, gracia, actor);
        Empresa guardada = empresaRepository.save(empresa);
        auditar(actor, "cancelar", tenantId,
                "cancelada empresa (tenant_id=" + tenantId + "); periodo_gracia=" + gracia
                        + "; fin_periodo_gracia=" + guardada.getFinPeriodoGracia());
        return guardada;
    }

    /**
     * Ejecuta la <strong>eliminacion definitiva</strong> de los datos de negocio
     * de una Empresa tras el Periodo_Gracia (Req 69.3), preservando los
     * comprobantes fiscales bajo retencion (Req 69.4) y afectando unicamente al
     * tenant objetivo (Req 69.5). Operacion exclusiva del {@code super_admin}
     * (control de acceso en el controlador).
     *
     * <p>Rechaza con {@link ReglaNegocioException} (HTTP 422) si la Empresa no
     * esta cancelada o si su Periodo_Gracia aun no ha expirado. La fila
     * {@code empresa} se conserva como lapida/ancla de auditoria (no se borra).</p>
     *
     * @param tenantId identificador de la Empresa objetivo; no {@code null}.
     * @return el resultado con el alcance eliminado y los recursos fiscales
     *         preservados (Req 69.6).
     * @throws ReglaNegocioException        si {@code tenantId} es {@code null},
     *                                      la Empresa no esta cancelada o la
     *                                      gracia no ha expirado.
     * @throws RecursoNoEncontradoException si la Empresa no existe.
     */
    @Transactional
    public ResultadoEliminacionTenantDto eliminarDefinitivamente(UUID tenantId) {
        exigirTenant(tenantId);
        String actor = actorActual();
        Empresa empresa = cargar(tenantId);

        if (!empresa.estaCancelada()) {
            throw new ReglaNegocioException(
                    "No se puede eliminar: la Empresa no esta cancelada.");
        }
        Instant ahora = Instant.now(clock);
        if (!empresa.periodoGraciaExpirado(ahora)) {
            throw new ReglaNegocioException(
                    "No se puede eliminar: el Periodo_Gracia aun no ha expirado.");
        }

        // Refuerzo RLS: toda eliminacion corre bajo el ambito del tenant objetivo.
        tenantSession.applyTenant(tenantId);

        Map<String, Long> eliminados = new LinkedHashMap<>();
        List<String> preservadosFiscales = new ArrayList<>();
        for (RecursoTenantOffboarding recurso : recursos) {
            if (recurso.esComprobanteFiscal()) {
                // Preservacion fiscal (Req 69.4): NO se elimina.
                preservadosFiscales.add(recurso.nombreRecurso());
                continue;
            }
            long afectados = recurso.eliminarOAnonimizar(tenantId);
            if (afectados > 0) {
                eliminados.put(recurso.nombreRecurso(), afectados);
            }
        }

        auditar(actor, "eliminar", tenantId,
                "eliminacion definitiva de datos por tenant_id=" + tenantId
                        + "; eliminados=" + eliminados
                        + "; preservados_fiscales=" + preservadosFiscales);
        return new ResultadoEliminacionTenantDto(tenantId, ahora, eliminados, preservadosFiscales);
    }

    /**
     * Lista las Empresas canceladas cuyo Periodo_Gracia ya expiro y que, por
     * tanto, son elegibles para la eliminacion definitiva (Req 69.3). Sirve de
     * apoyo operativo al {@code super_admin}; no ejecuta ninguna eliminacion.
     *
     * @param pageable parametros de paginacion (acotados a 20/100).
     * @return la pagina de Empresas elegibles.
     */
    @Transactional(readOnly = true)
    public Page<Empresa> listarGraciaExpirada(Pageable pageable) {
        return empresaRepository.findByEstadoAndFinPeriodoGraciaLessThanEqual(
                EstadoEmpresa.CANCELADA, Instant.now(clock), pageable);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Empresa cargar(UUID tenantId) {
        return empresaRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Empresa solicitada."));
    }

    private static void exigirTenant(UUID tenantId) {
        if (tenantId == null) {
            // Fail-closed: nunca operar sin un tenant objetivo explicito.
            throw new ReglaNegocioException("El identificador de la Empresa (tenant) es obligatorio.");
        }
    }

    private void auditar(String actor, String accion, UUID tenantId, String detalle) {
        // El offboarding lo dirige el super_admin (plataforma); se audita como
        // evento de plataforma referenciando la Empresa/tenant en el detalle,
        // incluyendo el alcance (Req 69.6). Nunca se incluyen secretos.
        auditoria.registrar(EventoAuditoria.dePlataforma(
                actor, accion, RECURSO_OFFBOARDING, detalle, null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
