package com.dessti.crm.platform.security.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.empresas.EstadoEmpresaPort;
import com.dessti.crm.platform.security.auth.rest.TokenResponse;
import com.dessti.crm.platform.security.jwt.ClaimsToken;
import com.dessti.crm.platform.security.jwt.ServicioTokensJwt;
import com.dessti.crm.platform.security.jwt.TokenEmitido;
import com.dessti.crm.platform.security.jwt.TokenInvalidoException;
import com.dessti.crm.platform.security.rbac.GiroEmpresaPort;
import com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort;
import com.dessti.crm.platform.security.sesiones.MotivoRevocacion;
import com.dessti.crm.platform.security.sesiones.RegistroSesion;
import com.dessti.crm.platform.security.sesiones.RegistroSesionesPort;

/**
 * Servicio de aplicacion de autenticacion (Req 1, 2; tareas 9.1 y 9.2).
 *
 * <p>Casos de uso implementados:</p>
 * <ul>
 *   <li><b>login</b>: verifica credenciales contra un Usuario activo (hash
 *       BCrypt), aplica el <b>bloqueo por intentos fallidos</b> (Req 2.1, 2.2),
 *       <b>impide el inicio de sesion de una Empresa con acceso bloqueado</b>
 *       (suspendida, Req 24.4; o cancelada en Periodo_Gracia, Req 69.2, via
 *       {@link EstadoEmpresaPort#accesoBloqueado(java.util.UUID)}) con mensaje
 *       generico (Req 1.3), y emite
 *       Token_Acceso + Token_Refresco.</li>
 *   <li><b>refresh</b>: valida un Token_Refresco vigente y reemite un
 *       Token_Acceso.</li>
 *   <li><b>logout</b>: no-op del lado servidor para JWT stateless (ver nota).</li>
 * </ul>
 *
 * <p><strong>Errores genericos (Req 1.3) y excepcion del caso bloqueo
 * (Req 2.2):</strong> ante credenciales invalidas por <em>usuario inexistente,
 * cuenta inactiva o contrasena incorrecta</em> se lanza siempre el mismo
 * {@link AutenticacionException} con un mensaje <b>generico</b>, para no revelar
 * cual credencial fallo ni el estado de la cuenta y evitar la enumeracion
 * (Req 1.3). <b>Como excepcion deliberada y solo para el caso de cuenta
 * bloqueada</b>, el Req 2.2 exige literalmente que cada intento sobre una cuenta
 * bloqueada se rechace con un mensaje que <em>indique el bloqueo temporal y el
 * tiempo restante en minutos</em>. Por tanto, en esa unica rama el mensaje deja
 * de ser generico e incluye los minutos restantes; el resto de causas de fallo
 * permanecen genericas (Req 1.3). El criterio de aceptacion explicito del
 * Req 2.2 prevalece sobre la anti-enumeracion <b>solo</b> para el estado de
 * bloqueo. El detalle del rechazo por bloqueo se sigue registrando en la
 * bitacora de auditoria (Req 2.5).</p>
 *
 * <p><strong>Bloqueo por intentos fallidos (Req 2.1, 2.2):</strong></p>
 * <ol>
 *   <li>Antes de comparar la contrasena, si la cuenta esta bloqueada
 *       ({@code bloqueado_hasta} en el futuro) se rechaza y se audita el
 *       rechazo por bloqueo (Req 2.5).</li>
 *   <li>Ante contrasena incorrecta se incrementa {@code intentos_fallidos} y,
 *       al alcanzar 5 fallos consecutivos dentro de la ventana de 15 min, se
 *       fija {@code bloqueado_hasta = now + 15 min}. El contador se reinicia a 0
 *       cuando el bloqueo expira (Req 2.1).</li>
 *   <li>Ante un login exitoso se reinicia {@code intentos_fallidos} a 0 y se
 *       limpia {@code bloqueado_hasta}.</li>
 * </ol>
 * <p>Toda la logica temporal usa el {@link Clock} inyectado (nunca
 * {@code Instant.now()} directo) para pruebas deterministas. Como {@code login}
 * ahora <b>muta</b> al Usuario, su transaccion es de lectura/escritura.</p>
 *
 * <p><strong>Auditoria (Req 2.3, 2.5):</strong> cada intento de login (exitoso
 * o fallido) y cada rechazo por bloqueo se registran via {@link AuditoriaPort}
 * con el identificador de cuenta, la direccion de origen y el resultado. Nunca
 * se incluye la contrasena ni el hash en el evento (Req 10.10).</p>
 *
 * <p><strong>Gestion y revocacion de Sesion (Req 68, tarea 11.2):</strong> el
 * servicio se apoya en {@link RegistroSesionesPort} para:</p>
 * <ul>
 *   <li><b>login</b>: registrar el Token_Refresco emitido (su {@code jti},
 *       usuario, tenant, emision y expiracion) como Sesion activa (Req 68.3),
 *       para poder listarla y revocarla despues.</li>
 *   <li><b>refresh</b>: rechazar (401) un Token_Refresco cuyo {@code jti} este
 *       revocado o sea desconocido (Req 1.9, 68.3) y, en el camino exitoso,
 *       <b>rotar</b> el Token_Refresco: revocar el {@code jti} presentado
 *       (motivo interno) y emitir/registrar uno nuevo. La rotacion limita la
 *       ventana de reutilizacion de un refresco filtrado.</li>
 *   <li><b>logout</b>: revocar el {@code jti} del Token_Refresco del Usuario,
 *       de modo que no pueda emitir nuevos Token_Acceso (Req 68.1).</li>
 * </ul>
 * <p>La revocacion por Administrador, por desactivacion de cuenta y por cambio
 * de contrasena (Req 68.2, 68.4) se coordina desde la gestion de Usuarios
 * (tarea 11.1) sobre el mismo {@link RegistroSesionesPort}.</p>
 */
@Service
public class ServicioAutenticacion {

    private static final Logger log = LoggerFactory.getLogger(ServicioAutenticacion.class);

    private static final String MSG_CREDENCIALES = "Credenciales invalidas";
    private static final String MSG_REFRESCO = "Token de refresco invalido";

    /** Recurso de auditoria para los eventos de autenticacion. */
    private static final String RECURSO_AUTENTICACION = "autenticacion";
    /** Recurso de auditoria para los eventos de sesion (Req 68). */
    private static final String RECURSO_SESION = "sesion";
    /** Acciones de auditoria (verbos). */
    private static final String ACCION_LOGIN = "login";
    /** Accion de auditoria para el cierre de sesion (Req 68.1). */
    private static final String ACCION_LOGOUT = "logout";
    /** Actor cuando el identificador no corresponde a una cuenta conocida. */
    private static final String ACTOR_DESCONOCIDO = "desconocido";
    /** Valor por defecto para la direccion de origen no disponible. */
    private static final String IP_DESCONOCIDA = "desconocida";

    private final UsuarioAuthRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ServicioTokensJwt servicioTokens;
    private final AuditoriaPort auditoria;
    private final RegistroSesionesPort registroSesiones;
    private final EstadoEmpresaPort estadoEmpresa;
    private final GiroEmpresaPort giroEmpresa;
    private final ModulosHabilitadosPort modulosHabilitados;
    private final Clock clock;

    public ServicioAutenticacion(UsuarioAuthRepository usuarioRepository,
                                 PasswordEncoder passwordEncoder,
                                 ServicioTokensJwt servicioTokens,
                                 AuditoriaPort auditoria,
                                 RegistroSesionesPort registroSesiones,
                                 EstadoEmpresaPort estadoEmpresa,
                                 GiroEmpresaPort giroEmpresa,
                                 ModulosHabilitadosPort modulosHabilitados,
                                 Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.servicioTokens = servicioTokens;
        this.auditoria = auditoria;
        this.registroSesiones = registroSesiones;
        this.estadoEmpresa = estadoEmpresa;
        this.giroEmpresa = giroEmpresa;
        this.modulosHabilitados = modulosHabilitados;
        this.clock = clock;
    }

    /**
     * Verifica credenciales, aplica el bloqueo por intentos fallidos y emite el
     * par de tokens (Req 1.1, 1.2, 1.4, 1.7, 2.1, 2.2, 2.3, 2.5).
     *
     * @param identificador identificador de acceso del Usuario.
     * @param password      contrasena en claro (se compara contra el hash).
     * @param direccionOrigen direccion IP de origen para la auditoria; puede ser
     *                        {@code null} si no se pudo resolver.
     * @return el par de tokens emitido.
     * @throws AutenticacionException si las credenciales son invalidas o la
     *         cuenta esta bloqueada (mensaje generico, Req 1.3).
     */
    @Transactional
    public TokenResponse login(String identificador, String password, String direccionOrigen) {
        String ip = normalizarIp(direccionOrigen);

        // Login insensible a mayusculas/minusculas en el identificador de acceso
        // (bugfix): el identificador se almacena en minusculas, asi que se
        // normaliza la entrada antes de resolver la cuenta. La contrasena SIGUE
        // siendo sensible a mayusculas (no se toca).
        String identificadorNormalizado =
                (identificador == null) ? null : identificador.strip().toLowerCase(java.util.Locale.ROOT);

        Optional<UsuarioAuth> encontrado =
                usuarioRepository.findByIdentificadorAcceso(identificadorNormalizado);

        if (encontrado.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Login fallido: identificador no encontrado");
            }
            // No hay cuenta que auditar por actor real: se registra con actor
            // desconocido para dejar traza del intento (Req 2.3).
            auditarIntento(null, identificador, ip, false, "identificador no encontrado");
            throw new AutenticacionException(MSG_CREDENCIALES);
        }

        UsuarioAuth usuario = encontrado.get();

        // (1) Rechazo por bloqueo vigente ANTES de comparar la contrasena
        //     (Req 2.2). Mensaje generico al cliente; el detalle del bloqueo se
        //     audita internamente (Req 2.5).
        if (usuario.estaBloqueado(clock)) {
            long minutos = usuario.minutosRestantesBloqueo(clock);
            if (log.isDebugEnabled()) {
                log.debug("Login rechazado: cuenta bloqueada, {} min restantes", minutos);
            }
            auditarRechazoBloqueo(usuario, ip, minutos);
            // Req 2.2: el mensaje al cliente indica el bloqueo temporal y el
            // tiempo restante en minutos (excepcion documentada a la
            // anti-enumeracion del Req 1.3, solo para el caso de bloqueo).
            throw new AutenticacionException(mensajeBloqueo(minutos));
        }

        // Cuenta inactiva: mismo error generico que credenciales invalidas
        // para no revelar el estado de la cuenta (Req 1.3, 4.2).
        if (!usuario.isActivo()) {
            if (log.isDebugEnabled()) {
                log.debug("Login rechazado: cuenta inactiva");
            }
            auditarIntento(usuario, identificador, ip, false, "cuenta inactiva");
            throw new AutenticacionException(MSG_CREDENCIALES);
        }

        // Empresa con acceso bloqueado por su estado (Req 24.4, 69.2): se impide
        // el inicio de sesion cuando la Empresa esta SUSPENDIDA (Req 24.4) o
        // CANCELADA durante su Periodo_Gracia (Req 69.2), en el que los datos se
        // conservan pero el acceso queda restringido conforme al estado. El
        // super_admin es de plataforma (tenant_id NULL) y NO se ve afectado, por
        // lo que la comprobacion se omite cuando la cuenta no pertenece a ninguna
        // Empresa. El mensaje al cliente es GENERICO (anti-enumeracion, Req 1.3):
        // no se revela el estado de la Empresa; el motivo se registra en la
        // auditoria interna. Se comprueba ANTES que la contrasena para no
        // realizar trabajo criptografico innecesario sobre una Empresa bloqueada.
        if (usuario.getTenantId() != null && estadoEmpresa.accesoBloqueado(usuario.getTenantId())) {
            if (log.isDebugEnabled()) {
                log.debug("Login rechazado: acceso bloqueado por estado de la empresa");
            }
            auditarIntento(usuario, identificador, ip, false, "acceso bloqueado por estado de la empresa");
            throw new AutenticacionException(MSG_CREDENCIALES);
        }

        // (2) Contrasena incorrecta: incrementar contador / aplicar bloqueo
        //     (Req 2.1) y auditar el intento fallido (Req 2.3).
        if (!passwordEncoder.matches(password, usuario.getHashPassword())) {
            if (log.isDebugEnabled()) {
                log.debug("Login fallido: contrasena incorrecta");
            }
            usuario.registrarFallo(clock);
            usuarioRepository.save(usuario);
            boolean quedaBloqueada = usuario.estaBloqueado(clock);
            String detalle = quedaBloqueada
                    ? "contrasena incorrecta; cuenta bloqueada por " + UsuarioAuth.MINUTOS_BLOQUEO + " min"
                    : "contrasena incorrecta; intentos fallidos=" + usuario.getIntentosFallidos();
            auditarIntento(usuario, identificador, ip, false, detalle);
            // Si este fallo dispara el bloqueo, la cuenta ya esta bloqueada: por
            // el Req 2.2 se responde con el mensaje de bloqueo y los minutos
            // restantes. Si aun no se bloquea, el mensaje es generico (Req 1.3).
            if (quedaBloqueada) {
                throw new AutenticacionException(mensajeBloqueo(usuario.minutosRestantesBloqueo(clock)));
            }
            throw new AutenticacionException(MSG_CREDENCIALES);
        }

        // (3) Login exitoso: reiniciar contador y limpiar bloqueo.
        usuario.registrarExito();
        usuarioRepository.save(usuario);
        auditarIntento(usuario, identificador, ip, true, null);

        List<String> roles = usuarioRepository.buscarNombresRoles(usuario.getId());
        List<String> permisos = usuarioRepository.buscarPermisos(usuario.getId());
        String subject = usuario.getId().toString();

        // Resolucion del Giro para el contexto de sesion del frontend (Req 9.1,
        // tarea 10.1): se consulta el Giro del tenant en el login (unico punto
        // con acceso a BD). El super_admin (tenant nulo) no pertenece a ninguna
        // Empresa, por lo que no se consulta y el claim giro queda ausente. Un
        // tenant cuyo Giro no se resuelva (deny-safe del puerto) tampoco lleva
        // claim, coherente con el manejo de tenant_id nulo.
        String giro = (usuario.getTenantId() == null)
                ? null
                : giroEmpresa.giroDeTenant(usuario.getTenantId()).orElse(null);

        // El identificador de acceso legible (p. ej. superadmin@dessti) viaja
        // como claim propio para que el frontend muestre el login real en el
        // menu de cuenta (el subject es el UUID interno del Usuario). Se usa un
        // nombre distinto al parametro 'identificador' del metodo para no
        // ensombrecerlo.
        String identificadorLegible = usuario.getIdentificadorAcceso();

        // Modulos habilitados de la Empresa para el claim 'modulos' (Req 25.4):
        // el frontend pinta el menu solo con lo contratado, sin endpoint extra.
        // El super_admin (tenant nulo) NO se rige por el gating de modulos, por
        // lo que se pasa null y el claim se omite, coherente con giro/tenant_id.
        // Una Empresa sin modulos habilitados lleva el claim vacio ([]).
        List<String> modulos = (usuario.getTenantId() == null)
                ? null
                : modulosHabilitados.modulosHabilitadosDe(usuario.getTenantId());

        TokenEmitido acceso = servicioTokens.emitirTokenAcceso(
                subject, usuario.getTenantId(), roles, permisos, giro, identificadorLegible, modulos);
        TokenEmitido refresco = servicioTokens.emitirTokenRefresco(
                subject, usuario.getTenantId(), roles, permisos, giro, identificadorLegible, modulos);

        // Registrar la Sesion (Token_Refresco) para poder listarla/revocarla
        // (Req 68.3). Solo se persisten metadatos, nunca el valor del token.
        registrarSesion(refresco, usuario.getId(), usuario.getTenantId());

        return construirRespuesta(acceso, refresco.valor(), usuario.isDebeCambiarPassword());
    }

    /**
     * Valida un Token_Refresco vigente y reemite un Token_Acceso (Req 1.5). Un
     * refresco expirado o invalido produce 401 sin emitir token (Req 1.9).
     *
     * @throws AutenticacionException si el Token_Refresco es invalido/expirado.
     */
    /**
     * Valida un Token_Refresco vigente y reemite un Token_Acceso (Req 1.5),
     * <b>rotando</b> el Token_Refresco (Req 68). Un refresco expirado, invalido
     * o <b>revocado</b> produce 401 sin emitir tokens (Req 1.9, 68.3).
     *
     * <p><strong>Rotacion:</strong> tras validar la firma/vigencia y comprobar
     * que el {@code jti} presentado NO esta revocado ni es desconocido, se
     * revoca ese {@code jti} (motivo {@link MotivoRevocacion#LOGOUT} como marca
     * de reemplazo por rotacion) y se emite y registra un nuevo Token_Refresco.
     * Asi, si un atacante intenta reutilizar el refresco anterior, sera
     * rechazado por estar ya revocado.</p>
     *
     * @throws AutenticacionException si el Token_Refresco es invalido, expirado
     *         o revocado.
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        ClaimsToken claims;
        try {
            claims = servicioTokens.validarTokenRefresco(refreshToken);
        } catch (TokenInvalidoException ex) {
            if (log.isDebugEnabled()) {
                log.debug("Refresh rechazado: {}", ex.getMessage());
            }
            throw new AutenticacionException(MSG_REFRESCO);
        }

        // Consultar la denylist/registro de sesiones por jti y rechazar un
        // refresco revocado o desconocido (Req 1.9, 68.3) ANTES de reemitir.
        if (registroSesiones.estaRevocado(claims.jti())) {
            if (log.isDebugEnabled()) {
                log.debug("Refresh rechazado: jti revocado o desconocido");
            }
            throw new AutenticacionException(MSG_REFRESCO);
        }

        UUID usuarioId = parsearUsuarioId(claims.subject());

        // El Giro ya viaja en los claims del Token_Refresco (se incluye en AMBOS
        // tokens al hacer login), por lo que el refresco se reemite sin acceder
        // a la BD (Req 1.5): se reutiliza claims.giro() en vez de consultar de
        // nuevo el Giro del tenant, preservando el comportamiento sin-BD.
        // El identificador legible ya viaja en los claims del Token_Refresco
        // (se incluye en AMBOS tokens al hacer login), por lo que se preserva al
        // reemitir sin acceder a la BD, igual que el giro.
        // Los modulos habilitados ya viajan en los claims del Token_Refresco
        // (se incluyen en AMBOS tokens al hacer login), por lo que el refresco se
        // reemite sin acceder a la BD, igual que el giro. Se preserva la
        // distincion omitido-vs-vacio: un principal de plataforma (tenant nulo,
        // super_admin) NO se rige por el gating de modulos y su claim debe seguir
        // OMITIDO, por lo que se pasa null; una Empresa conserva su lista (posible
        // vacia = cero modulos) reemitiendola tal cual.
        List<String> modulos = (claims.tenantId() == null) ? null : claims.modulos();

        TokenEmitido acceso = servicioTokens.emitirTokenAcceso(
                claims.subject(), claims.tenantId(), claims.roles(), claims.permisos(),
                claims.giro(), claims.identificador(), modulos);

        // Rotacion del Token_Refresco (Req 68): revocar el jti presentado y
        // emitir/registrar uno nuevo. El refresco anterior queda inutilizable.
        registroSesiones.revocar(claims.jti(), MotivoRevocacion.LOGOUT);
        TokenEmitido nuevoRefresco = servicioTokens.emitirTokenRefresco(
                claims.subject(), claims.tenantId(), claims.roles(), claims.permisos(),
                claims.giro(), claims.identificador(), modulos);
        if (usuarioId != null) {
            registrarSesion(nuevoRefresco, usuarioId, claims.tenantId());
        }

        // El refresco no re-evalua la obligacion de cambio: si el Usuario
        // llego a refrescar es porque ya paso el login; el flag solo aplica al
        // login inicial. Se propaga false para no forzar el cambio en cada refresh.
        return construirRespuesta(acceso, nuevoRefresco.valor(), false);
    }

    /**
     * Cierre de sesion (Req 68.1): revoca el Token_Refresco presentado para que
     * no pueda emitir nuevos Token_Acceso. Se identifica la Sesion por el
     * {@code jti} del propio Token_Refresco.
     *
     * <p>La operacion es <b>tolerante</b>: un Token_Refresco invalido, expirado
     * o ya revocado no produce error (el cierre de sesion es idempotente desde
     * la perspectiva del cliente, que en cualquier caso descarta sus tokens);
     * el controlador responde 204. Solo se audita la revocacion efectiva.</p>
     *
     * @param refreshToken Token_Refresco de la Sesion a cerrar; puede ser
     *                     {@code null}/vacio, en cuyo caso el logout es un no-op.
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        ClaimsToken claims;
        try {
            claims = servicioTokens.validarTokenRefresco(refreshToken);
        } catch (TokenInvalidoException ex) {
            // Un refresco invalido/expirado en logout no es un error: nada que
            // revocar. El cliente descarta sus tokens de todas formas.
            if (log.isDebugEnabled()) {
                log.debug("Logout con refresco no valido: {}", ex.getMessage());
            }
            return;
        }

        int revocadas = registroSesiones.revocar(claims.jti(), MotivoRevocacion.LOGOUT);
        if (revocadas > 0) {
            auditarSesion(claims.tenantId(), claims.subject(), ACCION_LOGOUT,
                    "sesion cerrada; jti=" + claims.jti() + "; motivo=" + MotivoRevocacion.LOGOUT);
        }
    }

    // -----------------------------------------------------------------
    // Sesiones (Req 68) — registro y utilidades.
    // -----------------------------------------------------------------

    /**
     * Registra el Token_Refresco emitido como Sesion activa (Req 68.3),
     * tolerando fallos del puerto de sesiones sin abortar la emision de tokens:
     * un problema al registrar no debe convertir un login/refresh legitimo en
     * un error 500. El fallo se registra en el log tecnico.
     */
    private void registrarSesion(TokenEmitido refresco, UUID usuarioId, UUID tenantId) {
        try {
            registroSesiones.registrar(new RegistroSesion(
                    refresco.jti(), usuarioId, tenantId, clock.instant(), refresco.expiracion()));
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar la sesion de refresco: {}",
                    ex.getClass().getSimpleName());
        }
    }

    /**
     * Interpreta el {@code sub} del token como el {@code usuario_id} (UUID). Si
     * no es un UUID valido (no deberia ocurrir en tokens emitidos por el
     * Sistema), devuelve {@code null} para no abortar el flujo; el nuevo
     * refresco simplemente no se registrara.
     */
    private static UUID parsearUsuarioId(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // -----------------------------------------------------------------
    // Auditoria (Req 2.3, 2.5) — nunca se incluye la contrasena ni el hash.
    // -----------------------------------------------------------------

    /**
     * Registra un intento de login (exitoso o fallido) en la bitacora (Req 2.3).
     * El detalle incluye la direccion de origen y el resultado; nunca la
     * contrasena ni el hash (Req 10.10).
     */
    private void auditarIntento(UsuarioAuth usuario, String identificador, String ip,
                                boolean exitoso, String motivo) {
        String actor = actorDe(usuario, identificador);
        String detalle = "resultado=" + (exitoso ? "exitoso" : "fallido")
                + "; ip=" + ip
                + (motivo != null ? "; motivo=" + motivo : "");
        registrar(usuario, actor, detalle);
    }

    /**
     * Registra el rechazo de un intento sobre una cuenta bloqueada (Req 2.5),
     * incluyendo el tiempo restante de bloqueo con fines internos.
     */
    private void auditarRechazoBloqueo(UsuarioAuth usuario, String ip, long minutosRestantes) {
        String detalle = "resultado=rechazado_por_bloqueo"
                + "; ip=" + ip
                + "; minutos_restantes=" + minutosRestantes;
        registrar(usuario, usuario.getIdentificadorAcceso(), detalle);
    }

    /**
     * Construye y registra el {@link EventoAuditoria}, tolerando fallos del
     * puerto de auditoria sin abortar el flujo de autenticacion: un problema al
     * auditar no debe convertir un login legitimo en un error 500 ni ocultar el
     * rechazo de credenciales. El fallo se registra en el log tecnico.
     */
    private void registrar(UsuarioAuth usuario, String actor, String detalle) {
        try {
            UUID tenantId = (usuario != null) ? usuario.getTenantId() : null;
            EventoAuditoria evento = (tenantId != null)
                    ? EventoAuditoria.deTenant(tenantId, actor, ACCION_LOGIN,
                            RECURSO_AUTENTICACION, detalle, null, null)
                    : EventoAuditoria.dePlataforma(actor, ACCION_LOGIN,
                            RECURSO_AUTENTICACION, detalle, null, null);
            auditoria.registrar(evento);
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar el evento de auditoria de login: {}",
                    ex.getClass().getSimpleName());
        }
    }

    /**
     * Registra un evento de auditoria de Sesion (Req 68, Req 10) tolerando
     * fallos del puerto de auditoria. Nunca incluye el valor del token; el
     * {@code jti} si puede registrarse.
     */
    private void auditarSesion(UUID tenantId, String actor, String accion, String detalle) {
        try {
            String actorEfectivo = (actor != null && !actor.isBlank()) ? actor : ACTOR_DESCONOCIDO;
            EventoAuditoria evento = (tenantId != null)
                    ? EventoAuditoria.deTenant(tenantId, actorEfectivo, accion,
                            RECURSO_SESION, detalle, null, null)
                    : EventoAuditoria.dePlataforma(actorEfectivo, accion,
                            RECURSO_SESION, detalle, null, null);
            auditoria.registrar(evento);
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar el evento de auditoria de sesion: {}",
                    ex.getClass().getSimpleName());
        }
    }

    private static String actorDe(UsuarioAuth usuario, String identificador) {
        if (usuario != null) {
            return usuario.getIdentificadorAcceso();
        }
        return (identificador != null && !identificador.isBlank())
                ? identificador
                : ACTOR_DESCONOCIDO;
    }

    private static String normalizarIp(String direccionOrigen) {
        return (direccionOrigen != null && !direccionOrigen.isBlank())
                ? direccionOrigen
                : IP_DESCONOCIDA;
    }

    /**
     * Construye el mensaje de rechazo por cuenta bloqueada exigido por el
     * Req 2.2: indica el bloqueo temporal y el tiempo restante en minutos, con
     * concordancia singular/plural. Ejemplos:
     * <ul>
     *   <li>{@code "La cuenta esta temporalmente bloqueada. Intentelo de nuevo en 1 minuto."}</li>
     *   <li>{@code "La cuenta esta temporalmente bloqueada. Intentelo de nuevo en 15 minutos."}</li>
     * </ul>
     * Se garantiza un minimo de 1 minuto para no mostrar "0 minutos" en el borde
     * de expiracion (los minutos restantes ya se redondean hacia arriba en el
     * dominio, pero se refuerza aqui por robustez).
     *
     * @param minutosRestantes minutos restantes de bloqueo (>= 0).
     * @return el mensaje de bloqueo con el tiempo restante en minutos.
     */
    private static String mensajeBloqueo(long minutosRestantes) {
        long minutos = Math.max(1, minutosRestantes);
        String unidad = (minutos == 1) ? "minuto" : "minutos";
        return "La cuenta esta temporalmente bloqueada. Intentelo de nuevo en "
                + minutos + " " + unidad + ".";
    }

    private TokenResponse construirRespuesta(TokenEmitido acceso, String refreshToken,
                                             boolean debeCambiarPassword) {
        long expiresIn = Math.max(0,
                Duration.between(clock.instant(), acceso.expiracion()).toSeconds());
        return new TokenResponse(acceso.valor(), refreshToken, TokenResponse.TIPO_BEARER, expiresIn,
                debeCambiarPassword);
    }
}
