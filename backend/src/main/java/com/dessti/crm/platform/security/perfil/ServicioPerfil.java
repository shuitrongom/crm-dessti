package com.dessti.crm.platform.security.perfil;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.UsuarioAutenticado;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.security.usuarios.Usuario;
import com.dessti.crm.platform.security.usuarios.UsuarioRepository;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Servicio de aplicacion del <strong>perfil propio</strong> del Usuario
 * autenticado (CHANGE 2): consulta de sus datos de cuenta y cambio de su propia
 * contrasena. Funciona tanto para el {@code super_admin} de plataforma
 * ({@code tenant_id} NULL) como para cualquier Usuario de una Empresa
 * ({@code admin_empresa}, etc.).
 *
 * <h2>Resolucion de la identidad</h2>
 * <p>La cuenta se resuelve a partir del {@code principal} del contexto de
 * seguridad, que es un {@link UsuarioAutenticado} cuyo {@code id} es el UUID del
 * Usuario (el {@code sub} del Token_Acceso). Nunca se confia en un id enviado
 * por la peticion.</p>
 *
 * <h2>RLS (Req 23) en el cambio de contrasena</h2>
 * <p>La tabla {@code usuario} tiene politicas RLS estrictas para UPDATE
 * (V48/V53): una fila tenant-scoped solo puede modificarse cuando
 * {@code app.current_tenant} coincide con su {@code tenant_id}. Ademas, la
 * politica {@code usuario_login_mutacion} permite el UPDATE cuando NO hay tenant
 * fijado (ventana de login / plataforma). Por ello:</p>
 * <ul>
 *   <li><strong>Usuario de Empresa</strong> ({@code tenant_id} no nulo): se fija
 *       {@code app.current_tenant = tenant_id} con {@link TenantSessionInitializer#applyTenant(UUID)}
 *       antes del UPDATE, satisfaciendo la politica tenant-scoped.</li>
 *   <li><strong>super_admin</strong> ({@code tenant_id} NULL): NO se fija ningun
 *       tenant; la politica {@code usuario_login_mutacion} permite el UPDATE de
 *       su fila (tenant NULL) sin tenant en la sesion.</li>
 * </ul>
 *
 * <h2>Secretos (Req 11.3)</h2>
 * <p>La contrasena actual y la nueva se manejan solo el tiempo imprescindible
 * para verificar/cifrar; nunca se registran en logs ni se incluyen en la
 * auditoria. La verificacion usa {@link PasswordEncoder#matches}.</p>
 */
@Service
public class ServicioPerfil {

    /** Recurso de auditoria asociado al perfil/cuenta de acceso. */
    static final String RECURSO_USUARIO = "usuario";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaPort auditoria;
    private final TenantSessionInitializer tenantSession;

    public ServicioPerfil(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          AuditoriaPort auditoria,
                          TenantSessionInitializer tenantSession) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.tenantSession = tenantSession;
    }

    /**
     * Devuelve el perfil del Usuario autenticado (CHANGE 2). Operacion de solo
     * lectura, sin permiso especial: cualquier Usuario autenticado puede
     * consultar sus propios datos.
     *
     * @return el DTO de perfil del Usuario actual.
     * @throws RecursoNoEncontradoException si el Usuario del contexto ya no existe.
     */
    @Transactional(readOnly = true)
    public PerfilDto consultarPerfil() {
        Usuario usuario = cargarUsuarioActual();
        return PerfilDto.de(usuario);
    }

    /**
     * Cambia la contrasena del propio Usuario autenticado (CHANGE 2). Verifica la
     * contrasena actual contra el hash almacenado y, si coincide, cifra y
     * persiste la nueva. No fuerza el cierre de la sesion en curso.
     *
     * @param passwordActual contrasena actual en claro (se compara con el hash).
     * @param passwordNueva  nueva contrasena en claro (ya validada por longitud
     *                       en la capa web).
     * @throws RecursoNoEncontradoException si el Usuario del contexto ya no existe.
     * @throws ReglaNegocioException        si la contrasena actual no es correcta
     *                                      (422) o la nueva es invalida.
     */
    @Transactional
    public void cambiarPasswordPropia(String passwordActual, String passwordNueva) {
        if (passwordNueva == null || passwordNueva.isBlank()) {
            throw new ReglaNegocioException("La contrasena nueva es obligatoria.");
        }
        Usuario usuario = cargarUsuarioActual();

        // Verificacion de la contrasena actual (Req 11.3): un valor incorrecto se
        // rechaza con 422 sin revelar detalle del hash.
        if (passwordActual == null
                || !passwordEncoder.matches(passwordActual, usuario.getHashPassword())) {
            throw new ReglaNegocioException("La contrasena actual no es correcta.");
        }

        // RLS (Req 23): un Usuario de Empresa exige fijar su tenant antes del
        // UPDATE; el super_admin (tenant NULL) se actualiza sin tenant fijado
        // (politica usuario_login_mutacion).
        if (usuario.getTenantId() != null) {
            tenantSession.applyTenant(usuario.getTenantId());
        }

        String actor = usuario.getIdentificadorAcceso();
        usuario.cambiarPassword(passwordEncoder.encode(passwordNueva), actor);
        usuarioRepository.save(usuario);

        auditar(usuario.getTenantId(), actor,
                "cambiar_password_propia",
                "el usuario '" + actor + "' cambio su propia contrasena");
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Resuelve la cuenta del Usuario autenticado a partir del principal del
     * contexto de seguridad ({@link UsuarioAutenticado#id()} = UUID del Usuario).
     * Nunca confia en un id de la peticion (Req 23.4).
     */
    private Usuario cargarUsuarioActual() {
        UUID usuarioId = idUsuarioActual();
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el usuario autenticado."));
    }

    private UUID idUsuarioActual() {
        Authentication authentication = AutenticacionActual.obtener()
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay un usuario autenticado."));
        Object principal = authentication.getPrincipal();
        if (principal instanceof UsuarioAutenticado autenticado) {
            return parsearId(autenticado.id());
        }
        // Otros tipos de principal (p. ej. pruebas con user(...)) exponen el
        // nombre como identidad; se intenta interpretar como UUID.
        return parsearId(authentication.getName());
    }

    private static UUID parsearId(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new RecursoNoEncontradoException("No se encontro el usuario autenticado.");
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException ex) {
            throw new RecursoNoEncontradoException("No se encontro el usuario autenticado.");
        }
    }

    private void auditar(UUID tenantId, String actor, String accion, String detalle) {
        EventoAuditoria evento = (tenantId != null)
                ? EventoAuditoria.deTenant(tenantId, actor, accion, RECURSO_USUARIO, detalle, null, null)
                : EventoAuditoria.dePlataforma(actor, accion, RECURSO_USUARIO, detalle, null, null);
        auditoria.registrar(evento);
    }
}
