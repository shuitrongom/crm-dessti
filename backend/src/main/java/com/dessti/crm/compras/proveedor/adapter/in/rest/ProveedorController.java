package com.dessti.crm.compras.proveedor.adapter.in.rest;

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

import com.dessti.crm.compras.proveedor.application.ActualizarProveedorCommand;
import com.dessti.crm.compras.proveedor.application.CrearProveedorCommand;
import com.dessti.crm.compras.proveedor.application.ProveedorDto;
import com.dessti.crm.compras.proveedor.application.ServicioProveedores;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo compras-abastecimiento para la gestion de
 * {@link ProveedorDto Proveedores} (Req 29, 12; tarea 26.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /compras/proveedores} — alta
 *       ({@code @autorizador.tiene('proveedor','crear')}); 201 Created. 422 si los
 *       datos son invalidos, 409 si el RFC ya existe entre activos (Req 29.1, 29.2).</li>
 *   <li>{@code GET /compras/proveedores/{id}} — consulta
 *       ({@code @autorizador.tiene('proveedor','leer')}); 200 OK. 404 si no
 *       existe/otro tenant (Req 23.3).</li>
 *   <li>{@code PUT /compras/proveedores/{id}} — actualizacion
 *       ({@code @autorizador.tiene('proveedor','actualizar')}); 200 OK. 404/409/422
 *       segun corresponda (Req 29.4).</li>
 *   <li>{@code DELETE /compras/proveedores/{id}} — baja logica
 *       ({@code @autorizador.tiene('proveedor','actualizar')}); 200 OK con el
 *       {@link ProveedorDto} desactivado. 404 si no existe/ya inactivo/otro tenant
 *       (Req 29.5).</li>
 *   <li>{@code GET /compras/proveedores?filtro=&page=&size=} — listado paginado
 *       ({@code @autorizador.tiene('proveedor','listar')}); 200 OK con
 *       {@link PaginaResponse}. Tamano por defecto 20, maximo 100; filtro por
 *       nombre o RFC sin distinguir mayusculas (Req 29.3, 29.6).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.5)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code proveedor:{crear,leer,listar,actualizar}} se sembraron en V5 y se
 * asignaron al rol {@code almacen} (Req 27.5). <strong>Decision:</strong> V5 no
 * sembro {@code proveedor:eliminar}; la baja logica (Req 29.5) se protege con
 * {@code proveedor:actualizar} —al ser una modificacion del recurso— para no
 * introducir un permiso nuevo, a diferencia del Cliente (cuyo {@code eliminar} si
 * se sembro en V11). Sin el permiso, Spring Security responde 403 (Req 3.2).</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores}.</p>
 */
@RestController
@RequestMapping("/compras/proveedores")
public class ProveedorController {

    private final ServicioProveedores servicioProveedores;

    public ProveedorController(ServicioProveedores servicioProveedores) {
        this.servicioProveedores = servicioProveedores;
    }

    /**
     * Da de alta un Proveedor (Req 29.1, 29.2). 422 si los datos son invalidos;
     * 409 si ya existe un Proveedor activo con el mismo RFC.
     *
     * @param request datos del Proveedor a crear.
     * @return 201 Created con el {@link ProveedorDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('proveedor','crear')")
    public ResponseEntity<ProveedorDto> crear(@Valid @RequestBody CrearProveedorRequest request) {
        ProveedorDto dto = servicioProveedores.crearProveedor(new CrearProveedorCommand(
                request.nombre(),
                request.rfc(),
                request.email(),
                request.telefono()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Proveedor por su identificador (Req 23.3). 404 si no existe,
     * esta inactivo o pertenece a otro tenant.
     *
     * @param id identificador del Proveedor.
     * @return 200 OK con el {@link ProveedorDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('proveedor','leer')")
    public ResponseEntity<ProveedorDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioProveedores.consultarProveedor(id));
    }

    /**
     * Actualiza los datos de un Proveedor activo (Req 29.4). 404 si no es
     * accesible; 409 si el nuevo RFC colisiona con otro Proveedor activo; 422 si
     * los datos son invalidos.
     *
     * @param id      identificador del Proveedor.
     * @param request nuevos datos.
     * @return 200 OK con el {@link ProveedorDto} actualizado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('proveedor','actualizar')")
    public ResponseEntity<ProveedorDto> actualizar(@PathVariable("id") UUID id,
                                                   @Valid @RequestBody ActualizarProveedorRequest request) {
        ProveedorDto dto = servicioProveedores.actualizarProveedor(id, new ActualizarProveedorCommand(
                request.nombre(),
                request.rfc(),
                request.email(),
                request.telefono()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Realiza el borrado logico de un Proveedor activo (Req 29.5): lo marca
     * inactivo conservando su historico. Se responde 200 OK con el
     * {@link ProveedorDto} desactivado; 404 si no existe, ya esta inactivo o
     * pertenece a otro tenant. Se protege con {@code proveedor:actualizar} (ver
     * nota de autorizacion de la clase).
     *
     * @param id identificador del Proveedor.
     * @return 200 OK con el {@link ProveedorDto} desactivado.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('proveedor','actualizar')")
    public ResponseEntity<ProveedorDto> eliminar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioProveedores.desactivarProveedor(id));
    }

    /**
     * Lista los Proveedores activos del tenant de forma paginada (20 por defecto,
     * 100 maximo) filtrando por nombre o RFC sin distinguir mayusculas (Req 29.3,
     * 29.6). Un {@code filtro} nulo o en blanco lista todos; el {@code size}
     * superior al maximo se acota a 100.
     *
     * @param filtro subcadena a buscar en nombre o RFC; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ProveedorDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('proveedor','listar')")
    public PaginaResponse<ProveedorDto> listar(
            @RequestParam(name = "filtro", required = false) String filtro,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioProveedores.listarProveedores(filtro, pageable));
    }
}
