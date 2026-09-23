package com.dessti.crm.compras.proveedor.application;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.compras.proveedor.adapter.out.persistence.ProveedorRepository;
import com.dessti.crm.compras.proveedor.domain.Proveedor;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion del modulo compras-abastecimiento que gobierna el ciclo
 * de vida de los {@link Proveedor} (Req 29). Replica <em>exactamente</em> el
 * patron establecido por {@code ServicioClientes} (mismo tratamiento de RFC unico
 * por tenant entre activos y de borrado logico).
 *
 * <h2>Operaciones (Req 29)</h2>
 * <ul>
 *   <li><strong>crearProveedor (Req 29.1, 29.2):</strong> valida los datos
 *       obligatorios, rechaza el RFC duplicado entre Proveedores activos (409,
 *       doble defensa: comprobacion previa {@code existsByRfcAndActivoTrue} +
 *       traduccion de la violacion del indice unico parcial ante carreras) y
 *       audita el alta.</li>
 *   <li><strong>actualizarProveedor (Req 29.4):</strong> persiste los cambios y
 *       audita; 404 si el Proveedor no existe/pertenece a otro tenant.</li>
 *   <li><strong>desactivarProveedor (Req 29.5):</strong> borrado logico
 *       ({@code activo=false}) conservando el historico; audita.</li>
 *   <li><strong>listarProveedores (Req 29.3, 29.6):</strong> listado paginado
 *       (20/100) filtrable por nombre o RFC sin distinguir mayusculas.</li>
 *   <li><strong>consultarProveedor (Req 23.3):</strong> consulta puntual; 404 +
 *       auditoria del intento si el Proveedor no existe o pertenece a otro
 *       tenant.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 29.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion se registra via {@link AuditoriaPort} como
 * evento de tenant con el actor derivado del contexto de seguridad, sin incluir
 * secretos.</p>
 */
@Service
public class ServicioProveedores {

    /** Tipo de recurso de auditoria/RBAC del Proveedor. */
    static final String RECURSO_PROVEEDOR = "proveedor";

    private final ProveedorRepository proveedorRepository;
    private final AuditoriaPort auditoria;

    public ServicioProveedores(ProveedorRepository proveedorRepository, AuditoriaPort auditoria) {
        this.proveedorRepository = proveedorRepository;
        this.auditoria = auditoria;
    }

    /**
     * Registra un Proveedor con los datos obligatorios validados (Req 29.1) y un
     * RFC unico entre los Proveedores activos del tenant (Req 29.2, 23.6).
     *
     * @param comando datos del Proveedor a crear.
     * @return el DTO del Proveedor creado.
     * @throws ReglaNegocioException si faltan o son invalidos los datos
     *         obligatorios (422, Req 29.1).
     * @throws ConflictoUnicidadException si ya existe un Proveedor activo con el
     *         mismo RFC en el tenant (409, Req 29.2).
     */
    @Transactional
    public ProveedorDto crearProveedor(CrearProveedorCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del Proveedor son obligatorios.");
        }

        // Construye y valida el Proveedor (Req 29.1) antes de la comprobacion de
        // unicidad, para que un dato invalido produzca 422 y no 409.
        Proveedor proveedor = Proveedor.crear(
                comando.nombre(), comando.rfc(), comando.email(), comando.telefono(), actor);

        // Pre-comprobacion de unicidad de RFC entre activos del tenant (Req 29.2).
        if (proveedorRepository.existsByRfcAndActivoTrue(proveedor.getRfc())) {
            throw new ConflictoUnicidadException(
                    "Ya existe un Proveedor activo con el identificador fiscal '"
                            + proveedor.getRfc() + "'.");
        }

        Proveedor guardado = guardarTraduciendoUnicidad(proveedor);
        auditar(actor, "crear", guardado.getId(),
                "creado proveedor '" + guardado.getNombre() + "' (rfc=" + guardado.getRfc() + ")");
        return ProveedorDto.de(guardado);
    }

    /**
     * Actualiza los datos de un Proveedor activo del tenant (Req 29.4).
     *
     * @param proveedorId identificador del Proveedor.
     * @param comando     nuevos datos.
     * @return el DTO del Proveedor actualizado.
     * @throws RecursoNoEncontradoException si el Proveedor no existe, esta inactivo
     *         o pertenece a otro tenant (404, Req 23.3).
     * @throws ConflictoUnicidadException si el nuevo RFC colisiona con otro
     *         Proveedor activo del tenant (409, Req 29.2).
     */
    @Transactional
    public ProveedorDto actualizarProveedor(UUID proveedorId, ActualizarProveedorCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del Proveedor son obligatorios.");
        }
        Proveedor proveedor = cargarActivo(proveedorId, actor);

        proveedor.actualizar(comando.nombre(), comando.rfc(), comando.email(), comando.telefono(), actor);

        // Si cambia el RFC, comprobar que no colisione con OTRO Proveedor activo.
        if (proveedorRepository.existsByRfcAndActivoTrue(proveedor.getRfc())
                && !esMismoRfcDelProveedor(proveedorId, proveedor.getRfc())) {
            throw new ConflictoUnicidadException(
                    "Ya existe un Proveedor activo con el identificador fiscal '"
                            + proveedor.getRfc() + "'.");
        }

        Proveedor guardado = guardarTraduciendoUnicidad(proveedor);
        auditar(actor, "actualizar", guardado.getId(),
                "actualizado proveedor '" + guardado.getNombre() + "' (rfc=" + guardado.getRfc() + ")");
        return ProveedorDto.de(guardado);
    }

    /**
     * Realiza el borrado logico de un Proveedor activo del tenant (Req 29.5):
     * marca {@code activo=false} conservando el historico y audita.
     *
     * @param proveedorId identificador del Proveedor.
     * @return el DTO del Proveedor desactivado.
     * @throws RecursoNoEncontradoException si el Proveedor no existe, ya esta
     *         inactivo o pertenece a otro tenant (404, Req 23.3).
     */
    @Transactional
    public ProveedorDto desactivarProveedor(UUID proveedorId) {
        String actor = actorActual();
        Proveedor proveedor = cargarActivo(proveedorId, actor);
        proveedor.desactivar(actor);
        Proveedor guardado = proveedorRepository.save(proveedor);
        auditar(actor, "eliminar", guardado.getId(),
                "baja logica del proveedor '" + guardado.getNombre() + "' (rfc=" + guardado.getRfc() + ")");
        return ProveedorDto.de(guardado);
    }

    /**
     * Consulta puntual de un Proveedor activo del tenant (Req 23.3). Un Proveedor
     * inexistente, inactivo o de otro tenant produce 404 y se audita el intento
     * como acceso cruzado.
     *
     * @param proveedorId identificador del Proveedor.
     * @return el DTO del Proveedor.
     * @throws RecursoNoEncontradoException si el Proveedor no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ProveedorDto consultarProveedor(UUID proveedorId) {
        String actor = actorActual();
        return ProveedorDto.de(cargarActivo(proveedorId, actor));
    }

    /**
     * Listado paginado de Proveedores activos del tenant, filtrable por nombre o
     * RFC sin distinguir mayusculas (Req 29.3, 29.6). Un filtro nulo o en blanco
     * devuelve todos los Proveedores activos.
     *
     * @param filtro   subcadena a buscar en nombre o RFC; {@code null}/blanco
     *                 lista todos.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Proveedores activos como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ProveedorDto> listarProveedores(String filtro, Pageable pageable) {
        String criterio = (filtro == null) ? "" : filtro.strip().toLowerCase(java.util.Locale.ROOT);
        return proveedorRepository.buscarActivosPorNombreORfc(criterio, pageable)
                .map(ProveedorDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Carga un Proveedor activo por id dentro del tenant vigente; si no es
     * accesible (inexistente, inactivo o de otro tenant) audita el intento y
     * lanza 404 (Req 23.3).
     */
    private Proveedor cargarActivo(UUID proveedorId, String actor) {
        if (proveedorId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Proveedor solicitado.");
        }
        return proveedorRepository.findByIdAndActivoTrue(proveedorId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_PROVEEDOR, proveedorId);
                    throw new RecursoNoEncontradoException("No se encontro el Proveedor solicitado.");
                });
    }

    /**
     * Comprueba si el unico Proveedor activo con ese RFC en el tenant es el propio
     * Proveedor que se esta actualizando (para no confundir "sin cambio de RFC" con
     * un duplicado real).
     */
    private boolean esMismoRfcDelProveedor(UUID proveedorId, String rfc) {
        return proveedorRepository.findByIdAndActivoTrue(proveedorId)
                .map(existente -> rfc.equals(existente.getRfc()))
                .orElse(false);
    }

    private Proveedor guardarTraduciendoUnicidad(Proveedor proveedor) {
        try {
            return proveedorRepository.saveAndFlush(proveedor);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente contra el indice parcial uq_proveedor_rfc_activo_por_tenant (V28).
            throw new ConflictoUnicidadException(
                    "Ya existe un Proveedor activo con el identificador fiscal '"
                            + proveedor.getRfc() + "'.");
        }
    }

    private void auditar(String actor, String accion, UUID proveedorId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_PROVEEDOR,
                detalle + " [id=" + proveedorId + "]", null, null));
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
