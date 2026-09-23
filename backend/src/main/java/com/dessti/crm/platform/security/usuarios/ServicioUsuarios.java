package com.dessti.crm.platform.security.usuarios;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort;
import com.dessti.crm.platform.security.roles.Rol;
import com.dessti.crm.platform.security.roles.RolModuloCatalogo;
import com.dessti.crm.platform.security.roles.RolRepository;
import com.dessti.crm.platform.security.sesiones.MotivoRevocacion;
import com.dessti.crm.platform.security.sesiones.RegistroSesionesPort;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna la gestion de Usuarios y su asignacion de
 * Roles dentro de una Empresa (Req 4).
 *
 * <h2>Reglas de negocio implementadas</h2>
 * <ul>
 *   <li><strong>Creacion (Req 4.1):</strong> crea una cuenta activa dentro del
 *       {@code tenant_id} del contexto autenticado (nunca de la peticion,
 *       Req 23.4), cifra la contrasena con {@link PasswordEncoder} (BCrypt) y
 *       asigna al menos un Rol valido.</li>
 *   <li><strong>Limite de Usuarios del Plan (Req 25.3):</strong> antes de crear
 *       una cuenta se consulta {@link LimiteUsuariosPort}; si la Empresa alcanzo
 *       el {@code max_usuarios} de su Plan vigente, se rechaza la creacion con
 *       {@link ReglaNegocioException} (HTTP 422) informando el limite alcanzado.
 *       El puerto desacopla esta comprobacion de la persistencia de plataforma
 *       (Planes/Suscripciones), preservando la frontera hexagonal.</li>
 *   <li><strong>Conflicto de identificador duplicado (Req 4.4):</strong> el
 *       {@code identificador_acceso} es UNICO GLOBAL (V1). El conflicto se
 *       traduce a {@link ConflictoUnicidadException} (HTTP 409), tanto por
 *       comprobacion previa ({@code existsByIdentificadorAcceso}) como por
 *       violacion del indice unico {@code uq_usuario_identificador_acceso} de la
 *       BD ante una carrera concurrente.</li>
 *   <li><strong>Desactivacion (Req 4.2, 68.2):</strong> marca
 *       {@code activo = false} dentro de la Empresa; el login ya rechaza las
 *       cuentas inactivas y, ademas, se <em>revocan las Sesiones vigentes</em>
 *       de la cuenta via {@link RegistroSesionesPort} para cortar el acceso
 *       (tras la expiracion del Token_Acceso vigente, &le; 15 min). Una cuenta
 *       de otra Empresa devuelve 404 (Req 23.3).</li>
 *   <li><strong>Asignacion de Roles (Req 4.3):</strong> reemplaza el conjunto de
 *       Roles por Roles <em>existentes y asignables</em> por la Empresa: los
 *       roles predefinidos de nivel empresa (tenant_id NULL) o los roles
 *       personalizados de la propia Empresa. Rechaza los roles de nivel
 *       plataforma ({@code super_admin}) con {@link ReglaNegocioException}
 *       (Req 27.7, 27.16) y los roles inexistentes o de otra Empresa (Req 23).
 *       Los Permisos del nuevo Rol aplican en la <em>siguiente</em> evaluacion
 *       de autorizacion (Req 4.3): las authorities se leen del JWT en cada
 *       peticion y se reemiten al refrescar el token, por lo que el cambio surte
 *       efecto al emitirse/refrescarse el proximo token. Para cortar de
 *       inmediato las sesiones vigentes existe la operacion
 *       {@link #revocarSesiones(java.util.UUID)} (Req 68.2).</li>
 *   <li><strong>Auditoria (Req 4.5):</strong> cada creacion, desactivacion y
 *       asignacion se registra via {@link AuditoriaPort} con actor, accion y
 *       recurso, sin incluir la contrasena ni su hash (Req 10.10, 11.3).</li>
 * </ul>
 */
@Service
public class ServicioUsuarios {

    /** Recurso de auditoria/RBAC asociado a la gestion de cuentas de acceso. */
    static final String RECURSO_USUARIO = "usuario";

    /**
     * Nombres de Roles de nivel plataforma que NUNCA pueden asignarse a un
     * Usuario de empresa (Req 27.7, 27.16). Coincide con el rol sembrado en V5.
     */
    private static final Set<String> ROLES_PLATAFORMA = Set.of("super_admin");

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaPort auditoria;
    private final RegistroSesionesPort registroSesiones;
    private final LimiteUsuariosPort limiteUsuarios;
    private final ModulosHabilitadosPort modulosHabilitados;

    public ServicioUsuarios(UsuarioRepository usuarioRepository,
                            RolRepository rolRepository,
                            PasswordEncoder passwordEncoder,
                            AuditoriaPort auditoria,
                            RegistroSesionesPort registroSesiones,
                            LimiteUsuariosPort limiteUsuarios,
                            ModulosHabilitadosPort modulosHabilitados) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.registroSesiones = registroSesiones;
        this.limiteUsuarios = limiteUsuarios;
        this.modulosHabilitados = modulosHabilitados;
    }

    /**
     * Crea una cuenta de Usuario activa con al menos un Rol (Req 4.1).
     *
     * @param comando datos de la cuenta (identificador, contrasena y roles).
     * @return el DTO de la cuenta creada (sin hash de contrasena).
     * @throws ConflictoUnicidadException si el identificador ya existe (Req 4.4).
     * @throws ReglaNegocioException      si falta algun dato, no se indica ningun
     *                                    Rol o algun Rol no es asignable (Req 27.7).
     * @throws RecursoNoEncontradoException si algun Rol no existe/es de otra
     *                                    Empresa (Req 23).
     */
    @Transactional
    public UsuarioDto crearUsuario(CrearUsuarioCommand comando) {
        UUID tenantId = TenantContext.require();
        String actor = actorActual();
        String identificador = normalizarIdentificador(comando.identificadorAcceso());
        String password = comando.password();
        if (password == null || password.isBlank()) {
            throw new ReglaNegocioException("La contrasena es obligatoria.");
        }

        Set<Rol> roles = resolverRolesAsignables(comando.rolIds(), tenantId);

        // Limite de Usuarios del Plan (Req 25.3): si la Empresa alcanzo el
        // maximo de su Plan, se rechaza la creacion (422) informando el limite.
        if (!limiteUsuarios.puedeCrearUsuario(tenantId)) {
            throw new ReglaNegocioException(
                    "Se alcanzo el limite de Usuarios del Plan de la Empresa; "
                            + "no es posible crear mas Usuarios.");
        }

        // Conflicto de identificador duplicado: comprobacion previa (Req 4.4).
        if (usuarioRepository.existsByIdentificadorAcceso(identificador)) {
            throw new ConflictoUnicidadException(
                    "Ya existe un usuario con el identificador de acceso '" + identificador + "'.");
        }

        String hash = passwordEncoder.encode(password);
        Usuario usuario = Usuario.crear(tenantId, identificador, hash,
                comando.nombreVisible(), roles, actor);
        Usuario guardado = guardarTraduciendoUnicidad(usuario, identificador);

        auditar(tenantId, actor, "crear",
                "creado usuario '" + identificador + "' con " + roles.size() + " rol(es)");
        return UsuarioDto.de(guardado);
    }

    /**
     * Desactiva una cuenta de Usuario para impedir su inicio de sesion (Req 4.2).
     *
     * @param usuarioId identificador de la cuenta a desactivar.
     * @return el DTO de la cuenta desactivada.
     * @throws RecursoNoEncontradoException si la cuenta no existe en la Empresa
     *                                      (Req 23.3).
     */
    @Transactional
    public UsuarioDto desactivarUsuario(UUID usuarioId) {
        UUID tenantId = TenantContext.require();
        String actor = actorActual();

        Usuario usuario = cargarDeLaEmpresa(usuarioId, tenantId);
        usuario.desactivar(actor);
        Usuario guardado = usuarioRepository.save(usuario);

        auditar(tenantId, actor, "desactivar",
                "desactivado usuario '" + usuario.getIdentificadorAcceso() + "'");

        // La desactivacion revoca las Sesiones vigentes de la cuenta (Req 68.2):
        // tras la expiracion del Token_Acceso vigente (<= 15 min) se impide todo
        // acceso de esa cuenta.
        int revocadas = registroSesiones.revocarTodasDeUsuario(usuarioId, MotivoRevocacion.DESACTIVACION);
        auditar(tenantId, actor, "revocar_sesiones",
                "revocadas " + revocadas + " sesion(es) del usuario '"
                        + usuario.getIdentificadorAcceso() + "' por desactivacion");
        return UsuarioDto.de(guardado);
    }

    /**
     * Revoca todas las Sesiones (Token_Refresco) vigentes de una cuenta a
     * peticion de un Administrador con Permiso (Req 68.2), sin desactivar la
     * cuenta. Util para cortar el acceso de inmediato ante un riesgo (robo de
     * credenciales) o como parte de un futuro cambio de contrasena (Req 68.4),
     * que debera invocar esta operacion (o {@link RegistroSesionesPort} con el
     * motivo {@code CAMBIO_PASSWORD}).
     *
     * @param usuarioId cuenta cuyas sesiones se revocan.
     * @return el numero de Sesiones revocadas por la operacion.
     * @throws RecursoNoEncontradoException si la cuenta no existe en la Empresa
     *                                      (Req 23.3).
     */
    @Transactional
    public int revocarSesiones(UUID usuarioId) {
        UUID tenantId = TenantContext.require();
        String actor = actorActual();

        Usuario usuario = cargarDeLaEmpresa(usuarioId, tenantId);
        int revocadas = registroSesiones.revocarTodasDeUsuario(usuarioId, MotivoRevocacion.ADMINISTRADOR);

        auditar(tenantId, actor, "revocar_sesiones",
                "revocadas " + revocadas + " sesion(es) del usuario '"
                        + usuario.getIdentificadorAcceso() + "' por administrador");
        return revocadas;
    }

    /**
     * Reemplaza el conjunto de Roles de un Usuario (Req 4.3).
     *
     * <p>Los Permisos del nuevo conjunto aplican en la siguiente evaluacion de
     * autorizacion (ver la nota de clase). Se validan los Roles con las mismas
     * reglas que en la creacion: existentes y asignables por la Empresa, nunca
     * de nivel plataforma.</p>
     *
     * @param usuarioId identificador de la cuenta.
     * @param rolIds    nuevos Roles a asignar (al menos uno).
     * @return el DTO de la cuenta con sus Roles actualizados.
     * @throws RecursoNoEncontradoException si la cuenta o algun Rol no existe en
     *                                      la Empresa (Req 23).
     * @throws ReglaNegocioException        si no se indica ningun Rol o algun Rol
     *                                      es de nivel plataforma (Req 27.7).
     */
    @Transactional
    public UsuarioDto asignarRoles(UUID usuarioId, Set<UUID> rolIds) {
        UUID tenantId = TenantContext.require();
        String actor = actorActual();

        Usuario usuario = cargarDeLaEmpresa(usuarioId, tenantId);
        Set<Rol> roles = resolverRolesAsignables(rolIds, tenantId);
        usuario.reemplazarRoles(roles, actor);
        Usuario guardado = usuarioRepository.save(usuario);

        String nombres = roles.stream().map(Rol::getNombre).sorted().collect(Collectors.joining(", "));
        auditar(tenantId, actor, "asignar_roles",
                "asignados roles [" + nombres + "] al usuario '" + usuario.getIdentificadorAcceso() + "'");
        return UsuarioDto.de(guardado);
    }

    /**
     * Actualiza el nombre PARA MOSTRAR de una cuenta de la Empresa (Req 4). El
     * nombre visible es un dato descriptivo, independiente del identificador de
     * acceso (login); un {@code null}/blanco lo deja sin definir. La cuenta debe
     * pertenecer a la Empresa del contexto (Req 23.3).
     *
     * @param usuarioId     identificador de la cuenta.
     * @param nombreVisible nuevo nombre para mostrar; {@code null}/blanco lo limpia.
     * @return el DTO de la cuenta con su nombre visible actualizado.
     * @throws RecursoNoEncontradoException si la cuenta no existe en la Empresa
     *                                      (Req 23.3).
     * @throws ReglaNegocioException        si el nombre visible excede su longitud
     *                                      maxima (HTTP 422).
     */
    @Transactional
    public UsuarioDto actualizarNombreVisible(UUID usuarioId, String nombreVisible) {
        UUID tenantId = TenantContext.require();
        String actor = actorActual();

        Usuario usuario = cargarDeLaEmpresa(usuarioId, tenantId);
        usuario.actualizarNombreVisible(nombreVisible, actor);
        Usuario guardado = usuarioRepository.save(usuario);

        auditar(tenantId, actor, "actualizar",
                "actualizado nombre visible del usuario '" + usuario.getIdentificadorAcceso() + "'");
        return UsuarioDto.de(guardado);
    }

    /**
     * Consulta una cuenta de la Empresa por su identificador (Req 4), para
     * precargar la edicion desde la interfaz. Tenant-scoped: una cuenta de otra
     * Empresa produce 404 (Req 23.3).
     *
     * @param usuarioId identificador de la cuenta.
     * @return el DTO de la cuenta.
     * @throws RecursoNoEncontradoException si la cuenta no existe en la Empresa.
     */
    @Transactional(readOnly = true)
    public UsuarioDto consultarUsuario(UUID usuarioId) {
        UUID tenantId = TenantContext.require();
        return UsuarioDto.de(cargarDeLaEmpresa(usuarioId, tenantId));
    }

    /**
     * Lista de forma paginada las cuentas de la Empresa del contexto (Req 4),
     * opcionalmente filtradas por un texto de busqueda {@code q} que coincide
     * (contiene, sin distinguir mayusculas/minusculas) en el identificador de
     * acceso o en el nombre visible. Tenant-scoped: solo cuentas de la propia
     * Empresa. Un {@code q} nulo/en blanco lista todas las cuentas del tenant.
     *
     * @param q        texto de busqueda opcional (identificador o nombre visible).
     * @param pageable parametros de paginacion (acotados a 20/100 por el controlador).
     * @return la pagina de cuentas de la Empresa proyectadas a {@link UsuarioDto}.
     */
    @Transactional(readOnly = true)
    public Page<UsuarioDto> listarUsuarios(String q, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        String filtro = (q == null || q.isBlank()) ? null : q.strip();
        return usuarioRepository.buscarPorTenant(tenantId, filtro, pageable)
                .map(UsuarioDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Carga una cuenta garantizando que pertenece a la Empresa (Req 23.3). Una
     * cuenta inexistente o de otra Empresa se traduce a 404 para no revelar su
     * existencia.
     */
    private Usuario cargarDeLaEmpresa(UUID usuarioId, UUID tenantId) {
        return usuarioRepository.findByIdAndTenantId(usuarioId, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el usuario solicitado."));
    }

    /**
     * Resuelve los Roles solicitados validando que TODOS existan, que ninguno
     * sea de nivel plataforma (Req 27.7) y que cada uno sea asignable por la
     * Empresa: un rol predefinido de nivel empresa (tenant_id NULL) o un rol
     * personalizado de la propia Empresa (tenant_id == tenant actual). Un
     * conjunto vacio se rechaza porque toda cuenta requiere al menos un Rol
     * (Req 4.1).
     */
    private Set<Rol> resolverRolesAsignables(Set<UUID> rolIds, UUID tenantId) {
        if (rolIds == null || rolIds.isEmpty()) {
            throw new ReglaNegocioException("Debe asignar al menos un Rol al Usuario.");
        }

        Set<UUID> idsSolicitados = new LinkedHashSet<>(rolIds);
        List<Rol> roles = new ArrayList<>();
        List<UUID> inexistentes = new ArrayList<>();
        List<String> dePlataforma = new ArrayList<>();
        List<UUID> deOtraEmpresa = new ArrayList<>();
        // Roles predefinidos cuyo modulo requerido NO esta contratado por la
        // Empresa (defensa en profundidad frente a la oferta de la interfaz).
        List<String> deModuloNoContratado = new ArrayList<>();

        for (UUID rolId : idsSolicitados) {
            Rol rol = rolRepository.findById(rolId).orElse(null);
            if (rol == null) {
                inexistentes.add(rolId);
                continue;
            }
            if (esRolPlataforma(rol)) {
                dePlataforma.add(rol.getNombre());
                continue;
            }
            // Asignable si es predefinido de empresa (tenant NULL) o de esta Empresa.
            boolean predefinidoDeEmpresa = rol.getTenantId() == null;
            boolean personalizadoDeEstaEmpresa = tenantId.equals(rol.getTenantId());
            if (!predefinidoDeEmpresa && !personalizadoDeEstaEmpresa) {
                deOtraEmpresa.add(rolId);
                continue;
            }
            // Gating por modulo contratado (defensa en profundidad): un rol
            // predefinido de MODULO solo es asignable si la Empresa contrato su
            // modulo. Los roles transversales (admin_empresa/gerente/supervisor)
            // y los roles personalizados de la Empresa no se restringen aqui:
            // RolModuloCatalogo.porNombre() devuelve vacio para ellos.
            if (predefinidoDeEmpresa && !moduloContratadoParaRol(rol, tenantId)) {
                deModuloNoContratado.add(rol.getNombre());
                continue;
            }
            roles.add(rol);
        }

        // Roles de nivel plataforma prohibidos para usuarios de empresa (Req 27.7).
        if (!dePlataforma.isEmpty()) {
            throw new ReglaNegocioException(
                    "No se puede asignar un Rol de nivel plataforma a un usuario de empresa: "
                            + dePlataforma);
        }
        // Roles de modulo cuyo modulo NO contrato la Empresa -> 422 (defensa en
        // profundidad, coherente con GET /roles/asignables).
        if (!deModuloNoContratado.isEmpty()) {
            throw new ReglaNegocioException(
                    "No se puede asignar un Rol cuyo modulo no esta contratado por la Empresa: "
                            + deModuloNoContratado);
        }
        // Roles inexistentes o de otra Empresa -> 404 (Req 23.3): no se revela cual.
        if (!inexistentes.isEmpty() || !deOtraEmpresa.isEmpty()) {
            throw new RecursoNoEncontradoException(
                    "Uno o mas Roles solicitados no existen o no pertenecen a esta Empresa.");
        }

        return new LinkedHashSet<>(roles);
    }

    /**
     * Un Rol es de nivel plataforma si es un rol predefinido de plataforma
     * (tenant_id NULL) cuyo nombre esta en {@link #ROLES_PLATAFORMA} (p. ej.
     * {@code super_admin}).
     */
    private static boolean esRolPlataforma(Rol rol) {
        return rol.getNombre() != null
                && ROLES_PLATAFORMA.contains(rol.getNombre().strip().toLowerCase(Locale.ROOT));
    }

    /**
     * Determina si el modulo requerido por un rol PREDEFINIDO esta contratado por
     * la Empresa (plataforma-multigiro). Se apoya en {@link RolModuloCatalogo}
     * (fuente unica de verdad de la relacion rol&rarr;modulo) y en
     * {@link ModulosHabilitadosPort} (misma fuente que el claim {@code modulos}
     * del JWT). Devuelve {@code true} para los roles no mapeados (roles
     * transversales de administracion/direccion: siempre asignables) y para los
     * roles de modulo cuyo modulo requerido si esta contratado.
     */
    private boolean moduloContratadoParaRol(Rol rol, UUID tenantId) {
        return RolModuloCatalogo.porNombre(rol.getNombre())
                .map(entrada -> entrada.esAsignableCon(modulosHabilitados.modulosHabilitadosDe(tenantId)))
                .orElse(true);
    }

    /**
     * Persiste la cuenta traduciendo una posible violacion del indice unico
     * global de {@code identificador_acceso} a {@link ConflictoUnicidadException}
     * (Req 4.4), por si dos peticiones concurrentes superan la comprobacion
     * previa.
     */
    private Usuario guardarTraduciendoUnicidad(Usuario usuario, String identificador) {
        try {
            return usuarioRepository.save(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoUnicidadException(
                    "Ya existe un usuario con el identificador de acceso '" + identificador + "'.");
        }
    }

    private void auditar(UUID tenantId, String actor, String accion, String detalle) {
        auditoria.registrar(
                EventoAuditoria.deTenant(tenantId, actor, accion, RECURSO_USUARIO, detalle, null, null));
    }

    /**
     * Resuelve el identificador del actor autenticado para la auditoria; si no
     * hay contexto de seguridad (p. ej. procesos internos), usa "sistema".
     */
    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }

    private static String normalizarIdentificador(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El identificador de acceso es obligatorio.");
        }
        // Minusculas para que el login sea insensible a mayusculas (bugfix).
        return valor.strip().toLowerCase(Locale.ROOT);
    }
}
