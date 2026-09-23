package com.dessti.crm.comercial.producto.application;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.comercial.producto.adapter.out.persistence.ListaPreciosRepository;
import com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository;
import com.dessti.crm.comercial.producto.adapter.out.persistence.ProductoRepository;
import com.dessti.crm.comercial.producto.domain.ListaPrecios;
import com.dessti.crm.comercial.producto.domain.PrecioProducto;
import com.dessti.crm.comercial.producto.domain.Producto;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna las {@link ListaPrecios} y la asignacion de
 * {@link PrecioProducto} (Req 59.3, 59.9, 59.10). Replica el patron de
 * {@code ServicioClientes}.
 *
 * <h2>Operaciones (Req 59)</h2>
 * <ul>
 *   <li><strong>definirLista (Req 59.3, 59.9):</strong> crea una Lista_Precios con
 *       su vigencia, prioridad y segmento opcional; audita.</li>
 *   <li><strong>actualizarLista:</strong> revalida y persiste; 404 si no es
 *       accesible.</li>
 *   <li><strong>desactivarLista:</strong> baja logica de la lista; audita.</li>
 *   <li><strong>asignarPrecio (Req 59.3, 59.10):</strong> asigna (o actualiza) el
 *       precio de un Producto activo dentro de una lista, validando el rango
 *       0.01..999,999,999.99 (422 fuera de rango); audita.</li>
 *   <li><strong>consultarLista / listarListas (Req 59.7):</strong> consulta y
 *       listado paginado filtrable por nombre.</li>
 * </ul>
 *
 * <h2>Aislamiento y auditoria (Req 23, 59.8)</h2>
 * <p>El tenant se deriva del {@link TenantContext}; cada operacion se audita como
 * evento de tenant. Un recurso de otro tenant devuelve vacio -> 404 auditado.</p>
 */
@Service
public class ServicioListasPrecios {

    /** Tipo de recurso de auditoria/RBAC de la Lista_Precios. */
    static final String RECURSO_LISTA = "lista_precios";

    private final ListaPreciosRepository listaPreciosRepository;
    private final PrecioProductoRepository precioProductoRepository;
    private final ProductoRepository productoRepository;
    private final AuditoriaPort auditoria;

    public ServicioListasPrecios(ListaPreciosRepository listaPreciosRepository,
                                 PrecioProductoRepository precioProductoRepository,
                                 ProductoRepository productoRepository,
                                 AuditoriaPort auditoria) {
        this.listaPreciosRepository = listaPreciosRepository;
        this.precioProductoRepository = precioProductoRepository;
        this.productoRepository = productoRepository;
        this.auditoria = auditoria;
    }

    /**
     * Crea una Lista_Precios con su vigencia, prioridad y segmento (Req 59.3,
     * 59.9) y audita el alta.
     *
     * @param comando datos de la lista.
     * @return el DTO de la Lista_Precios creada.
     * @throws ReglaNegocioException si los datos son invalidos (422).
     */
    @Transactional
    public ListaPreciosDto definirLista(DefinirListaPreciosCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Lista_Precios son obligatorios.");
        }
        ListaPrecios lista = ListaPrecios.crear(
                comando.nombre(), comando.prioridad(), comando.segmento(),
                comando.vigenciaInicio(), comando.vigenciaFin(), actor);
        ListaPrecios guardada = listaPreciosRepository.save(lista);
        auditarLista(actor, "crear", guardada.getId(),
                "creada lista de precios '" + guardada.getNombre() + "'");
        return ListaPreciosDto.de(guardada);
    }

    /**
     * Actualiza una Lista_Precios activa del tenant (Req 59.3, 59.9).
     *
     * @param listaId identificador de la lista.
     * @param comando nuevos datos.
     * @return el DTO de la lista actualizada.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si los datos son invalidos (422).
     */
    @Transactional
    public ListaPreciosDto actualizarLista(UUID listaId, DefinirListaPreciosCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Lista_Precios son obligatorios.");
        }
        ListaPrecios lista = cargarListaActiva(listaId, actor);
        lista.actualizar(comando.nombre(), comando.prioridad(), comando.segmento(),
                comando.vigenciaInicio(), comando.vigenciaFin(), actor);
        ListaPrecios guardada = listaPreciosRepository.save(lista);
        auditarLista(actor, "actualizar", guardada.getId(),
                "actualizada lista de precios '" + guardada.getNombre() + "'");
        return ListaPreciosDto.de(guardada);
    }

    /**
     * Realiza el borrado logico de una Lista_Precios activa y audita.
     *
     * @param listaId identificador de la lista.
     * @return el DTO de la lista desactivada.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public ListaPreciosDto desactivarLista(UUID listaId) {
        String actor = actorActual();
        ListaPrecios lista = cargarListaActiva(listaId, actor);
        lista.desactivar(actor);
        ListaPrecios guardada = listaPreciosRepository.save(lista);
        auditarLista(actor, "eliminar", guardada.getId(),
                "baja logica de la lista de precios '" + guardada.getNombre() + "'");
        return ListaPreciosDto.de(guardada);
    }

    /**
     * Asigna (o actualiza) el precio de un Producto activo dentro de una
     * Lista_Precios activa, validando el rango 0.01..999,999,999.99 (Req 59.3,
     * 59.10). Un precio fuera de rango se rechaza con 422 y no se persiste
     * (Property 30). Si el Producto ya tenia precio en esa lista, se actualiza
     * (unico por lista/producto).
     *
     * @param listaId identificador de la Lista_Precios.
     * @param comando Producto y precio a asignar.
     * @return el DTO del precio asignado.
     * @throws RecursoNoEncontradoException si la lista o el Producto no son
     *         accesibles (404).
     * @throws ReglaNegocioException si el precio esta fuera de rango (422).
     */
    @Transactional
    public PrecioProductoDto asignarPrecio(UUID listaId, AsignarPrecioCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.productoId() == null) {
            throw new ReglaNegocioException("El Producto del precio es obligatorio.");
        }
        ListaPrecios lista = cargarListaActiva(listaId, actor);
        Producto producto = productoRepository.findByIdAndActivoTrue(comando.productoId())
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, ServicioProductos.RECURSO_PRODUCTO, comando.productoId());
                    throw new RecursoNoEncontradoException("No se encontro el Producto solicitado.");
                });

        PrecioProducto precio = precioProductoRepository
                .findByListaPreciosIdAndProductoId(lista.getId(), producto.getId())
                .map(existente -> {
                    existente.actualizarPrecio(comando.precio(), actor);
                    return existente;
                })
                .orElseGet(() -> PrecioProducto.crear(
                        lista.getId(), producto.getId(), comando.precio(), actor));

        PrecioProducto guardado = guardarPrecioTraduciendoUnicidad(precio);
        auditarLista(actor, "actualizar", lista.getId(),
                "asignado precio " + guardado.getPrecio().toPlainString()
                        + " al producto " + producto.getId() + " en la lista");
        return PrecioProductoDto.de(guardado);
    }

    /**
     * Lista los precios asignados en una Lista_Precios activa del tenant, con el
     * nombre del Producto, para mostrarlos en la UI (bugfix: dar visibilidad al
     * precio guardado). 404 si la lista no es accesible (Req 23.3).
     *
     * @param listaId identificador de la Lista_Precios.
     * @return los precios de la lista como DTOs (producto + precio).
     */
    @Transactional(readOnly = true)
    public java.util.List<PrecioListaDto> listarPrecios(UUID listaId) {
        String actor = actorActual();
        ListaPrecios lista = cargarListaActiva(listaId, actor);
        return precioProductoRepository.buscarPreciosDeLista(lista.getId()).stream()
                .map(PrecioListaDto::de)
                .toList();
    }

    /**
     * Consulta puntual de una Lista_Precios activa (Req 4.3, 23.3).
     *
     * @param listaId identificador de la lista.
     * @return el DTO de la lista.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ListaPreciosDto consultarLista(UUID listaId) {
        String actor = actorActual();
        return ListaPreciosDto.de(cargarListaActiva(listaId, actor));
    }

    /**
     * Listado paginado de Listas_Precios activas del tenant, filtrable por nombre
     * (Req 59.7). Un filtro nulo/blanco lista todas.
     *
     * @param filtro   subcadena a buscar en el nombre; {@code null}/blanco lista todas.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Listas_Precios activas como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ListaPreciosDto> listarListas(String filtro, Pageable pageable) {
        String criterio = (filtro == null) ? "" : filtro.strip().toLowerCase(Locale.ROOT);
        return listaPreciosRepository.buscarActivasPorNombre(criterio, pageable)
                .map(ListaPreciosDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private ListaPrecios cargarListaActiva(UUID listaId, String actor) {
        if (listaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Lista_Precios solicitada.");
        }
        return listaPreciosRepository.findByIdAndActivoTrue(listaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_LISTA, listaId);
                    throw new RecursoNoEncontradoException("No se encontro la Lista_Precios solicitada.");
                });
    }

    private PrecioProducto guardarPrecioTraduciendoUnicidad(PrecioProducto precio) {
        try {
            return precioProductoRepository.saveAndFlush(precio);
        } catch (DataIntegrityViolationException ex) {
            // Carrera contra uq_precio_producto_lista_producto (V12): otro hilo asigno
            // el mismo (lista, producto). Se reintenta como actualizacion idempotente.
            throw new ReglaNegocioException(
                    "El precio del Producto en esta lista fue modificado concurrentemente; reintente.");
        }
    }

    private void auditarLista(String actor, String accion, UUID listaId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_LISTA,
                detalle + " [id=" + listaId + "]", null, null));
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
