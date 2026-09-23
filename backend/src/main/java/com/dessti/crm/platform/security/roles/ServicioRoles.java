package com.dessti.crm.platform.security.roles;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.security.rbac.GiroEmpresaPort;
import com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el modelo de roles (Req 27, 28):
 * gestiona los {@code Rol_Personalizado} de una Empresa y protege la
 * inmutabilidad de los roles predefinidos del Sistema.
 *
 * <h2>Reglas de negocio implementadas</h2>
 * <ul>
 *   <li><strong>Creacion de Rol_Personalizado (Req 28.2, 28.4):</strong>
 *       combina permisos <em>existentes</em> dentro del {@code tenant_id} de la
 *       Empresa derivado del contexto autenticado (nunca de la peticion,
 *       Req 23.4).</li>
 *   <li><strong>Rechazo de permisos invalidos (Req 28.5):</strong> si algun
 *       permiso solicitado no existe en el catalogo, o corresponde a operaciones
 *       de nivel plataforma ({@code super_admin}: {@code empresa}, {@code plan},
 *       {@code suscripcion}, {@code offboarding}), se rechaza la creacion
 *       informando el/los permiso(s) invalido(s) ({@link ReglaNegocioException}
 *       -&gt; HTTP 422).</li>
 *   <li><strong>RBAC consciente del Giro (Req 7.2, 7.4):</strong> ademas, se
 *       rechaza ({@link ReglaNegocioException} -&gt; HTTP 422) todo permiso cuyo
 *       recurso pertenezca a un <em>vertical ajeno</em> al Giro de la Empresa del
 *       contexto (resuelto via {@link GiroEmpresaPort}); los permisos de Nucleo o
 *       del vertical del propio Giro son aceptables. La clasificacion
 *       recurso&rarr;Giro la aporta {@link ClasificadorRecursosVertical}. El
 *       filtrado de <em>oferta</em> de permisos aplicables al Giro (Req 7.3) se
 *       aplicara donde se listen los permisos asignables; esta validacion dura al
 *       crear/actualizar el rol es la garantia de que nunca se persista un rol con
 *       permisos de otro Giro. El gating efectivo de los roles predefinidos de un
 *       vertical (Req 7.5) lo impone el gating por Giro del {@code Autorizador}
 *       (tarea 6.1), sin duplicarse aqui.</li>
 *   <li><strong>Unicidad por tenant (Req 28.2):</strong> el nombre del rol es
 *       unico dentro de la Empresa; el conflicto se traduce a
 *       {@link ConflictoUnicidadException} (HTTP 409), tanto por comprobacion
 *       previa como por violacion del indice parcial
 *       {@code uq_rol_nombre_por_tenant} de la BD.</li>
 *   <li><strong>Inmutabilidad de predefinidos (Req 28.6):</strong> cualquier
 *       intento de modificar o eliminar un rol {@code predefinido = true} lanza
 *       {@link RolPredefinidoInmutableException} (HTTP 403).</li>
 *   <li><strong>Auditoria (Req 28.7):</strong> cada creacion, modificacion o
 *       eliminacion se registra via {@link AuditoriaPort} con actor, accion,
 *       recurso y marca temporal (UTC la fija el servicio de auditoria).</li>
 * </ul>
 */
@Service
public class ServicioRoles {

    /** Recurso de auditoria/RBAC asociado a la gestion de roles. */
    static final String RECURSO_ROL = "rol";

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final AuditoriaPort auditoria;
    private final ClasificadorRecursosVertical clasificadorVertical;
    private final GiroEmpresaPort giroEmpresaPort;
    private final ModulosHabilitadosPort modulosHabilitados;

    public ServicioRoles(RolRepository rolRepository,
                         PermisoRepository permisoRepository,
                         AuditoriaPort auditoria,
                         ClasificadorRecursosVertical clasificadorVertical,
                         GiroEmpresaPort giroEmpresaPort,
                         ModulosHabilitadosPort modulosHabilitados) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.auditoria = auditoria;
        this.clasificadorVertical = clasificadorVertical;
        this.giroEmpresaPort = giroEmpresaPort;
        this.modulosHabilitados = modulosHabilitados;
    }

    /**
     * Crea un {@code Rol_Personalizado} para la Empresa del contexto autenticado
     * (Req 28.2).
     *
     * @param comando datos del rol (nombre y permisos a combinar).
     * @return el identificador del rol creado.
     * @throws ReglaNegocioException      si algun permiso no existe o es de
     *                                    plataforma (Req 28.5).
     * @throws ConflictoUnicidadException si ya existe un rol con ese nombre en la
     *                                    Empresa (Req 28.2).
     */
    @Transactional
    public UUID crearRolPersonalizado(CrearRolPersonalizadoCommand comando) {
        UUID tenantId = TenantContext.require();
        String actor = actorActual();
        String nombre = normalizarNombre(comando.nombre());

        Set<PermisoEntity> permisos = resolverPermisosValidos(tenantId, comando.permisoIds());

        // Unicidad por tenant: comprobacion previa para un mensaje claro (Req 28.2).
        if (rolRepository.existsByTenantIdAndNombre(tenantId, nombre)) {
            throw new ConflictoUnicidadException(
                    "Ya existe un rol con el nombre '" + nombre + "' en esta Empresa.");
        }

        Rol rol = Rol.personalizado(tenantId, nombre, permisos, actor);
        Rol guardado = guardarTraduciendoUnicidad(rol, nombre);

        auditar(tenantId, actor, "crear",
                "creado rol personalizado '" + nombre + "' con " + permisos.size() + " permiso(s)");
        return guardado.getId();
    }

    /**
     * Reemplaza los permisos de un {@code Rol_Personalizado} de la Empresa
     * (Req 28). Rechaza los roles predefinidos (Req 28.6) y los permisos
     * invalidos (Req 28.5).
     *
     * @param rolId       identificador del rol a modificar.
     * @param permisoIds  nuevos permisos a combinar.
     * @throws RolPredefinidoInmutableException si el rol es predefinido.
     * @throws RecursoNoEncontradoException     si el rol no existe en la Empresa.
     * @throws ReglaNegocioException            si algun permiso es invalido.
     */
    @Transactional
    public void actualizarPermisos(UUID rolId, Set<UUID> permisoIds) {
        UUID tenantId = TenantContext.require();
        String actor = actorActual();

        Rol rol = cargarRolGestionable(rolId, tenantId);
        Set<PermisoEntity> permisos = resolverPermisosValidos(tenantId, permisoIds);
        rol.reemplazarPermisos(permisos, actor);
        rolRepository.save(rol);

        auditar(tenantId, actor, "actualizar",
                "actualizados permisos del rol '" + rol.getNombre()
                        + "' (" + permisos.size() + " permiso(s))");
    }

    /**
     * Elimina un {@code Rol_Personalizado} de la Empresa (Req 28). Rechaza los
     * roles predefinidos (Req 28.6).
     *
     * @param rolId identificador del rol a eliminar.
     * @throws RolPredefinidoInmutableException si el rol es predefinido.
     * @throws RecursoNoEncontradoException     si el rol no existe en la Empresa.
     */
    @Transactional
    public void eliminarRolPersonalizado(UUID rolId) {
        UUID tenantId = TenantContext.require();
        String actor = actorActual();

        Rol rol = cargarRolGestionable(rolId, tenantId);
        String nombre = rol.getNombre();
        rolRepository.delete(rol);

        auditar(tenantId, actor, "eliminar", "eliminado rol personalizado '" + nombre + "'");
    }

    /**
     * Lista los Roles PREDEFINIDOS que el {@code admin_empresa} de la Empresa del
     * contexto puede asignar (plataforma-multigiro): los roles transversales de
     * administracion/direccion (siempre) mas los roles de modulo cuyo modulo
     * requerido esta contratado por la Empresa. El rol de plataforma
     * {@code super_admin} nunca se ofrece.
     *
     * <p>La relacion rol&rarr;modulo proviene de {@link RolModuloCatalogo} (fuente
     * unica de verdad) y los modulos contratados de
     * {@link ModulosHabilitadosPort#modulosHabilitadosDe(java.util.UUID)} (la
     * misma fuente que alimenta el claim {@code modulos} del JWT), resuelta para
     * el tenant del contexto (Req 23.4, nunca de la peticion). El resultado se
     * ordena con los roles transversales primero y los de modulo despues, por
     * nombre ascendente.</p>
     *
     * @return los roles asignables por la Empresa, con su modulo representativo
     *         ({@code null} para los transversales) y una breve descripcion.
     */
    @Transactional(readOnly = true)
    public List<RolAsignableDto> listarRolesAsignables() {
        UUID tenantId = TenantContext.require();
        List<String> contratados = modulosHabilitados.modulosHabilitadosDe(tenantId);

        List<RolAsignableDto> transversales = new ArrayList<>();
        List<RolAsignableDto> deModulo = new ArrayList<>();

        for (RolModuloCatalogo entrada : RolModuloCatalogo.todos()) {
            if (!entrada.esAsignableCon(contratados)) {
                continue;
            }
            // Se resuelve el Rol predefinido real (id + nombre) por su nombre
            // (tenant_id NULL). Si por cualquier anomalia de siembra no existiera,
            // se omite en lugar de fallar (la oferta es best-effort).
            Rol rol = rolRepository.findByNombreAndTenantIdIsNull(entrada.nombreRol()).orElse(null);
            if (rol == null) {
                continue;
            }
            String moduloRepresentativo = entrada.requiereModulo()
                    ? moduloRepresentativo(entrada, contratados)
                    : null;
            RolAsignableDto dto = new RolAsignableDto(
                    rol.getId(), rol.getNombre(), moduloRepresentativo, descripcionDe(entrada));
            if (entrada.requiereModulo()) {
                deModulo.add(dto);
            } else {
                transversales.add(dto);
            }
        }

        // Transversales primero (por nombre), luego los de modulo (por nombre).
        transversales.sort((a, b) -> a.nombre().compareToIgnoreCase(b.nombre()));
        deModulo.sort((a, b) -> a.nombre().compareToIgnoreCase(b.nombre()));
        List<RolAsignableDto> resultado = new ArrayList<>(transversales.size() + deModulo.size());
        resultado.addAll(transversales);
        resultado.addAll(deModulo);
        return resultado;
    }

    /**
     * Elige el modulo REPRESENTATIVO a exponer para un rol de modulo: el primero
     * (orden alfabetico estable) de sus modulos requeridos que el tenant contrato;
     * si por alguna razon ninguno coincidiera, el primero declarado del rol.
     */
    private static String moduloRepresentativo(RolModuloCatalogo entrada, List<String> contratados) {
        return entrada.modulos().stream()
                .filter(m -> contratados != null && contratados.stream()
                        .anyMatch(c -> c != null && c.strip().equalsIgnoreCase(m)))
                .sorted()
                .findFirst()
                .orElseGet(() -> entrada.modulos().stream().sorted().findFirst().orElse(null));
    }

    /** Descripcion breve y estable del alcance de cada rol predefinido de empresa. */
    private static String descripcionDe(RolModuloCatalogo entrada) {
        return switch (entrada) {
            case ADMIN_EMPRESA -> "Administracion de la Empresa: usuarios, roles, sesiones y branding.";
            case DIRECTOR -> "Direccion general: lectura transversal, estrategia, presupuestos y BI (aprueba estrategia).";
            case GERENTE -> "Direccion: lectura transversal, reportes y aprobaciones.";
            case SUPERVISOR -> "Supervision: lectura acotada de la operacion.";
            case VENTAS -> "Comercial: clientes, oportunidades y cotizaciones (sin aprobar).";
            case DISENO -> "Diseno: pruebas de diseno de la operacion (sin aprobar).";
            case PRODUCCION -> "Produccion: ordenes de fabricacion (sin aprobar).";
            case ALMACEN -> "Almacen: inventario, compras, recepciones y kardex (sin aprobar compras).";
            case INSTALACION -> "Instalacion: levantamientos, permisos y ordenes de trabajo (sin aprobar).";
            case MANTENIMIENTO -> "Mantenimiento: contratos y tickets de servicio (sin aprobar).";
            case CONTABILIDAD -> "Contabilidad y finanzas: registra facturas, polizas y pagos (sin aprobar ni aplicar pago).";
            case RH -> "RH y nomina: empleados, contratos, incidencias y prepara la nomina (sin aprobar).";
            case MARKETING -> "Marketing: redes sociales, publicaciones y campanas (sin publicar/aprobar).";
            case CALIDAD -> "Calidad (SGC ISO 9001): quejas, no conformidades, acciones y riesgos (sin aprobar).";
            case GERENTE_COMERCIAL -> "Gerencia comercial: aprueba cotizaciones y oportunidades del pipeline.";
            case GERENTE_COMPRAS -> "Gerencia de compras: aprueba requisiciones y ordenes de compra.";
            case CONTADOR_GENERAL -> "Contralor: aprueba facturas y notas de credito y registra polizas.";
            case TESORERO -> "Tesoreria: autoriza y aplica pagos, concilia y gestiona cuentas bancarias.";
            case GERENTE_RH -> "Gerencia de RH: aprueba la nomina preparada por el area de RH.";
            case GERENTE_OPERACIONES -> "Gerencia de operaciones: aprueba ordenes de trabajo y publicaciones sociales.";
            case GERENTE_CALIDAD -> "Gerencia de calidad: aprueba no conformidades, acciones correctivas, cambios y riesgos del SGC.";
        };
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Carga un rol garantizando que pertenece a la Empresa (Req 23) y que es
     * gestionable, es decir, NO predefinido (Req 28.6).
     */
    private Rol cargarRolGestionable(UUID rolId, UUID tenantId) {
        // Un rol predefinido tiene tenant_id NULL: no lo resolvera la busqueda
        // por tenant. Se comprueba primero su existencia global para distinguir
        // "no existe" (404) de "es predefinido e inmutable" (403).
        Optional<Rol> global = rolRepository.findById(rolId);
        if (global.isPresent() && global.get().isPredefinido()) {
            throw new RolPredefinidoInmutableException(
                    "Los roles predefinidos del Sistema no pueden modificarse ni eliminarse.");
        }
        return rolRepository.findByIdAndTenantId(rolId, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el rol solicitado."));
    }

    /**
     * Resuelve los permisos solicitados validando que TODOS existan, que NINGUNO
     * sea de nivel plataforma (Req 28.5) y que NINGUNO pertenezca a un vertical
     * ajeno al Giro de la Empresa del contexto (Req 7.2, 7.4). Un conjunto vacio
     * se rechaza porque un rol sin permisos no combina "permisos existentes".
     *
     * @param tenantId Empresa (tenant) derivada del contexto autenticado, cuyo
     *                 Giro determina que recursos de vertical son aplicables
     *                 (Req 7.2); nunca se toma de la peticion (Req 8.2).
     */
    private Set<PermisoEntity> resolverPermisosValidos(UUID tenantId, Set<UUID> permisoIds) {
        if (permisoIds == null || permisoIds.isEmpty()) {
            throw new ReglaNegocioException(
                    "Debe seleccionar al menos un Permiso existente para el rol.");
        }

        Set<UUID> idsSolicitados = new LinkedHashSet<>(permisoIds);
        List<PermisoEntity> encontrados = permisoRepository.findByIdIn(idsSolicitados);

        // Permisos inexistentes (Req 28.5).
        if (encontrados.size() != idsSolicitados.size()) {
            Set<UUID> idsEncontrados = encontrados.stream()
                    .map(PermisoEntity::getId)
                    .collect(Collectors.toSet());
            List<UUID> inexistentes = idsSolicitados.stream()
                    .filter(id -> !idsEncontrados.contains(id))
                    .toList();
            throw new ReglaNegocioException(
                    "Se referencian Permisos inexistentes: " + inexistentes);
        }

        // Permisos de nivel plataforma prohibidos en roles de empresa (Req 28.5).
        List<String> dePlataforma = new ArrayList<>();
        for (PermisoEntity permiso : encontrados) {
            if (ClasificadorRecursosPlataforma.esPermisoPlataforma(permiso)) {
                dePlataforma.add(permiso.authority());
            }
        }
        if (!dePlataforma.isEmpty()) {
            throw new ReglaNegocioException(
                    "Un Rol_Personalizado no puede incluir Permisos de nivel plataforma: "
                            + dePlataforma);
        }

        // Permisos de un vertical ajeno al Giro de la Empresa prohibidos (Req 7.2,
        // 7.4): un permiso de Nucleo o del vertical del propio Giro es aceptable.
        // El Giro se resuelve del contexto del tenant, nunca de la peticion (Req 8.2).
        String giroEmpresa = giroEmpresaPort.giroDeTenant(tenantId).orElse(null);
        List<String> deVerticalAjeno = new ArrayList<>();
        for (PermisoEntity permiso : encontrados) {
            if (!clasificadorVertical.esRecursoAplicableAGiro(permiso.getRecurso(), giroEmpresa)) {
                deVerticalAjeno.add(permiso.authority());
            }
        }
        if (!deVerticalAjeno.isEmpty()) {
            throw new ReglaNegocioException(
                    "Un Rol_Personalizado no puede incluir Permisos de un vertical ajeno al "
                            + "Giro de la Empresa: " + deVerticalAjeno);
        }

        return new LinkedHashSet<>(encontrados);
    }

    /**
     * Persiste el rol traduciendo una posible violacion del indice parcial de
     * unicidad por tenant a {@link ConflictoUnicidadException} (Req 28.2), por
     * si dos peticiones concurrentes superan la comprobacion previa.
     */
    private Rol guardarTraduciendoUnicidad(Rol rol, String nombre) {
        try {
            return rolRepository.save(rol);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoUnicidadException(
                    "Ya existe un rol con el nombre '" + nombre + "' en esta Empresa.");
        }
    }

    private void auditar(UUID tenantId, String actor, String accion, String detalle) {
        auditoria.registrar(
                EventoAuditoria.deTenant(tenantId, actor, accion, RECURSO_ROL, detalle, null, null));
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

    private static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre del rol es obligatorio.");
        }
        return valor.strip();
    }
}
