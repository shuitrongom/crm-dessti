package com.dessti.crm.comercial.producto.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.comercial.producto.application.ActualizarProductoCommand;
import com.dessti.crm.comercial.producto.application.CrearProductoCommand;
import com.dessti.crm.comercial.producto.application.ProductoDto;
import com.dessti.crm.comercial.producto.application.ServicioProductos;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del submodulo comercial-crm para la gestion del
 * catalogo de {@link ProductoDto Productos} (Req 59, 12; tarea 16.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /productos} — alta ({@code @autorizador.tiene('producto','crear')});
 *       201 Created. 422 si los datos son invalidos (Req 59.1, 59.2).</li>
 *   <li>{@code GET /productos/{id}} — consulta
 *       ({@code @autorizador.tiene('producto','leer')}); 200 OK. 404 si no
 *       existe/otro tenant (Req 4.3, 23.3).</li>
 *   <li>{@code PUT /productos/{id}} — actualizacion
 *       ({@code @autorizador.tiene('producto','actualizar')}); 200 OK.</li>
 *   <li>{@code DELETE /productos/{id}} — baja logica
 *       ({@code @autorizador.tiene('producto','eliminar')}); 200 OK con el DTO
 *       desactivado (Req 59.6).</li>
 *   <li>{@code GET /productos?filtro=&page=&size=} — listado paginado (20/100)
 *       filtrable por nombre ({@code @autorizador.tiene('producto','listar')});
 *       200 OK con {@link PaginaResponse} (Req 59.7).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 59)</h2>
 * <p>Cada endpoint exige el permiso atomico {@code producto:{crear,leer,listar,
 * actualizar,eliminar}} via {@code @autorizador.tiene(recurso, operacion)}. Esos
 * permisos se sembraron en V5/V12 y se asignaron a los roles {@code ventas} y
 * {@code almacen} (area comercial o de almacen, Req 27.2/27.5).</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores}.</p>
 */
@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ServicioProductos servicioProductos;

    public ProductoController(ServicioProductos servicioProductos) {
        this.servicioProductos = servicioProductos;
    }

    /**
     * Da de alta un Producto (Req 59.1, 59.2). 422 si los datos son invalidos.
     *
     * @param request datos del Producto a crear.
     * @return 201 Created con el {@link ProductoDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('producto','crear')")
    public ResponseEntity<ProductoDto> crear(@Valid @RequestBody CrearProductoRequest request) {
        ProductoDto dto = servicioProductos.crearProducto(new CrearProductoCommand(
                request.nombre(),
                request.unidad(),
                request.descripcion(),
                request.clienteMeta(),
                request.alianzas(),
                request.competencia(),
                request.foto()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Producto por su identificador (Req 4.3, 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador del Producto.
     * @return 200 OK con el {@link ProductoDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('producto','leer')")
    public ResponseEntity<ProductoDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioProductos.consultarProducto(id));
    }

    /**
     * Actualiza los datos de un Producto activo (Req 59). 404 si no es accesible;
     * 422 si los datos son invalidos.
     *
     * @param id      identificador del Producto.
     * @param request nuevos datos.
     * @return 200 OK con el {@link ProductoDto} actualizado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('producto','actualizar')")
    public ResponseEntity<ProductoDto> actualizar(@PathVariable("id") UUID id,
                                                  @Valid @RequestBody ActualizarProductoRequest request) {
        ProductoDto dto = servicioProductos.actualizarProducto(id, new ActualizarProductoCommand(
                request.nombre(),
                request.unidad(),
                request.descripcion(),
                request.clienteMeta(),
                request.alianzas(),
                request.competencia(),
                request.foto()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Realiza el borrado logico de un Producto activo (Req 59.6). 200 OK con el
     * {@link ProductoDto} desactivado; 404 si no existe/ya inactivo/otro tenant.
     *
     * @param id identificador del Producto.
     * @return 200 OK con el {@link ProductoDto} desactivado.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('producto','eliminar')")
    public ResponseEntity<ProductoDto> eliminar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioProductos.desactivarProducto(id));
    }

    /**
     * Lista los Productos activos del tenant de forma paginada (20 por defecto,
     * 100 maximo) filtrando por nombre sin distinguir mayusculas (Req 59.7).
     *
     * @param filtro subcadena a buscar en el nombre; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ProductoDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('producto','listar')")
    public PaginaResponse<ProductoDto> listar(
            @RequestParam(name = "filtro", required = false) String filtro,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioProductos.listarProductos(filtro, pageable));
    }
}
