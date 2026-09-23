package com.dessti.crm.platform.security.rbac;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.vertical.RegistroVerticales;

/**
 * Componente central de autorizacion RBAC con <strong>denegacion por
 * defecto</strong> (Req 3), pensado para usarse desde expresiones SpEL de
 * {@code @PreAuthorize} bajo el nombre de bean {@code autorizador}.
 *
 * <p>Ejemplos de uso en un controlador o servicio de aplicacion:</p>
 * <pre>{@code
 * @PreAuthorize("@autorizador.tiene('cliente', 'crear')")
 * public ClienteDto crear(CrearClienteCommand cmd) { ... }
 *
 * @PreAuthorize("@autorizador.moduloHabilitado('facturacion') "
 *             + "and @autorizador.tiene('factura', 'timbrar')")
 * public void timbrar(UUID facturaId) { ... }
 * }</pre>
 *
 * <h2>Denegacion por defecto</h2>
 * <p>Un metodo sin anotacion de permiso queda denegado por la configuracion de
 * seguridad de metodo ({@code @EnableMethodSecurity}) cuando se combina con la
 * politica de la cadena de filtros de la tarea 9.1. A nivel de este evaluador,
 * cualquier ausencia (sin autenticacion, sin authorities, o sin el permiso
 * requerido) devuelve {@code false}, lo que Spring Security traduce a
 * {@code AccessDeniedException} y el manejador global mapea a HTTP 403
 * (Req 3.2, 3.5, 3.6).</p>
 *
 * <h2>Evaluacion dentro del tenant (Req 23.5)</h2>
 * <p>Los permisos del Usuario provienen de las {@code authorities} del
 * {@link Authentication}, que el filtro JWT de la tarea 9.1 puebla a partir del
 * token cuyo {@code tenant_id} ya fijo el {@code TenantContext}. Por tanto la
 * evaluacion es intrinsecamente <em>intra-tenant</em>: solo se consideran los
 * permisos emitidos para la Empresa del Usuario y este evaluador nunca autoriza
 * cruzar de tenant. El metodo {@link #mismoTenant(UUID)} permite reforzar
 * explicitamente esa comprobacion cuando un recurso ya cargado expone su
 * {@code tenant_id}.</p>
 *
 * <p><strong>Punto de union:</strong> este evaluador asume que las
 * {@code authorities} contienen los permisos atomicos con formato
 * {@code recurso:operacion} (tarea 9.1 los emite en el JWT; la tarea 10.2
 * define los roles predefinidos/personalizados que los agrupan).</p>
 */
@Component("autorizador")
public class Autorizador {

    private static final Logger log = LoggerFactory.getLogger(Autorizador.class);

    /** Accion de auditoria para una denegacion por gating de Giro (Req 6.5). */
    private static final String ACCION_DENEGAR_GIRO = "denegar_giro";

    private final PlanModulosPort planModulos;
    private final RegistroVerticales registroVerticales;
    private final GiroEmpresaPort giroEmpresa;
    private final AuditoriaPort auditoria;

    /**
     * Construye el evaluador con las dependencias del <strong>doble gating</strong>
     * (Plan Y Giro) y la auditoria de denegaciones por Giro.
     *
     * @param planModulos        puerto del gating por Plan (habilitacion de modulos).
     * @param registroVerticales registro que resuelve a que Giro pertenece un modulo
     *                           (o vacio si es Nucleo, Req 6.4).
     * @param giroEmpresa        puerto que resuelve la clave de Giro del tenant actual
     *                           (Req 6.1, 8.1, 8.2).
     * @param auditoria          puerto de auditoria para registrar denegaciones por
     *                           Giro (Req 6.5).
     */
    public Autorizador(
            PlanModulosPort planModulos,
            RegistroVerticales registroVerticales,
            GiroEmpresaPort giroEmpresa,
            AuditoriaPort auditoria) {
        this.planModulos = planModulos;
        this.registroVerticales = registroVerticales;
        this.giroEmpresa = giroEmpresa;
        this.auditoria = auditoria;
    }

    /**
     * Comprueba si el Usuario autenticado posee el permiso atomico indicado.
     *
     * @param recurso   recurso protegido (p. ej. {@code cliente}).
     * @param operacion operacion sobre el recurso (p. ej. {@code crear}).
     * @return {@code true} si alguna authority del Usuario coincide con el
     *         permiso; {@code false} en caso de ausencia (denegacion por defecto).
     */
    public boolean tiene(String recurso, String operacion) {
        Permiso requerido;
        try {
            requerido = Permiso.de(recurso, operacion);
        } catch (IllegalArgumentException ex) {
            // Un permiso mal declarado en el codigo se trata como denegacion.
            return false;
        }
        return tiene(requerido);
    }

    /**
     * Comprueba si el Usuario autenticado posee el {@link Permiso} indicado.
     *
     * @param requerido permiso atomico requerido.
     * @return {@code true} si el Usuario lo posee; {@code false} en caso contrario.
     */
    public boolean tiene(Permiso requerido) {
        if (requerido == null) {
            return false;
        }
        Optional<Authentication> auth = autenticacionValida();
        if (auth.isEmpty()) {
            return false;
        }
        String authorityRequerida = requerido.authority();
        for (GrantedAuthority ga : auth.get().getAuthorities()) {
            if (authorityRequerida.equalsIgnoreCase(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * <em>Gating</em> por Plan: indica si el modulo esta habilitado para la
     * Empresa del Usuario (Req 25.4).
     *
     * <p>Requiere un Usuario autenticado y un {@code tenant_id} en el contexto;
     * en su ausencia deniega (denegacion por defecto). Delega la decision en
     * {@link PlanModulosPort} (implementacion definitiva en la tarea 14.2).</p>
     *
     * @param modulo nombre canonico del modulo (p. ej. {@code facturacion}).
     * @return {@code true} si el modulo esta habilitado; {@code false} si no,
     *         lo que deriva en 403.
     */
    public boolean moduloHabilitado(String modulo) {
        if (modulo == null || modulo.isBlank()) {
            return false;
        }
        if (autenticacionValida().isEmpty()) {
            return false;
        }
        Optional<UUID> tenant = TenantContext.getCurrent();
        if (tenant.isEmpty()) {
            return false;
        }
        return planModulos.moduloHabilitado(tenant.get(), modulo.strip().toLowerCase());
    }

    /**
     * <em>Gating</em> por Giro: indica si el modulo indicado corresponde al Giro
     * de la Empresa autenticada, componiendo la segunda mitad del
     * <strong>doble gating</strong> (Plan Y Giro, Req 6).
     *
     * <p>La decision sigue estas reglas:</p>
     * <ol>
     *   <li>Se resuelve el Giro del modulo via
     *       {@link RegistroVerticales#giroDeModulo(String)}.</li>
     *   <li>Si el modulo es del <strong>Nucleo Comun</strong> (sin Giro
     *       asociado), devuelve {@code true}: el Nucleo es transversal a todo
     *       Giro y no queda sujeto al gating por Giro (Req 6.4).</li>
     *   <li>Si el modulo pertenece a un <strong>vertical</strong>, exige un
     *       Usuario autenticado y un {@code tenant_id} en el contexto; en su
     *       ausencia deniega (denegacion por defecto). Compara la clave del Giro
     *       del modulo con la clave del Giro del tenant resuelta por
     *       {@link GiroEmpresaPort#giroDeTenant(UUID)}: coinciden (comparacion
     *       insensible a mayusculas/espacios) => {@code true}; en caso contrario
     *       => {@code false} (Req 6.1, 6.2, 6.3).</li>
     * </ol>
     *
     * <p>Cuando <strong>deniega</strong> por Giro (modulo de vertical ajeno al
     * Giro de la Empresa) registra el intento en la bitacora de auditoria
     * (Req 6.5). No audita cuando permite ni cuando el modulo es de Nucleo, para
     * evitar ruido. Un fallo de la auditoria no altera la decision de
     * autorizacion: se registra en el log y la denegacion prevalece
     * (comportamiento deny-safe).</p>
     *
     * <p>Uso previsto en los controladores de un vertical, compuesto por AND con
     * el gating por Plan y el permiso atomico RBAC:</p>
     * <pre>{@code
     * @PreAuthorize("@autorizador.moduloHabilitado('anuncios') "
     *             + "and @autorizador.giroCorresponde('anuncios') "
     *             + "and @autorizador.tiene('prueba_diseno','crear')")
     * }</pre>
     *
     * @param modulo clave del modulo cuyo Giro se evalua (p. ej. {@code anuncios}).
     * @return {@code true} si el modulo es de Nucleo o su Giro coincide con el de
     *         la Empresa; {@code false} si es de un vertical ajeno o falta
     *         autenticacion/tenant, lo que deriva en 403.
     */
    public boolean giroCorresponde(String modulo) {
        Optional<String> giroModulo = registroVerticales.giroDeModulo(modulo);
        if (giroModulo.isEmpty()) {
            // Modulo del Nucleo Comun: transversal a todo Giro (Req 6.4).
            return true;
        }

        if (autenticacionValida().isEmpty()) {
            return false;
        }
        Optional<UUID> tenant = TenantContext.getCurrent();
        if (tenant.isEmpty()) {
            return false;
        }

        String giroRequerido = normalizarClave(giroModulo.get());
        String giroEmpresaActual = giroEmpresa.giroDeTenant(tenant.get())
                .map(Autorizador::normalizarClave)
                .orElse(null);

        if (giroRequerido != null && giroRequerido.equals(giroEmpresaActual)) {
            return true;
        }

        auditarDenegacionPorGiro(tenant.get(), modulo, giroRequerido, giroEmpresaActual);
        return false;
    }

    /**
     * Refuerza la evaluacion intra-tenant (Req 23.5): comprueba que el
     * {@code tenant_id} de un recurso ya cargado coincide con el del contexto
     * autenticado. Util en expresiones que reciben el tenant del recurso.
     *
     * @param tenantIdRecurso {@code tenant_id} del recurso al que se accede.
     * @return {@code true} solo si coincide con el tenant del contexto.
     */
    public boolean mismoTenant(UUID tenantIdRecurso) {
        if (tenantIdRecurso == null) {
            return false;
        }
        return TenantContext.getCurrent()
                .map(actual -> actual.equals(tenantIdRecurso))
                .orElse(false);
    }

    /**
     * Devuelve el {@link Authentication} vigente si representa a un principal
     * autenticado real (no anonimo).
     */
    private Optional<Authentication> autenticacionValida() {
        return AutenticacionActual.obtener();
    }

    /**
     * Registra en la bitacora un intento denegado por gating de Giro (Req 6.5).
     * El evento es de ambito tenant (siempre hay {@code tenant_id} cuando se
     * llega a la comparacion de Giros). Tolera fallos de auditoria sin romper la
     * autorizacion: la denegacion prevalece y el error se deja en el log.
     *
     * @param tenantId          tenant de la Empresa que intento la operacion.
     * @param modulo            modulo (recurso) al que se intento acceder.
     * @param giroRequerido     clave de Giro que exige el modulo del vertical.
     * @param giroEmpresaActual clave de Giro de la Empresa (o {@code null} si no
     *                          se resolvio).
     */
    private void auditarDenegacionPorGiro(
            UUID tenantId, String modulo, String giroRequerido, String giroEmpresaActual) {
        try {
            String detalle = "Denegacion por Giro: la Empresa (giro='"
                    + (giroEmpresaActual == null ? "desconocido" : giroEmpresaActual)
                    + "') intento operar el modulo '" + modulo
                    + "' que requiere el giro '"
                    + (giroRequerido == null ? "desconocido" : giroRequerido) + "'.";
            auditoria.registrar(EventoAuditoria.deTenant(
                    tenantId, actorActual(), ACCION_DENEGAR_GIRO, modulo, detalle, null, null));
        } catch (RuntimeException ex) {
            log.warn("No se pudo auditar la denegacion por Giro del modulo '{}' (tenant {}): {}",
                    modulo, tenantId, ex.getMessage(), ex);
        }
    }

    /**
     * Resuelve el identificador del actor autenticado para la auditoria; si no
     * hay contexto de seguridad, usa "sistema", igual que {@code ServicioGiros},
     * {@code ServicioEmpresas} y {@code ServicioRoles}.
     */
    private String actorActual() {
        return autenticacionValida()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }

    /**
     * Normaliza una clave de Giro a minusculas y sin espacios extremos, de forma
     * consistente con {@link RegistroVerticales} y el catalogo de Giros.
     *
     * @param valor clave cruda; puede ser nula.
     * @return la clave normalizada, o {@code null} si es nula o queda en blanco.
     */
    private static String normalizarClave(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        return normalizado.isEmpty() ? null : normalizado;
    }
}
