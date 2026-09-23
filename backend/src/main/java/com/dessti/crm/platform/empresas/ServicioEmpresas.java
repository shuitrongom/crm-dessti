package com.dessti.crm.platform.empresas;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
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
import com.dessti.crm.platform.giros.adapter.out.persistence.GiroRepository;
import com.dessti.crm.platform.giros.domain.Giro;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.security.roles.Rol;
import com.dessti.crm.platform.security.roles.RolRepository;
import com.dessti.crm.platform.security.sesiones.MotivoRevocacion;
import com.dessti.crm.platform.security.sesiones.RegistroSesionesPort;
import com.dessti.crm.platform.security.usuarios.Usuario;
import com.dessti.crm.platform.security.usuarios.UsuarioRepository;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

import java.util.List;

/**
 * Servicio de aplicacion de <strong>plataforma</strong> que gobierna la
 * administracion de Empresas (tenants) por parte del {@code super_admin}
 * (Req 24).
 *
 * <h2>Operaciones (Req 24.1)</h2>
 * <ul>
 *   <li><strong>crearEmpresa (Req 24.2):</strong> crea la Empresa en estado
 *       {@code activa} con un {@code tenant_id} unico (su propia PK), aprovisiona
 *       al menos un Usuario {@code admin_empresa} para esa Empresa y crea una
 *       Suscripcion basica al Plan inicial indicado. La unicidad del RFC a nivel
 *       de plataforma se comprueba en el servicio ({@code existsByRfc}) y se
 *       refuerza con el indice unico {@code uq_empresa_rfc} (V8); un duplicado
 *       produce 409.</li>
 *   <li><strong>activarEmpresa / suspenderEmpresa (Req 24.1, 24.4):</strong>
 *       cambian el estado. La suspension impide el inicio de sesion de los
 *       Usuarios de la Empresa mientras dure, mediante {@link EstadoEmpresaPort}
 *       que consume {@code ServicioAutenticacion}.</li>
 *   <li><strong>consultarEmpresa / listarEmpresas (Req 24.1, 24.5):</strong>
 *       consulta puntual y listado paginado (20/100) filtrable por estado.</li>
 * </ul>
 *
 * <h2>Aislamiento del super_admin (Req 24.3)</h2>
 * <p>Este servicio opera exclusivamente sobre datos de <em>plataforma</em> de la
 * Empresa; no expone ni consulta dato alguno de negocio de las Empresas. El
 * acceso queda restringido por RBAC a los permisos {@code empresa:*} (V5), que
 * solo posee el {@code super_admin}.</p>
 *
 * <h2>Auditoria (Req 24.6)</h2>
 * <p>Cada operacion de plataforma sobre una Empresa se registra via
 * {@link AuditoriaPort} como evento de plataforma (sin tenant) con el actor, la
 * accion y la Empresa afectada, en UTC. Nunca se incluye la contrasena ni su
 * hash (Req 10.10, 11.3).</p>
 *
 * <h2>Aprovisionamiento del admin_empresa (Req 24.2)</h2>
 * <p>El primer Usuario {@code admin_empresa} se crea directamente contra
 * {@link UsuarioRepository} fijando su {@code tenant_id} a la Empresa recien
 * creada. No se reutiliza {@code ServicioUsuarios} porque este deriva el tenant
 * del contexto autenticado (que aqui es el {@code super_admin}, de plataforma,
 * con {@code tenant_id} nulo) y no puede aprovisionar cuentas para un tenant
 * distinto del suyo. Se le asigna el rol predefinido {@code admin_empresa}
 * (tenant_id NULL), localizado por nombre en {@link RolRepository}.</p>
 */
@Service
public class ServicioEmpresas {

    /** Recurso de auditoria/RBAC de nivel plataforma asociado a la Empresa. */
    static final String RECURSO_EMPRESA = "empresa";

    /** Nombre del rol predefinido de nivel empresa asignado al primer admin (V5). */
    static final String ROL_ADMIN_EMPRESA = "admin_empresa";

    /** Longitud en bytes de la contrasena temporal generada (>= 24 bytes). */
    private static final int BYTES_PASSWORD_TEMPORAL = 24;

    private final EmpresaRepository empresaRepository;
    private final GiroRepository giroRepository;
    private final PlanRepository planRepository;
    private final PaqueteSuscripcionRepository paqueteSuscripcionRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatosVerticalPort datosVertical;
    private final AuditoriaPort auditoria;
    private final RegistroSesionesPort registroSesiones;
    private final ContratacionProperties contratacionProperties;
    private final Clock clock;
    private final TenantSessionInitializer tenantSession;
    private final SecureRandom random = new SecureRandom();

    public ServicioEmpresas(EmpresaRepository empresaRepository,
                            GiroRepository giroRepository,
                            PlanRepository planRepository,
                            PaqueteSuscripcionRepository paqueteSuscripcionRepository,
                            SuscripcionRepository suscripcionRepository,
                            UsuarioRepository usuarioRepository,
                            RolRepository rolRepository,
                            PasswordEncoder passwordEncoder,
                            DatosVerticalPort datosVertical,
                            AuditoriaPort auditoria,
                            RegistroSesionesPort registroSesiones,
                            ContratacionProperties contratacionProperties,
                            Clock clock,
                            TenantSessionInitializer tenantSession) {
        this.empresaRepository = empresaRepository;
        this.giroRepository = giroRepository;
        this.planRepository = planRepository;
        this.paqueteSuscripcionRepository = paqueteSuscripcionRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.datosVertical = datosVertical;
        this.auditoria = auditoria;
        this.registroSesiones = registroSesiones;
        this.contratacionProperties = contratacionProperties;
        this.clock = clock;
        this.tenantSession = tenantSession;
    }

    /**
     * Da de alta una Empresa con datos validos (Req 24.2, 4.1-4.5): crea la
     * Empresa activa con {@code tenant_id} unico, su primer {@code admin_empresa}
     * y su <strong>Contrato inicial</strong> con el instrumento excluyente
     * indicado (un Plan <em>o</em> un Paquete de Suscripcion, nunca ambos).
     *
     * <h2>Instrumento excluyente (Req 4.3)</h2>
     * <p>El comando debe traer EXACTAMENTE uno de {@code planId}/
     * {@code paqueteSuscripcionId}; indicar ambos o ninguno se rechaza con 422.
     * Cuando es un Plan se crea un Contrato {@code ACTIVA} sin fin de vigencia.
     * Cuando es una Suscripcion:</p>
     * <ul>
     *   <li>si {@code otorgarPrueba} y el Paquete admite prueba, el Contrato nace
     *       {@code EN_PRUEBA} con {@code vigenciaFin} derivada de la duracion de
     *       la prueba;</li>
     *   <li>si {@code otorgarPrueba} pero el Paquete NO admite prueba, se rechaza
     *       con 422 (mas claro para el {@code super_admin} que ignorar la peticion);</li>
     *   <li>en otro caso el Contrato nace {@code ACTIVA} con
     *       {@code vigenciaFin = hoy + duracionDias} del Paquete.</li>
     * </ul>
     *
     * @param comando datos del alta (Empresa, instrumento inicial y admin inicial).
     * @return el resultado del alta, incluida la contrasena temporal cuando se
     *         genero automaticamente (una unica vez, Req 11.3).
     * @throws ReglaNegocioException      si faltan datos obligatorios, se viola la
     *                                    exclusividad del instrumento (422, Req 4.3)
     *                                    o se solicita prueba sobre un Paquete que
     *                                    no la admite (422, Req 4.2).
     * @throws ConflictoUnicidadException si el RFC o el identificador del admin
     *                                    ya existen (Req 24.2, 4.4).
     * @throws RecursoNoEncontradoException si el Plan o el Paquete de Suscripcion
     *                                    inicial no existen (Req 24.2, 4.1) o falta
     *                                    el rol predefinido {@code admin_empresa}.
     */
    @Transactional
    public EmpresaCreadaDto crearEmpresa(CrearEmpresaCommand comando) {
        String actor = actorActual();
        validarComando(comando);

        // Normaliza (mayusculas) y valida la ESTRUCTURA del RFC mexicano (Req 24)
        // antes de tocar la BD: un RFC mal formado se rechaza con 422 sin consultar
        // unicidad. La misma validacion la refuerza el dominio en Empresa.crear.
        String rfcNormalizado = RfcValidador.normalizarYValidar(comando.rfc());
        if (empresaRepository.existsByRfc(rfcNormalizado)) {
            throw new ConflictoUnicidadException(
                    "Ya existe una Empresa con el identificador fiscal '" + rfcNormalizado + "'.");
        }

        // El Giro es obligatorio y debe existir y estar ACTIVO (Req 2.1/2.2). Un
        // Giro ausente/inexistente/inactivo produce 422 (ReglaNegocioException),
        // coherente con las demas reglas de negocio del alta.
        Giro giro = validarGiroActivo(comando.giroId());

        // El instrumento inicial es excluyente (Req 4.3): se contrata con un Plan
        // O con un Paquete de Suscripcion, nunca ambos. Se resuelve aqui (404 si
        // el catalogo indicado no existe) para fallar antes de tocar filas de la
        // nueva Empresa. El Contrato concreto se crea mas abajo, una vez fijado
        // el tenant, segun el instrumento resuelto.
        boolean esPlan = comando.planId() != null;
        Plan plan = null;
        PaqueteSuscripcion paquete = null;
        if (esPlan) {
            plan = planRepository.findById(comando.planId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No se encontro el Plan inicial indicado."));
        } else {
            paquete = paqueteSuscripcionRepository.findById(comando.paqueteSuscripcionId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No se encontro la Suscripcion inicial indicada."));
            // Si se solicita prueba, el Paquete debe admitirla (Req 4.2): se rechaza
            // con 422 en lugar de ignorar silenciosamente la peticion, lo que es mas
            // claro para el super_admin.
            if (comando.otorgarPrueba() && !paquete.isAdmitePrueba()) {
                throw new ReglaNegocioException(
                        "El paquete seleccionado no admite periodo de prueba.");
            }
        }

        // Identificador de acceso normalizado a minusculas para que el login sea
        // insensible a mayusculas/minusculas (bugfix login case-insensitive).
        String identificadorAdmin = comando.adminIdentificador().strip().toLowerCase(java.util.Locale.ROOT);
        if (usuarioRepository.existsByIdentificadorAcceso(identificadorAdmin)) {
            throw new ConflictoUnicidadException(
                    "Ya existe un usuario con el identificador de acceso '" + identificadorAdmin + "'.");
        }

        Rol rolAdmin = rolRepository.findByNombreAndTenantIdIsNull(ROL_ADMIN_EMPRESA)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el rol predefinido '" + ROL_ADMIN_EMPRESA + "'."));

        // 1) Empresa activa con tenant_id unico (su propia PK, Req 24.2) ligada a
        //    su Giro obligatorio, ya validado como existente y activo (Req 2.1/2.2/2.3).
        Empresa empresa = Empresa.crear(comando.nombre(), rfcNormalizado, giro.getId(), actor);
        // Datos descriptivos/de contacto opcionales (Req 24): se asignan ANTES de
        // guardar para que persistan en la misma insercion. Si no vienen, se omite.
        if (comando.datos() != null) {
            empresa.asignarDatosDescriptivos(comando.datos(), actor);
        }
        Empresa empresaGuardada = guardarEmpresaTraduciendoUnicidad(empresa, rfcNormalizado);
        UUID tenantId = empresaGuardada.getId();

        // Fija app.current_tenant al nuevo tenant para que las inserciones del
        // admin_empresa y de la suscripcion satisfagan las politicas RLS
        // WITH CHECK de V48 (usuario_insercion), sin debilitar la seguridad: el
        // super_admin opera en contexto de plataforma (sin tenant), por lo que
        // hay que fijar explicitamente el tenant destino dentro de esta
        // transaccion antes de tocar filas tenant-scoped de la nueva Empresa.
        tenantSession.applyTenant(tenantId);

        // 2) Primer Usuario admin_empresa de la Empresa (Req 24.2). La contrasena
        //    se cifra de inmediato; si no se proporciono, se genera una temporal.
        boolean passwordGenerada = (comando.adminPassword() == null || comando.adminPassword().isBlank());
        String passwordEnClaro = passwordGenerada ? generarPasswordTemporal() : comando.adminPassword();
        String hash = passwordEncoder.encode(passwordEnClaro);
        // El nombre para mostrar (nombre_visible, Req 4/V57) no se captura en el
        // alta de plataforma; el admin_empresa puede definirlo despues (PUT /usuarios/{id}).
        Usuario admin = Usuario.crear(tenantId, identificadorAdmin, hash, null, Set.of(rolAdmin), actor);
        // Si la contrasena del admin fue TEMPORAL (generada), se obliga a cambiarla
        // en el primer inicio de sesion (V69).
        if (passwordGenerada) {
            admin.cambiarPasswordYForzarCambio(hash, actor);
        }
        Usuario adminGuardado = guardarAdminTraduciendoUnicidad(admin, identificadorAdmin);

        // 3) Contrato inicial que vincula la Empresa con su instrumento excluyente
        //    (Plan O Paquete de Suscripcion, Req 4.3). Si el comando trae un
        //    subconjunto de modulos (no null), se valida que sea subconjunto del
        //    instrumento (422 si no) y se fija como override en el Contrato antes
        //    de guardarlo; si es null, la Empresa hereda todos los modulos del
        //    instrumento (comportamiento historico, Req 25.4).
        LocalDate hoy = LocalDate.now(clock);
        Set<String> modulosElegidos = comando.modulosHabilitados();
        Suscripcion suscripcion;
        if (esPlan) {
            // Plan: Contrato ACTIVA sin fin de vigencia; override validado contra el Plan.
            suscripcion = Suscripcion.crearDePlan(tenantId, plan.getId(), hoy, null, actor);
            if (modulosElegidos != null) {
                ModulosPlanValidacion.exigirSubconjuntoDelPlan(modulosElegidos, plan);
                suscripcion.asignarModulos(modulosElegidos, actor);
            }
        } else if (comando.otorgarPrueba()) {
            // Suscripcion con prueba: Contrato EN_PRUEBA, vigenciaFin derivada de la
            // duracion de la prueba del Paquete; override validado contra el Paquete.
            suscripcion = Suscripcion.crearEnPrueba(
                    tenantId, paquete.getId(), hoy, paquete.getDuracionPruebaMeses(), actor);
            if (modulosElegidos != null) {
                ModulosPlanValidacion.exigirSubconjuntoDelPaquete(modulosElegidos, paquete);
                suscripcion.asignarModulos(modulosElegidos, actor);
            }
        } else {
            // Suscripcion sin prueba: Contrato ACTIVA con vigenciaFin = hoy + duracionDias.
            suscripcion = Suscripcion.crearDeSuscripcion(
                    tenantId, paquete.getId(), hoy, hoy.plusDays(paquete.getDuracionDias()), actor);
            if (modulosElegidos != null) {
                ModulosPlanValidacion.exigirSubconjuntoDelPaquete(modulosElegidos, paquete);
                suscripcion.asignarModulos(modulosElegidos, actor);
            }
        }
        suscripcionRepository.save(suscripcion);

        // 4) Auditoria de plataforma (Req 24.6): sin contrasena ni hash. Refleja el
        //    instrumento contratado (plan_id o paquete_suscripcion_id) y su estado.
        String detalleModulos = (modulosElegidos == null)
                ? "modulos=heredados_del_instrumento"
                : "modulos=" + suscripcion.getModulosHabilitados();
        String detalleInstrumento = esPlan
                ? "plan_id=" + plan.getId()
                : "paquete_suscripcion_id=" + paquete.getId();
        auditarPlataforma(actor, "crear",
                "creada empresa '" + empresaGuardada.getNombre() + "' (rfc=" + rfcNormalizado
                        + ", tenant_id=" + tenantId + "); giro='" + giro.getClave()
                        + "' (giro_id=" + giro.getId() + "); admin_empresa='" + identificadorAdmin
                        + "'; " + detalleInstrumento + "; suscripcion_id=" + suscripcion.getId()
                        + "; estado=" + suscripcion.getEstado().valorBd()
                        + "; " + detalleModulos);

        return new EmpresaCreadaDto(
                EmpresaDto.de(empresaGuardada),
                adminGuardado.getId(),
                adminGuardado.getIdentificadorAcceso(),
                passwordGenerada ? passwordEnClaro : null);
    }

    /**
     * Edita los datos de PLATAFORMA de una Empresa existente por parte del
     * {@code super_admin} (CHANGE 1): identidad ({@code nombre}), identificador
     * fiscal ({@code rfc}) y ficha descriptiva/de contacto completa. NO cambia el
     * {@code estado} (activar/suspender/cancelar tienen sus propios endpoints), ni
     * el {@code giro} (flujo dedicado {@code cambiarGiro}, Req 3), ni el
     * Plan/Suscripcion (flujos de monetizacion): esa reasignacion queda fuera de
     * alcance por diseno.
     *
     * <h2>Reglas</h2>
     * <ol>
     *   <li>La Empresa debe existir (404 si no, {@link RecursoNoEncontradoException}).</li>
     *   <li>El {@code rfc} se normaliza y valida estructuralmente con el MISMO
     *       {@link RfcValidador} del alta (422 si es invalido). Su unicidad se
     *       comprueba frente a OTRAS Empresas: adoptar el RFC de otra Empresa
     *       produce 409 ({@link ConflictoUnicidadException}); conservar el propio
     *       RFC no es conflicto.</li>
     *   <li>Se aplican los cambios via {@link Empresa#actualizarDatosPlataforma},
     *       se persiste (traduciendo carreras del indice unico a 409) y se audita
     *       la accion {@code actualizar} (Req 24.6).</li>
     * </ol>
     *
     * <p>La tabla {@code empresa} no lleva RLS (V2), por lo que el
     * {@code super_admin} opera en contexto de plataforma sin fijar
     * {@code app.current_tenant} (mismo patron que
     * {@link #activarEmpresa(UUID)}/{@link #cambiarGiro(UUID, UUID)}).</p>
     *
     * @param empresaId identificador de la Empresa a editar.
     * @param comando   nuevos datos (nombre, rfc y ficha descriptiva/de contacto).
     * @return el DTO actualizado de la Empresa (mismo detalle que GET/listar).
     * @throws RecursoNoEncontradoException si la Empresa no existe (404).
     * @throws ReglaNegocioException        si el nombre es vacio, el RFC es
     *                                      invalido o un campo excede su longitud (422).
     * @throws ConflictoUnicidadException   si el RFC pertenece a otra Empresa (409).
     */
    @Transactional
    public EmpresaDto actualizarEmpresa(UUID empresaId, ActualizarEmpresaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Empresa son obligatorios.");
        }
        Empresa empresa = cargar(empresaId);

        // Normaliza y valida el RFC (misma regla que el alta) ANTES de tocar la
        // BD; un RFC mal formado se rechaza con 422 sin consultar unicidad.
        String rfcNormalizado = RfcValidador.normalizarYValidar(comando.rfc());
        // Unicidad frente a OTRAS Empresas (conservar el propio RFC no es conflicto).
        if (empresaRepository.existsByRfcAndIdNot(rfcNormalizado, empresa.getId())) {
            throw new ConflictoUnicidadException(
                    "Ya existe una Empresa con el identificador fiscal '" + rfcNormalizado + "'.");
        }

        empresa.actualizarDatosPlataforma(
                comando.nombre(), rfcNormalizado, comando.datos(), actor);
        Empresa guardada = guardarEmpresaTraduciendoUnicidad(empresa, rfcNormalizado);

        auditarPlataforma(actor, "actualizar",
                "actualizada empresa '" + guardada.getNombre() + "' (rfc=" + guardada.getRfc()
                        + ", tenant_id=" + guardada.getId() + ")");

        return EmpresaDto.de(guardada);
    }

    /**
     * Edita el perfil de CONTACTO de la PROPIA Empresa del {@code admin_empresa}
     * autenticado (CHANGE 2). A diferencia de {@link #actualizarEmpresa}, es una
     * operacion de nivel <em>empresa</em>: el tenant se deriva SIEMPRE del comando
     * (que lo obtiene del contexto autenticado), nunca de la ruta ni del cuerpo, y
     * solo se pueden modificar los campos de contacto (nombre, correo, telefono,
     * direccion, logo). El {@code rfc}, el {@code giro}, el Plan y el
     * {@code estado} <strong>no</strong> son editables por esta via (no viajan en
     * el comando y el dominio no los toca).
     *
     * <h2>RLS (Req 23)</h2>
     * <p>Antes de leer/actualizar la Empresa se fija {@code app.current_tenant} al
     * tenant del contexto con {@link TenantSessionInitializer#applyTenant(UUID)},
     * mismo patron que las demas operaciones tenant-scoped. La Empresa ES el
     * tenant, por lo que se carga por {@code id == tenantId}. Una Empresa
     * inexistente para ese tenant produce 404.</p>
     *
     * @param tenantId identificador del tenant (Empresa) del {@code admin_empresa}
     *                 autenticado; derivado del contexto, nunca de la peticion.
     * @param comando  campos de contacto a aplicar (nombre obligatorio).
     * @return el DTO actualizado de la Empresa.
     * @throws RecursoNoEncontradoException si la Empresa del tenant no existe (404).
     * @throws ReglaNegocioException        si el nombre es vacio o un campo excede
     *                                      su longitud (422).
     */
    @Transactional
    public EmpresaDto actualizarMiEmpresa(UUID tenantId, ActualizarMiEmpresaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Empresa son obligatorios.");
        }
        // Fija app.current_tenant para operar bajo RLS sobre la propia Empresa.
        tenantSession.applyTenant(tenantId);
        Empresa empresa = cargarPorTenant(tenantId);

        empresa.actualizarPerfilContacto(
                comando.nombre(),
                comando.emailContacto(),
                comando.telefono(),
                comando.direccionCalle(),
                comando.direccionCiudad(),
                comando.direccionEstado(),
                comando.direccionCp(),
                comando.direccionPais(),
                comando.logo(),
                actor);
        Empresa guardada = empresaRepository.save(empresa);

        // Auditoria de empresa (tenant presente): accion 'actualizar' sobre el
        // recurso 'empresa', sin volcar el logotipo completo en el detalle.
        auditoria.registrar(EventoAuditoria.deTenant(
                tenantId, actor, "actualizar", RECURSO_EMPRESA,
                "actualizados datos de contacto de la propia empresa '" + guardada.getNombre()
                        + "' (tenant_id=" + tenantId + ")",
                null, null));

        return EmpresaDto.de(guardada);
    }

    /**
     * Consulta el detalle de la PROPIA Empresa del {@code admin_empresa}
     * autenticado (CHANGE 2), para que la interfaz precargue el formulario "Datos
     * de mi empresa". El tenant se deriva del contexto (parametro {@code tenantId}),
     * nunca de la peticion. Fija {@code app.current_tenant} para operar bajo RLS.
     *
     * @param tenantId identificador del tenant (Empresa) del contexto autenticado.
     * @return el DTO de la propia Empresa.
     * @throws RecursoNoEncontradoException si la Empresa del tenant no existe (404).
     */
    @Transactional(readOnly = true)
    public EmpresaDto consultarMiEmpresa(UUID tenantId) {
        tenantSession.applyTenant(tenantId);
        return EmpresaDto.de(cargarPorTenant(tenantId));
    }

    /**
     * Restablece la contrasena del {@code admin_empresa} de una Empresa por
     * parte del {@code super_admin} (CHANGE 3). Es una operacion de plataforma
     * sobre la cuenta de acceso de una Empresa; no expone dato de negocio alguno.
     *
     * <h2>Seleccion del Usuario destino</h2>
     * <p>Si {@code usuarioId} es nulo, se restablece el {@code admin_empresa} de
     * la Empresa localizado por su Rol; cuando existe mas de uno, se toma el
     * <strong>primero por {@code id}</strong> de forma determinista
     * ({@link UsuarioRepository#buscarPorTenantYRol(UUID, String)} ordena por
     * {@code id} ascendente). Si {@code usuarioId} viene informado, se restablece
     * ese Usuario concreto <em>validando que pertenece a la Empresa</em>
     * ({@code tenant_id}); si no pertenece o no existe, 404 (Req 23.3).</p>
     *
     * <h2>Contrasena</h2>
     * <p>Si se proporciona {@code passwordExplicita} (no nula/blanca) se fija esa
     * contrasena; en caso contrario se genera una temporal robusta que se
     * devuelve una unica vez (Req 11.3). La validacion de longitud de la
     * contrasena explicita ocurre en la capa web (8..255).</p>
     *
     * <h2>RLS (Req 23) y revocacion de sesiones (Req 68.4)</h2>
     * <p>Antes de cargar/actualizar la cuenta tenant-scoped se fija
     * {@code app.current_tenant} a la Empresa con
     * {@link TenantSessionInitializer#applyTenant(UUID)}, satisfaciendo la
     * politica RLS de UPDATE de {@code usuario} (V48/V53). Tras el cambio se
     * revocan las Sesiones vigentes del Usuario (motivo {@code CAMBIO_PASSWORD})
     * para que la contrasena anterior no pueda seguir emitiendo tokens.</p>
     *
     * @param empresaId         Empresa cuyo {@code admin_empresa} se restablece.
     * @param passwordExplicita contrasena explicita opcional; {@code null}/blanca
     *                          genera una temporal.
     * @param usuarioId         Usuario concreto a restablecer; {@code null}
     *                          selecciona el {@code admin_empresa} de la Empresa.
     * @return el resultado con el Usuario afectado y, si se genero, la contrasena
     *         temporal (una unica vez).
     * @throws RecursoNoEncontradoException si la Empresa, el Usuario indicado o el
     *                                      {@code admin_empresa} no existen (404).
     */
    @Transactional
    public ResetPasswordAdminDto restablecerPasswordAdmin(UUID empresaId,
                                                          String passwordExplicita,
                                                          UUID usuarioId) {
        String actor = actorActual();
        // La Empresa debe existir (404 si no). No requiere fijar tenant porque
        // la tabla empresa no tiene RLS (V2).
        Empresa empresa = cargar(empresaId);
        UUID tenantId = empresa.getId();

        // Fija app.current_tenant a la Empresa para poder LEER y ACTUALIZAR filas
        // tenant-scoped de usuario bajo las politicas RLS (V48/V53); el
        // super_admin opera en contexto de plataforma (sin tenant), por lo que se
        // fija explicitamente el tenant destino dentro de esta transaccion.
        tenantSession.applyTenant(tenantId);

        Usuario objetivo = resolverAdminObjetivo(tenantId, usuarioId);

        boolean passwordGenerada = (passwordExplicita == null || passwordExplicita.isBlank());
        String passwordEnClaro = passwordGenerada ? generarPasswordTemporal() : passwordExplicita;
        // Si la contrasena es TEMPORAL (generada por el Sistema), la cuenta
        // queda obligada a cambiarla en el proximo inicio de sesion (V69). Si el
        // super_admin fija una explicita, no se fuerza el cambio.
        String hashNuevo = passwordEncoder.encode(passwordEnClaro);
        if (passwordGenerada) {
            objetivo.cambiarPasswordYForzarCambio(hashNuevo, actor);
        } else {
            objetivo.cambiarPassword(hashNuevo, actor);
        }
        usuarioRepository.save(objetivo);

        // Revocacion de las sesiones vigentes del Usuario (Req 68.4): la
        // contrasena anterior deja de poder emitir nuevos Token_Acceso.
        int revocadas = registroSesiones.revocarTodasDeUsuario(
                objetivo.getId(), MotivoRevocacion.CAMBIO_PASSWORD);

        // Auditoria de plataforma (Req 24.6): sin la contrasena ni el hash.
        auditarPlataforma(actor, "reset_password_admin",
                "restablecida contrasena del admin_empresa '" + objetivo.getIdentificadorAcceso()
                        + "' (usuario_id=" + objetivo.getId() + ") de la empresa '" + empresa.getNombre()
                        + "' (tenant_id=" + tenantId + "); password_generada=" + passwordGenerada
                        + "; sesiones_revocadas=" + revocadas);

        return new ResetPasswordAdminDto(
                objetivo.getId(),
                objetivo.getIdentificadorAcceso(),
                passwordGenerada ? passwordEnClaro : null);
    }

    /**
     * Resuelve el Usuario {@code admin_empresa} objetivo del restablecimiento. Si
     * se indica {@code usuarioId}, valida que pertenezca a la Empresa (404 si no,
     * Req 23.3). Si no, localiza los {@code admin_empresa} de la Empresa y toma el
     * primero por {@code id} (determinista); si no hay ninguno, 404.
     */
    private Usuario resolverAdminObjetivo(UUID tenantId, UUID usuarioId) {
        if (usuarioId != null) {
            return usuarioRepository.findByIdAndTenantId(usuarioId, tenantId)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No se encontro el usuario indicado en la Empresa."));
        }
        List<Usuario> admins = usuarioRepository.buscarPorTenantYRol(tenantId, ROL_ADMIN_EMPRESA);
        if (admins.isEmpty()) {
            throw new RecursoNoEncontradoException(
                    "La Empresa no tiene un administrador de empresa para restablecer.");
        }
        // Primer admin_empresa por id (la consulta ya ordena por id ascendente).
        return admins.get(0);
    }

    /**
     * Cambia el Giro (vertical) de una Empresa existente como operacion
     * controlada (Req 3), preservando los datos de negocio ya generados bajo el
     * Giro original.
     *
     * <h2>Reglas (Req 3)</h2>
     * <ol>
     *   <li>La Empresa debe existir (404 si no, {@link RecursoNoEncontradoException}).</li>
     *   <li>El nuevo Giro debe existir y estar <strong>activo</strong> (422 si no,
     *       reutilizando {@link #validarGiroActivo(UUID)}, Req 3.1).</li>
     *   <li>Se resuelve la clave del Giro <strong>actual</strong> de la Empresa y
     *       se consulta {@link DatosVerticalPort#tieneDatosDeVertical(UUID, String)}:
     *       si la Empresa tiene datos del vertical actual, el cambio se rechaza
     *       con 422 ({@link ReglaNegocioException}, Req 3.2). Si no los tiene, el
     *       cambio se permite (Req 3.1).</li>
     *   <li>Se aplica {@link Empresa#cambiarGiro(UUID, String)}, se guarda y se
     *       audita el giro anterior y el nuevo (Req 3.3).</li>
     * </ol>
     *
     * <p>El {@code tenant_id} usado para consultar los datos del vertical es la
     * PK de la Empresa ({@link Empresa#getId()}); nunca se toma de la peticion
     * (Req 8.2). No se expone endpoint REST en esta tarea (el diseno no define un
     * contrato REST para el cambio de Giro): se implementa solo el servicio.</p>
     *
     * @param empresaId    identificador de la Empresa cuyo Giro se cambia.
     * @param nuevoGiroId  identificador del nuevo Giro (debe existir y estar activo).
     * @return el DTO de la Empresa con su nuevo Giro.
     * @throws RecursoNoEncontradoException si la Empresa no existe (404).
     * @throws ReglaNegocioException        si el nuevo Giro es nulo, no existe o
     *                                      esta inactivo (422, Req 3.1), o si la
     *                                      Empresa tiene datos del vertical de su
     *                                      Giro actual (422, Req 3.2).
     */
    @Transactional
    public EmpresaDto cambiarGiro(UUID empresaId, UUID nuevoGiroId) {
        String actor = actorActual();
        Empresa empresa = cargar(empresaId);

        // El nuevo Giro debe existir y estar activo (Req 3.1): misma regla que el
        // alta (422 si no), reutilizando validarGiroActivo.
        Giro nuevoGiro = validarGiroActivo(nuevoGiroId);

        // Giro ACTUAL de la Empresa: se resuelve su clave para (a) consultar la
        // existencia de datos del vertical actual y (b) auditar el giro anterior.
        UUID giroActualId = empresa.getGiroId();
        Giro giroActual = giroRepository.findById(giroActualId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el Giro actual de la Empresa."));

        // Req 3.2: no se puede cambiar el Giro si la Empresa tiene datos de
        // negocio del vertical de su Giro ACTUAL. Se consulta por la clave del
        // giro actual con el tenant_id (PK de la Empresa, Req 8.2).
        if (datosVertical.tieneDatosDeVertical(empresa.getId(), giroActual.getClave())) {
            throw new ReglaNegocioException(
                    "No se puede cambiar el Giro: existen datos del vertical actual.");
        }

        // Req 3.1: sin datos del vertical, se aplica el cambio de dominio.
        empresa.cambiarGiro(nuevoGiro.getId(), actor);
        Empresa guardada = empresaRepository.save(empresa);

        // Req 3.3: auditoria con actor, giro anterior y nuevo (claves e ids) y la
        // Empresa afectada (la marca temporal en UTC la fija el Servicio_Auditoria).
        auditarPlataforma(actor, "cambiar_giro",
                "cambiado giro de empresa '" + guardada.getNombre() + "' (tenant_id="
                        + guardada.getId() + "); giro anterior='" + giroActual.getClave()
                        + "' (giro_id=" + giroActual.getId() + ") -> giro nuevo='"
                        + nuevoGiro.getClave() + "' (giro_id=" + nuevoGiro.getId() + ")");

        return EmpresaDto.de(guardada);
    }

    /**
     * Activa una Empresa (Req 24.1).
     *
     * @param empresaId identificador de la Empresa.
     * @return el DTO de la Empresa activada.
     * @throws RecursoNoEncontradoException si la Empresa no existe.
     * @throws ReglaNegocioException        si la Empresa esta cancelada.
     */
    @Transactional
    public EmpresaDto activarEmpresa(UUID empresaId) {
        String actor = actorActual();
        Empresa empresa = cargar(empresaId);
        EstadoEmpresa anterior = empresa.getEstado();
        empresa.activar(actor);
        Empresa guardada = empresaRepository.save(empresa);
        auditarPlataforma(actor, "activar",
                "activada empresa '" + guardada.getNombre() + "' (tenant_id=" + guardada.getId()
                        + "); estado " + anterior.valorBd() + " -> " + guardada.getEstado().valorBd());
        return EmpresaDto.de(guardada);
    }

    /**
     * Suspende una Empresa (Req 24.4): impide el inicio de sesion de sus
     * Usuarios mientras dure la suspension.
     *
     * @param empresaId identificador de la Empresa.
     * @return el DTO de la Empresa suspendida.
     * @throws RecursoNoEncontradoException si la Empresa no existe.
     * @throws ReglaNegocioException        si la Empresa esta cancelada.
     */
    @Transactional
    public EmpresaDto suspenderEmpresa(UUID empresaId) {
        String actor = actorActual();
        Empresa empresa = cargar(empresaId);
        EstadoEmpresa anterior = empresa.getEstado();
        empresa.suspender(actor);
        Empresa guardada = empresaRepository.save(empresa);
        auditarPlataforma(actor, "suspender",
                "suspendida empresa '" + guardada.getNombre() + "' (tenant_id=" + guardada.getId()
                        + "); estado " + anterior.valorBd() + " -> " + guardada.getEstado().valorBd());
        return EmpresaDto.de(guardada);
    }

    /**
     * Consulta puntual de una Empresa (Req 24.1), enriquecida con su
     * <strong>plan vigente</strong> (Req 4.3, 4.7, 4.8, 4.10).
     *
     * <p>La Empresa se carga sin fijar tenant (la tabla {@code empresa} no lleva
     * RLS, V2). Para leer sus Suscripciones -tabla tenant-scoped por RLS FORCE
     * con la politica {@code tenant_isolation} y un rol de aplicacion
     * {@code NOBYPASSRLS}- se fija {@code app.current_tenant} a ESA Empresa con
     * {@link TenantSessionInitializer#applyTenant(UUID)} dentro de la misma
     * transaccion (mismo patron probado que {@link #restablecerPasswordAdmin}
     * y {@link #consultarMiEmpresa}). Sin fijar el tenant, la politica RLS se
     * evalua a NULL y NINGUNA Suscripcion seria visible (deny-by-default), por lo
     * que fijar el tenant de la propia Empresa es imprescindible y NO debilita la
     * seguridad: solo se ven las Suscripciones de ese unico tenant. El
     * {@code SET LOCAL} funciona bajo una transaccion de solo lectura.</p>
     *
     * <p>Sobre las Suscripciones de la Empresa se aplica la regla unica de
     * {@link #suscripcionVigente(List)} (Req 3/4.4). El NOMBRE del plan vigente
     * lo resuelve el backend (patron NO-UUID, Req 4.8) con
     * {@code planRepository.findById}. Si la Empresa no tiene Suscripcion, el
     * {@code planVigente} del DTO es {@code null} (ausencia inequivoca, Req 4.3).</p>
     *
     * @param empresaId identificador de la Empresa.
     * @return el DTO de la Empresa con su plan vigente (o {@code null} si no tiene).
     * @throws RecursoNoEncontradoException si la Empresa no existe.
     */
    @Transactional(readOnly = true)
    public EmpresaDto consultarEmpresa(UUID empresaId) {
        Empresa empresa = cargar(empresaId);

        // La lectura de suscripcion es tenant-scoped por RLS: se fija el tenant
        // de ESTA Empresa para poder verla (deny-by-default sin tenant fijado).
        tenantSession.applyTenant(empresa.getId());
        List<Suscripcion> deLaEmpresa = suscripcionRepository.findByTenantIdOrderByIdAsc(empresa.getId());

        EmpresaDto.PlanVigenteDto planVigente = suscripcionVigente(deLaEmpresa)
                .map(this::aPlanVigenteDto)
                .orElse(null);

        return EmpresaDto.de(empresa, planVigente);
    }

    /**
     * Listado paginado de Empresas, opcionalmente filtrado por estado y por una
     * busqueda textual (Req 24.5, Req 24).
     *
     * <p>La busqueda {@code q} es opcional: si es {@code null} o en blanco se
     * comporta como el listado historico (todas, o filtradas por estado). Cuando
     * hay texto, se buscan Empresas cuyo nombre, RFC o nombre comercial contengan
     * {@code q} sin distinguir mayusculas/minusculas, combinable con el estado.</p>
     *
     * <p>Devuelve la pagina de <em>entidades</em> {@link Empresa} sin enriquecer.
     * El listado enriquecido con el plan vigente lo expone
     * {@link #listarEmpresasDto(EstadoEmpresa, String, Pageable)}; este metodo se
     * conserva como paso interno reutilizado por aquel (Req 12.5).</p>
     *
     * @param estado   estado por el que filtrar; {@code null} lista todas.
     * @param q        texto de busqueda opcional; {@code null}/blanco lo ignora.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Empresas (entidades).
     */
    @Transactional(readOnly = true)
    public Page<Empresa> listarEmpresas(EstadoEmpresa estado, String q, Pageable pageable) {
        String texto = (q == null) ? null : q.strip();
        boolean conBusqueda = texto != null && !texto.isEmpty();
        if (conBusqueda) {
            return (estado == null)
                    ? empresaRepository.buscar(texto, pageable)
                    : empresaRepository.buscarPorEstado(estado, texto, pageable);
        }
        return (estado == null)
                ? empresaRepository.findAll(pageable)
                : empresaRepository.findByEstado(estado, pageable);
    }

    /**
     * Listado paginado de Empresas <strong>enriquecido con el plan vigente</strong>
     * de cada Empresa (Req 4.5, 4.6, 4.11), fuente de verdad para la columna
     * "Plan" del super_admin.
     *
     * <p>Reutiliza {@link #listarEmpresas(EstadoEmpresa, String, Pageable)} para
     * obtener la pagina de Empresas y luego resuelve, por cada Empresa, su
     * suscripcion vigente y el nombre de su plan, mapeando cada {@link Empresa} a
     * un {@link EmpresaDto} enriquecido (o con {@code planVigente == null} si la
     * Empresa no tiene Suscripcion vigente, Req 4.3).</p>
     *
     * <h2>Estrategia frente a la RLS (Req 4.11 y riesgo R3)</h2>
     * <p>El diseno original planteaba un anti-N+1 estricto resolviendo TODAS las
     * suscripciones de la pagina con una unica consulta por lote
     * ({@code SuscripcionRepository.findByTenantIdInOrderByTenantIdAscCreatedAtDesc}).
     * Ese enfoque <strong>no es aplicable</strong> aqui: la tabla
     * {@code suscripcion} tiene RLS FORCE con la politica {@code tenant_isolation}
     * ({@code tenant_id = current_setting('app.current_tenant', true)::uuid}) y el
     * rol de la aplicacion es {@code NOBYPASSRLS}. El super_admin opera en
     * PLATAFORMA sin fijar {@code app.current_tenant}; PostgreSQL aplica el filtro
     * RLS <em>antes</em> del {@code WHERE ... IN (...)}, por lo que una consulta
     * por lote (derivada o JPQL) devolveria SIEMPRE vacio (deny-by-default). JPQL
     * no evita RLS; un bypass exigiria debilitar la seguridad y NO se hace.</p>
     *
     * <p>Por eso se resuelve la suscripcion vigente <strong>fijando el tenant de
     * cada Empresa</strong> ({@link TenantSessionInitializer#applyTenant(UUID)})
     * dentro de la MISMA transaccion de solo lectura y leyendo sus suscripciones
     * con {@code findByTenantIdOrderByIdAsc}. Esto es coherente con el patron de
     * seguridad ya probado del proyecto (crearEmpresa, restablecerPasswordAdmin,
     * consultarMiEmpresa) y no debilita la RLS: cada lectura ve exclusivamente las
     * suscripciones de un unico tenant. El coste es de N consultas de suscripcion
     * pero <strong>acotado al tamano de la PAGINA</strong> (maximo 100), no a toda
     * la tabla ni al total de Empresas; el {@code SET LOCAL} por iteracion es
     * despreciable para 20/100 filas.</p>
     *
     * <p>El nombre del plan (patron NO-UUID, Req 4.8) SI se resuelve por lote con
     * <strong>una unica</strong> {@code planRepository.findAllById(planIds)}: la
     * tabla {@code plan} no lleva RLS, de modo que el total de consultas del
     * enriquecimiento es {@code 1 (pagina) + N (suscripciones, N<=tamano pagina)
     * + 1 (planes)}, independiente del total de Empresas del sistema.</p>
     *
     * @param estado   estado por el que filtrar; {@code null} lista todas.
     * @param q        texto de busqueda opcional; {@code null}/blanco lo ignora.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de {@link EmpresaDto} enriquecidos con el plan vigente.
     */
    @Transactional(readOnly = true)
    public Page<EmpresaDto> listarEmpresasDto(EstadoEmpresa estado, String q, Pageable pageable) {
        Page<Empresa> pagina = listarEmpresas(estado, q, pageable);

        // 1) Suscripcion vigente por Empresa. Se fija el tenant de cada Empresa
        //    para poder LEER sus suscripciones bajo RLS (deny-by-default sin
        //    tenant); N acotado al tamano de la pagina (<=100), nunca al total.
        Map<UUID, Suscripcion> vigentePorTenant = new java.util.HashMap<>();
        for (Empresa empresa : pagina.getContent()) {
            UUID tenantId = empresa.getId();
            tenantSession.applyTenant(tenantId);
            suscripcionVigente(suscripcionRepository.findByTenantIdOrderByIdAsc(tenantId))
                    .ifPresent(vigente -> vigentePorTenant.put(tenantId, vigente));
        }

        // 2) Nombre del INSTRUMENTO por LOTE (patron NO-UUID, Req 4.8, 12.6),
        //    separando los Contratos vigentes por tipo: los de tipo PLAN resuelven
        //    su nombre con planRepository.findAllById(planIds); los de tipo
        //    SUSCRIPCION con paqueteSuscripcionRepository.findAllById(paqueteIds).
        //    Ambas tablas de catalogo carecen de RLS, por lo que findAllById es
        //    visible; el total de consultas del enriquecimiento sigue acotado.
        Set<UUID> planIds = vigentePorTenant.values().stream()
                .filter(s -> s.getTipoInstrumento() == TipoInstrumento.PLAN)
                .map(Suscripcion::getPlanId)
                .collect(Collectors.toSet());
        Set<UUID> paqueteIds = vigentePorTenant.values().stream()
                .filter(s -> s.getTipoInstrumento() == TipoInstrumento.SUSCRIPCION)
                .map(Suscripcion::getPaqueteSuscripcionId)
                .collect(Collectors.toSet());
        Map<UUID, String> nombrePorPlan = planIds.isEmpty()
                ? Map.of()
                : planRepository.findAllById(planIds).stream()
                        .collect(Collectors.toMap(Plan::getId, Plan::getNombre));
        Map<UUID, String> nombrePorPaquete = paqueteIds.isEmpty()
                ? Map.of()
                : paqueteSuscripcionRepository.findAllById(paqueteIds).stream()
                        .collect(Collectors.toMap(PaqueteSuscripcion::getId, PaqueteSuscripcion::getNombre));

        // 3) Proyeccion enriquecida por fila (planVigente o null, Req 4.3). El
        //    nombre del instrumento se toma del mapa correspondiente a su tipo.
        return pagina.map(empresa -> {
            Suscripcion vigente = vigentePorTenant.get(empresa.getId());
            EmpresaDto.PlanVigenteDto planVigente = (vigente == null)
                    ? null
                    : aPlanVigenteDto(vigente, nombreInstrumentoDe(vigente, nombrePorPlan, nombrePorPaquete));
            return EmpresaDto.de(empresa, planVigente);
        });
    }

    /**
     * Resuelve el nombre del instrumento vigente a partir de los mapas resueltos
     * por lote, segun el {@link TipoInstrumento} del Contrato: para PLAN toma el
     * nombre del Plan por {@code planId}; para SUSCRIPCION el del Paquete por
     * {@code paqueteSuscripcionId}. Devuelve {@code null} si el instrumento no se
     * encontro (caso anomalo/borrado), sin romper la proyeccion.
     */
    private static String nombreInstrumentoDe(Suscripcion vigente,
                                              Map<UUID, String> nombrePorPlan,
                                              Map<UUID, String> nombrePorPaquete) {
        return (vigente.getTipoInstrumento() == TipoInstrumento.PLAN)
                ? nombrePorPlan.get(vigente.getPlanId())
                : nombrePorPaquete.get(vigente.getPaqueteSuscripcionId());
    }

    /**
     * Regla UNICA de "suscripcion vigente" de una Empresa (Req 3.1-3.4, 4.4, 4.5),
     * fuente de verdad compartida por {@link #consultarEmpresa(UUID)} y
     * {@link #listarEmpresasDto(EstadoEmpresa, String, Pageable)}.
     *
     * <p>Selecciona, de las Suscripciones de una Empresa:</p>
     * <ol>
     *   <li>la que este en estado {@link EstadoSuscripcion#ACTIVA} (Req 3.1); si
     *       hubiera mas de una activa -situacion anomala- se toma la primera de
     *       forma estable segun el orden de la lista recibida;</li>
     *   <li>si no hay ninguna activa, la de {@code vigenciaInicio} mas reciente
     *       (Req 3.2);</li>
     *   <li>en caso de empate en {@code vigenciaInicio}, la de {@code createdAt}
     *       mas reciente como desempate (Req 3.3);</li>
     *   <li>si la Empresa no tiene ninguna Suscripcion, {@link Optional#empty()}
     *       (ausencia de plan vigente, Req 3.4).</li>
     * </ol>
     *
     * @param deLaEmpresa Suscripciones de una misma Empresa (puede estar vacia);
     *                    no {@code null}.
     * @return la Suscripcion vigente segun la regla, o {@link Optional#empty()} si
     *         la lista esta vacia.
     */
    static Optional<Suscripcion> suscripcionVigente(List<Suscripcion> deLaEmpresa) {
        return deLaEmpresa.stream()
                .filter(ServicioEmpresas::otorgaAcceso)
                .findFirst()
                .or(() -> deLaEmpresa.stream()
                        .max(Comparator.comparing(Suscripcion::getVigenciaInicio)
                                .thenComparing(Suscripcion::getCreatedAt)));
    }

    /**
     * Indica si el estado de un Contrato lo hace "vigente preferente" para la
     * regla de {@link #suscripcionVigente(List)}: tanto {@link EstadoSuscripcion#ACTIVA}
     * como {@link EstadoSuscripcion#EN_PRUEBA} otorgan acceso (V64, Req 1.4), por
     * lo que ambos se consideran candidatos preferentes por igual antes de
     * recurrir al desempate por vigencia/creacion.
     */
    private static boolean otorgaAcceso(Suscripcion suscripcion) {
        EstadoSuscripcion estado = suscripcion.getEstado();
        return estado == EstadoSuscripcion.ACTIVA || estado == EstadoSuscripcion.EN_PRUEBA;
    }

    /**
     * Construye el {@link EmpresaDto.PlanVigenteDto} de un Contrato vigente
     * resolviendo el nombre de su instrumento de forma INDIVIDUAL (patron NO-UUID,
     * Req 4.8): segun el {@link TipoInstrumento} usa {@code planRepository.findById}
     * (PLAN) o {@code paqueteSuscripcionRepository.findById} (SUSCRIPCION). Uso en
     * la consulta puntual {@link #consultarEmpresa(UUID)}.
     */
    private EmpresaDto.PlanVigenteDto aPlanVigenteDto(Suscripcion vigente) {
        String nombreInstrumento = (vigente.getTipoInstrumento() == TipoInstrumento.PLAN)
                ? planRepository.findById(vigente.getPlanId()).map(Plan::getNombre).orElse(null)
                : paqueteSuscripcionRepository.findById(vigente.getPaqueteSuscripcionId())
                        .map(PaqueteSuscripcion::getNombre).orElse(null);
        return aPlanVigenteDto(vigente, nombreInstrumento);
    }

    /**
     * Construye el {@link EmpresaDto.PlanVigenteDto} de un Contrato vigente con el
     * nombre del instrumento ya resuelto (usado por el listado, donde los nombres
     * se cargan por lote). Calcula el enriquecimiento derivado del Contrato:
     * <ul>
     *   <li>{@code tipoInstrumento} y los identificadores ({@code planId},
     *       {@code paqueteSuscripcionId}, {@code suscripcionId}) para las acciones
     *       del frontend, sin exponerse como UUID al usuario;</li>
     *   <li>{@code diasRestantes}: dias hasta {@code vigenciaFin} desde hoy, o
     *       {@code null} si el Contrato no tiene fin de vigencia;</li>
     *   <li>{@code enPrueba}: {@code true} si el estado es {@code EN_PRUEBA};</li>
     *   <li>{@code vencida}: derivada via {@link Suscripcion#estaVencida(LocalDate)};</li>
     *   <li>{@code porVencer}: {@code true} si hay fin de vigencia, aun no ha
     *       pasado ({@code diasRestantes >= 0}) y esta dentro del umbral de aviso
     *       configurado ({@link ContratacionProperties#umbralAvisoDias()}).</li>
     * </ul>
     * El {@code nombrePlan} se conserva con el nombre del instrumento por
     * compatibilidad (D7). Todo se calcula con {@code hoy = LocalDate.now(clock)}.
     */
    private EmpresaDto.PlanVigenteDto aPlanVigenteDto(Suscripcion vigente, String nombreInstrumento) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate vigenciaFin = vigente.getVigenciaFin();
        Integer diasRestantes = (vigenciaFin == null)
                ? null
                : (int) ChronoUnit.DAYS.between(hoy, vigenciaFin);
        boolean enPrueba = vigente.getEstado() == EstadoSuscripcion.EN_PRUEBA;
        boolean vencida = vigente.estaVencida(hoy);
        boolean porVencer = vigenciaFin != null
                && diasRestantes != null
                && diasRestantes >= 0
                && diasRestantes <= contratacionProperties.umbralAvisoDias();
        return new EmpresaDto.PlanVigenteDto(
                nombreInstrumento,
                vigente.getEstado(),
                vigente.getPlanId(),
                vigente.getId(),
                vigente.getVigenciaInicio(),
                vigenciaFin,
                vigente.getTipoInstrumento(),
                nombreInstrumento,
                vigente.getPaqueteSuscripcionId(),
                diasRestantes,
                enPrueba,
                vencida,
                porVencer);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Empresa cargar(UUID empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Empresa solicitada."));
    }

    /**
     * Carga la PROPIA Empresa de un tenant (CHANGE 2). Como la Empresa ES el
     * tenant, su PK coincide con el {@code tenantId}; una ausencia produce 404 sin
     * revelar si el tenant existe (Req 23.3).
     */
    private Empresa cargarPorTenant(UUID tenantId) {
        return empresaRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Empresa del contexto actual."));
    }

    /**
     * Valida que el Giro indicado en el alta sea obligatorio, exista y este
     * activo (Req 2.1/2.2). Cualquier incumplimiento se traduce a 422
     * ({@link ReglaNegocioException}): el Giro es un requisito de negocio del
     * alta y no un recurso navegable de plataforma. Un Giro inexistente o
     * inactivo comparten mensaje para no filtrar la existencia de claves de
     * plataforma.
     *
     * @param giroId identificador del Giro seleccionado en el alta.
     * @return el Giro activo validado, para ligar la Empresa y auditar el alta.
     * @throws ReglaNegocioException si el Giro es nulo, no existe o esta inactivo.
     */
    private Giro validarGiroActivo(UUID giroId) {
        if (giroId == null) {
            throw new ReglaNegocioException("El Giro es obligatorio.");
        }
        Giro giro = giroRepository.findById(giroId)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El Giro indicado no existe o no está activo."));
        if (!giro.isActivo()) {
            throw new ReglaNegocioException("El Giro indicado no existe o no está activo.");
        }
        return giro;
    }

    private static void validarComando(CrearEmpresaCommand comando) {
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Empresa son obligatorios.");
        }
        if (comando.nombre() == null || comando.nombre().isBlank()) {
            throw new ReglaNegocioException("El nombre de la Empresa es obligatorio.");
        }
        if (comando.rfc() == null || comando.rfc().isBlank()) {
            throw new ReglaNegocioException("El identificador fiscal (RFC) es obligatorio.");
        }
        // Instrumento excluyente (Req 4.3): exactamente uno de plan/paquete no nulo.
        boolean tienePlan = comando.planId() != null;
        boolean tienePaquete = comando.paqueteSuscripcionId() != null;
        if (tienePlan == tienePaquete) {
            throw new ReglaNegocioException(
                    "Debe indicarse un Plan o una Suscripción, y solo uno.");
        }
        if (comando.adminIdentificador() == null || comando.adminIdentificador().isBlank()) {
            throw new ReglaNegocioException(
                    "El identificador del administrador de empresa es obligatorio.");
        }
    }

    /**
     * Genera una contrasena temporal aleatoria y robusta (base64 url-safe sin
     * relleno) para el primer {@code admin_empresa} cuando no se proporciona.
     * Nunca se persiste ni se audita en claro; se devuelve una unica vez en la
     * respuesta del alta (Req 11.3).
     */
    private String generarPasswordTemporal() {
        byte[] bytes = new byte[BYTES_PASSWORD_TEMPORAL];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Empresa guardarEmpresaTraduciendoUnicidad(Empresa empresa, String rfc) {
        try {
            return empresaRepository.saveAndFlush(empresa);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente contra el indice unico uq_empresa_rfc (V8).
            throw new ConflictoUnicidadException(
                    "Ya existe una Empresa con el identificador fiscal '" + rfc + "'.");
        }
    }

    private Usuario guardarAdminTraduciendoUnicidad(Usuario admin, String identificador) {
        try {
            return usuarioRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente contra uq_usuario_identificador_acceso (V1).
            throw new ConflictoUnicidadException(
                    "Ya existe un usuario con el identificador de acceso '" + identificador + "'.");
        }
    }

    private void auditarPlataforma(String actor, String accion, String detalle) {
        auditoria.registrar(
                EventoAuditoria.dePlataforma(actor, accion, RECURSO_EMPRESA, detalle, null, null));
    }

    /**
     * Resuelve el identificador del actor autenticado (super_admin) para la
     * auditoria; si no hay contexto de seguridad, usa "sistema".
     */
    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
