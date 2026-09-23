package com.dessti.crm.comercial.producto.adapter.in.rest;

import java.util.List;
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

import com.dessti.crm.comercial.producto.application.AsignarPrecioCommand;
import com.dessti.crm.comercial.producto.application.DefinirListaPreciosCommand;
import com.dessti.crm.comercial.producto.application.ListaPreciosDto;
import com.dessti.crm.comercial.producto.application.PrecioListaDto;
import com.dessti.crm.comercial.producto.application.PrecioProductoDto;
import com.dessti.crm.comercial.producto.application.ServicioListasPrecios;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del submodulo comercial-crm para la gestion de
 * {@link ListaPreciosDto Listas_Precios} y la asignacion de precios a Productos
 * (Req 59.3, 59.9, 59.10; tarea 16.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /listas-precios} — define una lista
 *       ({@code @autorizador.tiene('lista_precios','crear')}); 201 Created.</li>
 *   <li>{@code GET /listas-precios/{id}} — consulta
 *       ({@code @autorizador.tiene('lista_precios','leer')}); 200 OK.</li>
 *   <li>{@code PUT /listas-precios/{id}} — actualiza
 *       ({@code @autorizador.tiene('lista_precios','actualizar')}); 200 OK.</li>
 *   <li>{@code DELETE /listas-precios/{id}} — baja logica
 *       ({@code @autorizador.tiene('lista_precios','actualizar')}); 200 OK.</li>
 *   <li>{@code GET /listas-precios?filtro=&page=&size=} — listado paginado
 *       ({@code @autorizador.tiene('lista_precios','listar')}); 200 OK.</li>
 *   <li>{@code PUT /listas-precios/{id}/precios} — asigna el precio de un
 *       Producto en la lista ({@code @autorizador.tiene('lista_precios','actualizar')});
 *       200 OK. 422 si el precio esta fuera de rango (Req 59.10).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 59)</h2>
 * <p>Los permisos {@code lista_precios:{crear,leer,listar,actualizar}} se
 * sembraron en V5 y se asignaron a los roles {@code ventas} y {@code almacen}.
 * La baja logica y la asignacion de precios se guardan con la operacion
 * {@code actualizar} (modificacion de la lista), sin requerir un permiso nuevo.</p>
 */
@RestController
@RequestMapping("/listas-precios")
public class ListaPreciosController {

    private final ServicioListasPrecios servicioListasPrecios;

    public ListaPreciosController(ServicioListasPrecios servicioListasPrecios) {
        this.servicioListasPrecios = servicioListasPrecios;
    }

    /**
     * Define una Lista_Precios con su vigencia, prioridad y segmento (Req 59.3,
     * 59.9). 422 si los datos son invalidos.
     *
     * @param request datos de la lista.
     * @return 201 Created con el {@link ListaPreciosDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('lista_precios','crear')")
    public ResponseEntity<ListaPreciosDto> definir(@Valid @RequestBody DefinirListaPreciosRequest request) {
        ListaPreciosDto dto = servicioListasPrecios.definirLista(comandoDe(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Lista_Precios por su identificador (Req 4.3, 23.3). 404 si no
     * es accesible.
     *
     * @param id identificador de la lista.
     * @return 200 OK con el {@link ListaPreciosDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('lista_precios','leer')")
    public ResponseEntity<ListaPreciosDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioListasPrecios.consultarLista(id));
    }

    /**
     * Actualiza una Lista_Precios activa (Req 59.3, 59.9). 404/422 segun aplique.
     *
     * @param id      identificador de la lista.
     * @param request nuevos datos.
     * @return 200 OK con el {@link ListaPreciosDto} actualizado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('lista_precios','actualizar')")
    public ResponseEntity<ListaPreciosDto> actualizar(@PathVariable("id") UUID id,
                                                      @Valid @RequestBody DefinirListaPreciosRequest request) {
        return ResponseEntity.ok(servicioListasPrecios.actualizarLista(id, comandoDe(request)));
    }

    /**
     * Realiza el borrado logico de una Lista_Precios activa. 200 OK con el DTO
     * desactivado; 404 si no es accesible.
     *
     * @param id identificador de la lista.
     * @return 200 OK con el {@link ListaPreciosDto} desactivado.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('lista_precios','actualizar')")
    public ResponseEntity<ListaPreciosDto> eliminar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioListasPrecios.desactivarLista(id));
    }

    /**
     * Lista las Listas_Precios activas del tenant de forma paginada (20/100)
     * filtrando por nombre (Req 59.7).
     *
     * @param filtro subcadena a buscar en el nombre; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ListaPreciosDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('lista_precios','listar')")
    public PaginaResponse<ListaPreciosDto> listar(
            @RequestParam(name = "filtro", required = false) String filtro,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioListasPrecios.listarListas(filtro, pageable));
    }

    /**
     * Asigna (o actualiza) el precio de un Producto en la lista, validando el
     * rango 0.01..999,999,999.99 (Req 59.3, 59.10). 422 si esta fuera de rango;
     * 404 si la lista o el Producto no son accesibles.
     *
     * @param id      identificador de la Lista_Precios.
     * @param request Producto y precio a asignar.
     * @return 200 OK con el {@link PrecioProductoDto} asignado.
     */
    @PutMapping("/{id}/precios")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('lista_precios','actualizar')")
    public ResponseEntity<PrecioProductoDto> asignarPrecio(@PathVariable("id") UUID id,
                                                           @Valid @RequestBody AsignarPrecioRequest request) {
        PrecioProductoDto dto = servicioListasPrecios.asignarPrecio(id,
                new AsignarPrecioCommand(request.productoId(), request.precio()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Lista los precios asignados en una Lista_Precios, con el nombre del
     * Producto, para mostrarlos en la UI (bugfix: visibilidad del precio guardado).
     *
     * @param id identificador de la Lista_Precios.
     * @return 200 OK con los precios (producto + precio) de la lista.
     */
    @GetMapping("/{id}/precios")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('lista_precios','leer')")
    public ResponseEntity<List<PrecioListaDto>> listarPrecios(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioListasPrecios.listarPrecios(id));
    }

    private static DefinirListaPreciosCommand comandoDe(DefinirListaPreciosRequest request) {
        return new DefinirListaPreciosCommand(
                request.nombre(),
                request.prioridad(),
                request.segmento(),
                request.vigenciaInicio(),
                request.vigenciaFin());
    }
}
