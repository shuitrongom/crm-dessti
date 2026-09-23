package com.dessti.crm.compras.ordencompra.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.compras.ordencompra.application.CrearOrdenCompraCommand;
import com.dessti.crm.compras.ordencompra.application.CrearPartidaOrdenCompraCommand;
import com.dessti.crm.compras.ordencompra.application.OrdenCompraDto;
import com.dessti.crm.compras.ordencompra.application.ServicioOrdenesCompra;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo compras-abastecimiento para la gestion de
 * las {@link OrdenCompraDto Ordenes de Compra} (Req 31, 12; tarea 26.3).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /compras/ordenes-compra} — alta
 *       ({@code @autorizador.tiene('orden_compra','crear')}); 201 Created. 422 si
 *       los datos son invalidos; 404 si el Proveedor o algun Material no existe
 *       (Req 31.1, 31.2).</li>
 *   <li>{@code GET /compras/ordenes-compra/{id}} — consulta
 *       ({@code @autorizador.tiene('orden_compra','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 *   <li>{@code PUT /compras/ordenes-compra/{id}/estado} — cambio de estado
 *       ({@code @autorizador.tiene('orden_compra','cambiar_estado')}); 200 OK; 409
 *       si la transicion es invalida (Req 31.5, 31.6).</li>
 *   <li>{@code GET /compras/ordenes-compra?proveedorId=&estado=&page=&size=} —
 *       listado paginado (20/100) con filtros
 *       ({@code @autorizador.tiene('orden_compra','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 31.7).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.5)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code orden_compra:{crear,leer,listar,cambiar_estado}} ya se sembraron en V5 y
 * se asignaron al rol {@code almacen} (Req 27.5), por lo que V28 no requiere
 * sembrar permisos adicionales.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/compras/ordenes-compra")
public class OrdenCompraController {

    private final ServicioOrdenesCompra servicioOrdenesCompra;

    public OrdenCompraController(ServicioOrdenesCompra servicioOrdenesCompra) {
        this.servicioOrdenesCompra = servicioOrdenesCompra;
    }

    /**
     * Da de alta una Orden_Compra en estado inicial {@code abierta} con un
     * Proveedor existente y entre 1 y 500 partidas (Req 31.1, 31.4). 422 si los
     * datos son invalidos; 404 si el Proveedor o algun Material no existe.
     *
     * @param request datos de la Orden_Compra a crear.
     * @return 201 Created con el {@link OrdenCompraDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('orden_compra','crear')")
    public ResponseEntity<OrdenCompraDto> crear(@Valid @RequestBody CrearOrdenCompraRequest request) {
        List<CrearPartidaOrdenCompraCommand> partidas = request.partidas().stream()
                .map(OrdenCompraController::aComando)
                .toList();
        OrdenCompraDto dto = servicioOrdenesCompra.crearOrdenCompra(
                new CrearOrdenCompraCommand(request.proveedorId(), null, partidas));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Orden_Compra por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la Orden_Compra.
     * @return 200 OK con el {@link OrdenCompraDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('orden_compra','leer')")
    public ResponseEntity<OrdenCompraDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioOrdenesCompra.consultarOrdenCompra(id));
    }

    /**
     * Cambia el estado de una Orden_Compra segun la maquina de estados (Req 31.5,
     * 31.6). 409 si la transicion es invalida; 404 si no es accesible; 422 si la
     * etiqueta es desconocida.
     *
     * @param id      identificador de la Orden_Compra.
     * @param request etiqueta del estado destino.
     * @return 200 OK con el {@link OrdenCompraDto} en su nuevo estado.
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('orden_compra','cambiar_estado')")
    public ResponseEntity<OrdenCompraDto> cambiarEstado(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CambiarEstadoOrdenCompraRequest request) {
        return ResponseEntity.ok(servicioOrdenesCompra.cambiarEstado(id, request.estado()));
    }

    /**
     * Lista las Ordenes de Compra del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por Proveedor y estado (Req 31.7).
     *
     * @param proveedorId Proveedor a filtrar; opcional.
     * @param estado      etiqueta de estado a filtrar; opcional.
     * @param page        numero de pagina 0-index; opcional.
     * @param size        tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link OrdenCompraDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('orden_compra','listar')")
    public PaginaResponse<OrdenCompraDto> listar(
            @RequestParam(name = "proveedorId", required = false) UUID proveedorId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(
                servicioOrdenesCompra.listarOrdenesCompra(proveedorId, estado, pageable));
    }

    private static CrearPartidaOrdenCompraCommand aComando(PartidaOrdenCompraRequest request) {
        return new CrearPartidaOrdenCompraCommand(
                request.materialId(), request.cantidad(), request.precioUnitario());
    }
}
