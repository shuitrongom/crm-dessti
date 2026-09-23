package com.dessti.crm.platform.security.roles;

import java.util.Set;

import com.dessti.crm.platform.security.rbac.Permiso;

/**
 * Regla de dominio que distingue los <strong>permisos de nivel plataforma</strong>
 * de los <strong>permisos de nivel empresa</strong> (Req 27.7, 28.5).
 *
 * <p>Los permisos de plataforma corresponden a la administracion de las
 * Empresas (tenants) y su ciclo de vida, y estan reservados al rol
 * {@code super_admin} (Req 24.3): la creacion/consulta/estado de
 * {@code Empresa}, la gestion de {@code Plan} y {@code Suscripcion}, y el
 * {@code offboarding} del tenant. NINGUN rol de empresa —predefinido o
 * {@code Rol_Personalizado}— puede incluir estos permisos, pues operarian
 * fuera del {@code tenant_id} de la Empresa.</p>
 *
 * <p>Se implementa como una lista de recursos <em>reservados</em> (allow/deny
 * por recurso) en lugar de por permiso individual: la frontera plataforma vs
 * empresa se traza de forma natural a nivel de recurso, y asi cualquier
 * operacion futura sobre un recurso de plataforma queda automaticamente
 * clasificada como de plataforma.</p>
 *
 * <p>Clase de utilidad, no instanciable.</p>
 */
public final class ClasificadorRecursosPlataforma {

    /**
     * Recursos cuyo ambito es la administracion de la plataforma (super_admin),
     * no una Empresa concreta. Deben coincidir con los recursos sembrados para
     * {@code super_admin} en la migracion V5.
     */
    private static final Set<String> RECURSOS_PLATAFORMA = Set.of(
            "empresa",
            "plan",
            "suscripcion",
            "offboarding",
            // Respaldo/recuperacion de datos (Req 50): operacion de plataforma
            // reservada a super_admin; ningun rol de empresa puede incluirla.
            "respaldo",
            // Catalogo de Giros (Req 1): administracion de los verticales de
            // negocio reservada a super_admin (permisos giro:* sembrados en V50);
            // ningun rol de empresa puede incluirla.
            "giro");

    private ClasificadorRecursosPlataforma() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Indica si un recurso pertenece al ambito de plataforma.
     *
     * @param recurso nombre del recurso (se compara normalizado a minusculas).
     * @return {@code true} si es un recurso de plataforma reservado.
     */
    public static boolean esRecursoPlataforma(String recurso) {
        if (recurso == null || recurso.isBlank()) {
            return false;
        }
        return RECURSOS_PLATAFORMA.contains(recurso.strip().toLowerCase());
    }

    /**
     * Indica si el permiso corresponde a una operacion de nivel plataforma.
     *
     * @param permiso permiso a clasificar.
     * @return {@code true} si es un permiso de plataforma (prohibido en roles de
     *         empresa, Req 28.5).
     */
    public static boolean esPermisoPlataforma(Permiso permiso) {
        return permiso != null && esRecursoPlataforma(permiso.recurso());
    }

    /**
     * Indica si el permiso (fila del catalogo) corresponde a nivel plataforma.
     *
     * @param permiso fila del catalogo a clasificar.
     * @return {@code true} si es un permiso de plataforma.
     */
    public static boolean esPermisoPlataforma(PermisoEntity permiso) {
        return permiso != null && esRecursoPlataforma(permiso.getRecurso());
    }
}
