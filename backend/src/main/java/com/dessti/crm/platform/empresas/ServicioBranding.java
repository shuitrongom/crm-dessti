package com.dessti.crm.platform.empresas;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna la personalizacion de marca (branding) de
 * una Empresa por parte de su {@code admin_empresa} (Req 26).
 *
 * <h2>Alcance de empresa (Req 26.1, 26.2, 23.4)</h2>
 * <p>A diferencia de {@link ServicioEmpresas} (operaciones de plataforma del
 * {@code super_admin}), este servicio opera <strong>dentro de la propia
 * Empresa</strong> del Usuario autenticado. El tenant a personalizar se deriva
 * SIEMPRE del contexto autenticado ({@link TenantContext#require()}), nunca de
 * datos de la peticion: un {@code admin_empresa} solo puede modificar y
 * consultar el branding de su propia Empresa. La Empresa <em>es</em> el tenant,
 * por lo que se carga por {@code id == tenantId} mediante
 * {@link EmpresaRepository#findById(Object)} (no esta sujeta al filtro de
 * tenant de Hibernate).</p>
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>actualizarBranding (Req 26.1, 6.3):</strong> carga la Empresa
 *       del contexto, aplica el nombre visible, el logotipo y el color de marca,
 *       persiste y audita (incluido el cambio de color).</li>
 *   <li><strong>consultarBranding (Req 26.2):</strong> devuelve el nombre
 *       visible y el logotipo vigentes para que la interfaz aplique la marca de
 *       la Empresa del Usuario.</li>
 * </ul>
 *
 * <h2>Auditoria (Req 26.3)</h2>
 * <p>Cada modificacion de branding se registra via {@link AuditoriaPort} como
 * evento de <em>empresa</em> ({@link EventoAuditoria#deTenant}) con el actor, la
 * accion {@code actualizar}, el recurso {@code branding} y la marca temporal en
 * UTC. El detalle NO incluye el logotipo completo (podria ser un {@code data
 * URI} extenso): solo se registra el cambio del nombre visible, si el logotipo
 * se establecio o se elimino y el cambio del color de marca (valor anterior ->
 * nuevo, Req 6.5, 6.6).</p>
 *
 * <p><strong>Resumen legible en {@code detalle} (columna TEXT), no en JSONB:</strong>
 * los campos {@code valorAnterior}/{@code valorNuevo} del evento se persisten
 * como <strong>JSONB</strong> (V3) y solo admiten JSON valido o {@code null};
 * volcar en ellos texto plano provocaba un error de sintaxis JSON en PostgreSQL
 * (SQLState 22P02) y, en consecuencia, un HTTP 500 al actualizar el branding.
 * Por eso este servicio pasa {@code null} en ambos y conserva el resumen humano
 * (nombre visible anterior/nuevo y estado del logotipo) en {@code detalle}, que
 * es una columna de TEXTO libre. Es el MISMO patron que siguen los demas
 * servicios de este dominio (p. ej. {@code ServicioUsuarios} y
 * {@code ServicioRoles}), que auditan con {@code null, null} y el texto legible
 * en {@code detalle}.</p>
 */
@Service
public class ServicioBranding {

    /** Recurso de auditoria/RBAC asociado a la personalizacion de marca. */
    static final String RECURSO_BRANDING = "branding";

    private final EmpresaRepository empresaRepository;
    private final AuditoriaPort auditoria;

    public ServicioBranding(EmpresaRepository empresaRepository, AuditoriaPort auditoria) {
        this.empresaRepository = empresaRepository;
        this.auditoria = auditoria;
    }

    /**
     * Actualiza la personalizacion de marca de la Empresa del Usuario
     * autenticado (Req 26.1, 6.3) y registra la auditoria (Req 26.3, 6.5).
     *
     * @param comando nombre visible, logotipo y color de marca a aplicar (todos
     *                opcionales; {@code null}/blanco limpia el dato).
     * @return el branding resultante (nombre visible + logotipo + color).
     * @throws RecursoNoEncontradoException si la Empresa del contexto no existe.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si el nombre
     *         visible o el logotipo exceden su longitud maxima, o si el color de
     *         marca no cumple el formato {@code #RRGGBB} (HTTP 422).
     */
    @Transactional
    public BrandingDto actualizarBranding(ActualizarBrandingCommand comando) {
        UUID tenantId = TenantContext.require();
        String actor = actorActual();
        Empresa empresa = cargar(tenantId);

        String nombreAnterior = empresa.getBrandingNombreVisible();
        boolean teniaLogo = empresa.getBrandingLogo() != null;
        // Se captura el color previo ANTES de mutar para poder auditar el cambio
        // (valor anterior -> nuevo) de forma legible (Req 6.5, 6.6).
        String colorAnterior = empresa.getBrandingColorPrimario();

        // El tenant proviene SIEMPRE de TenantContext.require() (arriba), nunca del
        // comando de la peticion: un admin_empresa solo personaliza su propia
        // Empresa (Req 6.6). El color primario del comando se propaga al dominio,
        // que valida su formato (^#[0-9a-fA-F]{6}$) en Empresa.actualizarBranding
        // via normalizarBrandingColorPrimario lanzando ReglaNegocioException (422).
        // Esa validacion de formato es la unica fuente de verdad (defensa en
        // profundidad, Req 6.4/6.6): no se duplica aqui para evitar divergencias.
        empresa.actualizarBranding(comando.nombreVisible(), comando.logo(), comando.colorPrimario(), actor);
        Empresa guardada = empresaRepository.save(empresa);

        auditar(tenantId, actor, nombreAnterior, teniaLogo, colorAnterior, guardada);
        return BrandingDto.de(guardada);
    }

    /**
     * Consulta la personalizacion de marca vigente de la Empresa del Usuario
     * autenticado, para que la interfaz la aplique (Req 26.2).
     *
     * @return el branding actual (nombre visible + logotipo); los campos pueden
     *         ser {@code null} si no se ha personalizado.
     * @throws RecursoNoEncontradoException si la Empresa del contexto no existe.
     */
    @Transactional(readOnly = true)
    public BrandingDto consultarBranding() {
        UUID tenantId = TenantContext.require();
        return BrandingDto.de(cargar(tenantId));
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Empresa cargar(UUID tenantId) {
        return empresaRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Empresa del contexto actual."));
    }

    /**
     * Registra la modificacion de branding sin volcar el logotipo completo en el
     * detalle (Req 26.3, 10.10): para el nombre visible se anota el valor previo
     * y el nuevo; para el logotipo solo se anota si quedo establecido o eliminado;
     * y para el color de marca se anota el valor anterior y el nuevo (Req 6.5, 6.6).
     */
    private void auditar(UUID tenantId, String actor, String nombreAnterior,
                         boolean teniaLogo, String colorAnterior, Empresa empresa) {
        boolean tieneLogo = empresa.getBrandingLogo() != null;
        String estadoLogo;
        if (tieneLogo && !teniaLogo) {
            estadoLogo = "logo establecido";
        } else if (!tieneLogo && teniaLogo) {
            estadoLogo = "logo eliminado";
        } else if (tieneLogo) {
            estadoLogo = "logo actualizado";
        } else {
            estadoLogo = "sin logo";
        }
        String detalle = "branding actualizado (tenant_id=" + tenantId + "); nombre visible '"
                + descripcion(nombreAnterior) + "' -> '" + descripcion(empresa.getBrandingNombreVisible())
                + "'; " + estadoLogo
                + "; color '" + descripcion(colorAnterior) + "' -> '"
                + descripcion(empresa.getBrandingColorPrimario()) + "'";
        // valorAnterior/valorNuevo se persisten como JSONB (V3): solo admiten
        // JSON valido o null. El resumen legible (nombre visible previo/nuevo y
        // estado del logotipo) va en 'detalle' (columna TEXT); en los campos
        // JSONB se pasa null, null, mismo patron que ServicioUsuarios/ServicioRoles.
        // Pasar texto plano aqui producia el error 22P02 de PostgreSQL -> HTTP 500.
        auditoria.registrar(EventoAuditoria.deTenant(
                tenantId, actor, "actualizar", RECURSO_BRANDING, detalle, null, null));
    }

    private static String descripcion(String valor) {
        return (valor == null) ? "(sin definir)" : valor;
    }

    /**
     * Resuelve el identificador del actor autenticado ({@code admin_empresa})
     * para la auditoria; si no hay contexto de seguridad, usa "sistema".
     */
    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
