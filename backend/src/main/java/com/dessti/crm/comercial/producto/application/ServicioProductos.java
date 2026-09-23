package com.dessti.crm.comercial.producto.application;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.comercial.producto.adapter.out.persistence.ProductoRepository;
import com.dessti.crm.comercial.producto.domain.Producto;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de los {@link Producto}
 * del catalogo comercial (Req 59). Replica el patron establecido por
 * {@code ServicioClientes}.
 *
 * <h2>Operaciones (Req 59)</h2>
 * <ul>
 *   <li><strong>crearProducto (Req 59.1, 59.2):</strong> valida los datos
 *       obligatorios (nombre 1..200, unidad y descripcion), persiste y audita.</li>
 *   <li><strong>actualizarProducto (Req 59):</strong> revalida y persiste; 404 si
 *       el Producto no existe/inactivo/otro tenant.</li>
 *   <li><strong>desactivarProducto (Req 59.6):</strong> borrado logico
 *       ({@code activo=false}) conservando el historico; audita.</li>
 *   <li><strong>consultarProducto (Req 4.3, 23.3):</strong> consulta puntual;
 *       404 + auditoria del intento si no es accesible.</li>
 *   <li><strong>listarProductos (Req 59.7):</strong> listado paginado (20/100)
 *       filtrable por nombre sin distinguir mayusculas.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 59.8)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada creacion, modificacion o baja se registra via
 * {@link AuditoriaPort} como evento de tenant ({@link EventoAuditoria#deTenant})
 * con el actor derivado del contexto de seguridad (Req 59.8, 10.10).</p>
 */
@Service
public class ServicioProductos {

    /** Tipo de recurso de auditoria/RBAC del Producto. */
    static final String RECURSO_PRODUCTO = "producto";

    private final ProductoRepository productoRepository;
    private final AuditoriaPort auditoria;

    public ServicioProductos(ProductoRepository productoRepository, AuditoriaPort auditoria) {
        this.productoRepository = productoRepository;
        this.auditoria = auditoria;
    }

    /**
     * Da de alta un Producto con los datos obligatorios validados (Req 59.1, 59.2).
     *
     * @param comando datos del Producto a crear.
     * @return el DTO del Producto creado.
     * @throws ReglaNegocioException si faltan o son invalidos los datos
     *         obligatorios (422, Req 59.2).
     */
    @Transactional
    public ProductoDto crearProducto(CrearProductoCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del Producto son obligatorios.");
        }
        Producto producto = Producto.crear(
                comando.nombre(), comando.unidad(), comando.descripcion(),
                comando.clienteMeta(), comando.alianzas(), comando.competencia(),
                comando.foto(), actor);
        Producto guardado = productoRepository.save(producto);
        auditarProducto(actor, "crear", guardado.getId(),
                "creado producto '" + guardado.getNombre() + "'");
        return ProductoDto.de(guardado);
    }

    /**
     * Actualiza los datos de un Producto activo del tenant (Req 59).
     *
     * @param productoId identificador del Producto.
     * @param comando    nuevos datos.
     * @return el DTO del Producto actualizado.
     * @throws RecursoNoEncontradoException si no es accesible (404, Req 23.3).
     * @throws ReglaNegocioException si los datos son invalidos (422, Req 59.2).
     */
    @Transactional
    public ProductoDto actualizarProducto(UUID productoId, ActualizarProductoCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del Producto son obligatorios.");
        }
        Producto producto = cargarProductoActivo(productoId, actor);
        producto.actualizar(comando.nombre(), comando.unidad(), comando.descripcion(),
                comando.clienteMeta(), comando.alianzas(), comando.competencia(),
                comando.foto(), actor);
        Producto guardado = productoRepository.save(producto);
        auditarProducto(actor, "actualizar", guardado.getId(),
                "actualizado producto '" + guardado.getNombre() + "'");
        return ProductoDto.de(guardado);
    }

    /**
     * Realiza el borrado logico de un Producto activo (Req 59.6): marca
     * {@code activo=false} conservando el historico y audita.
     *
     * @param productoId identificador del Producto.
     * @return el DTO del Producto desactivado.
     * @throws RecursoNoEncontradoException si no existe, ya esta inactivo o es de
     *         otro tenant (404, Req 23.3).
     */
    @Transactional
    public ProductoDto desactivarProducto(UUID productoId) {
        String actor = actorActual();
        Producto producto = cargarProductoActivo(productoId, actor);
        producto.desactivar(actor);
        Producto guardado = productoRepository.save(producto);
        auditarProducto(actor, "eliminar", guardado.getId(),
                "baja logica del producto '" + guardado.getNombre() + "'");
        return ProductoDto.de(guardado);
    }

    /**
     * Consulta puntual de un Producto activo del tenant (Req 4.3, 23.3).
     *
     * @param productoId identificador del Producto.
     * @return el DTO del Producto.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ProductoDto consultarProducto(UUID productoId) {
        String actor = actorActual();
        Producto producto = cargarProductoActivo(productoId, actor);
        return ProductoDto.de(producto);
    }

    /**
     * Listado paginado de Productos activos del tenant, filtrable por nombre sin
     * distinguir mayusculas (Req 59.7). Un filtro nulo/blanco lista todos.
     *
     * @param filtro   subcadena a buscar en el nombre; {@code null}/blanco lista todos.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Productos activos como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ProductoDto> listarProductos(String filtro, Pageable pageable) {
        String criterio = (filtro == null) ? "" : filtro.strip().toLowerCase(Locale.ROOT);
        return productoRepository.buscarActivosPorNombre(criterio, pageable)
                .map(ProductoDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Producto cargarProductoActivo(UUID productoId, String actor) {
        if (productoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Producto solicitado.");
        }
        return productoRepository.findByIdAndActivoTrue(productoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_PRODUCTO, productoId);
                    throw new RecursoNoEncontradoException("No se encontro el Producto solicitado.");
                });
    }

    private void auditarProducto(String actor, String accion, UUID productoId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_PRODUCTO,
                detalle + " [id=" + productoId + "]", null, null));
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
