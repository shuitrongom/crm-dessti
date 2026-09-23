package com.dessti.crm.compras.requisicion.adapter.in.rest;

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

import com.dessti.crm.compras.ordencompra.application.CrearPartidaOrdenCompraCommand;
import com.dessti.crm.compras.requisicion.application.CrearPartidaRequisicionCommand;
import com.dessti.crm.compras.requisicion.application.CrearRequisicionCompraCommand;
import com.dessti.crm.compras.requisicion.application.GenerarOrdenCompraCommand;
import com.dessti.crm.compras.requisicion.application.RequisicionCompraDto;
import com.dessti.crm.compras.requisicion.application.ServicioRequisiciones;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo compras-abastecimiento para la gestion de
 * las {@link RequisicionCompraDto Requisiciones de Compra} (Req 30, 12; tarea 26.2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /compras/requisiciones} — alta
 *       ({@code @autorizador.tiene('requisicion_compra','crear')}); 201 Created. 422
 *       si los datos son invalidos; 404 si algun Material no existe (Req 30.1).</li>
 *   <li>{@code GET /compras/requisiciones/{id}} — consulta
 *       ({@code @autorizador.tiene('requisicion_compra','leer')}); 200 OK; 404 si no
 *       es accesible (Req 23.3).</li>
 *   <li>{@code PUT /compras/requisiciones/{id}/estado} — cambio de estado
 *       ({@code @autorizador.tiene('requisicion_compra','cambiar_estado')}); 200 OK;
 *       409 si la transicion es invalida (Req 30.2).</li>
 *   <li>{@code POST /compras/requisiciones/{id}/orden-compra} — generar Orden_Compra
 *       desde una requisicion aprobada
 *       ({@code @autorizador.tiene('requisicion_compra','cambiar_estado')}); 201
 *       Created. 422 si la requisicion no esta aprobada; 404 si el Proveedor o algun
 *       Material no existe (Req 30.3, 30.4).</li>
 *   <li>{@code GET /compras/requisiciones?estado=&page=&size=} — listado paginado
 *       (20/100) con filtro por estado
 *       ({@code @autorizador.tiene('requisicion_compra','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 30.5).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.5)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code requisicion_compra:{crear,leer,listar,cambiar_estado}} ya se sembraron en
 * V5 y se asignaron al rol {@code almacen} (Req 27.5). La generacion de la
 * Orden_Compra se protege con {@code requisicion_compra:cambiar_estado} por ser una
 * accion que consume el estado aprobado de la requisicion; la Orden resultante
 * pertenece al recurso {@code orden_compra} y su creacion queda auditada. V28 no
 * requiere sembrar permisos adicionales.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/compras/requisiciones")
public class RequisicionCompraController {

    private final ServicioRequisiciones servicioRequisiciones;

    public RequisicionCompraController(ServicioRequisiciones servicioRequisiciones) {
        this.servicioRequisiciones = servicioRequisiciones;
    }

    /**
     * Da de alta una Requisicion_Compra en estado inicial {@code borrador} con al
     * menos una partida (Req 30.1). 422 si los datos son invalidos; 404 si algun
     * Material no existe.
     *
     * @param request datos de la Requisicion_Compra a crear.
     * @return 201 Created con el {@link RequisicionCompraDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('requisicion_compra','crear')")
    public ResponseEntity<RequisicionCompraDto> crear(
            @Valid @RequestBody CrearRequisicionCompraRequest request) {
        List<CrearPartidaRequisicionCommand> partidas = request.partidas().stream()
                .map(p -> new CrearPartidaRequisicionCommand(p.materialId(), p.cantidad()))
                .toList();
        RequisicionCompraDto dto = servicioRequisiciones.crearRequisicion(
                new CrearRequisicionCompraCommand(partidas));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Requisicion_Compra por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la Requisicion_Compra.
     * @return 200 OK con el {@link RequisicionCompraDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('requisicion_compra','leer')")
    public ResponseEntity<RequisicionCompraDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioRequisiciones.consultarRequisicion(id));
    }

    /**
     * Cambia el estado de una Requisicion_Compra segun la maquina de estados
     * (Req 30.2). 409 si la transicion es invalida; 404 si no es accesible; 422 si
     * la etiqueta es desconocida.
     *
     * @param id      identificador de la Requisicion_Compra.
     * @param request etiqueta del estado destino.
     * @return 200 OK con el {@link RequisicionCompraDto} en su nuevo estado.
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('requisicion_compra','cambiar_estado')")
    public ResponseEntity<RequisicionCompraDto> cambiarEstado(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CambiarEstadoRequisicionRequest request) {
        return ResponseEntity.ok(servicioRequisiciones.cambiarEstado(id, request.estado()));
    }

    /**
     * Genera una Orden_Compra a partir de una Requisicion_Compra aprobada (Req 30.3,
     * 30.4). 422 si la requisicion no esta aprobada; 404 si el Proveedor o algun
     * Material no existe. Devuelve el {@link RequisicionCompraDto} con la
     * Orden_Compra generada enlazada ({@code ordenCompraId}).
     *
     * @param id      identificador de la Requisicion_Compra aprobada.
     * @param request Proveedor y partidas (con precio) de la Orden a generar.
     * @return 201 Created con el {@link RequisicionCompraDto} enlazado a la Orden.
     */
    @PostMapping("/{id}/orden-compra")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('requisicion_compra','cambiar_estado')")
    public ResponseEntity<RequisicionCompraDto> generarOrdenCompra(
            @PathVariable("id") UUID id,
            @Valid @RequestBody GenerarOrdenCompraRequest request) {
        List<CrearPartidaOrdenCompraCommand> partidas = request.partidas().stream()
                .map(p -> new CrearPartidaOrdenCompraCommand(
                        p.materialId(), p.cantidad(), p.precioUnitario()))
                .toList();
        RequisicionCompraDto dto = servicioRequisiciones.generarOrdenCompra(
                id, new GenerarOrdenCompraCommand(request.proveedorId(), partidas));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista las Requisiciones de Compra del tenant de forma paginada (20 por
     * defecto, 100 maximo) con filtro opcional por estado (Req 30.5).
     *
     * @param estado etiqueta de estado a filtrar; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link RequisicionCompraDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('requisicion_compra','listar')")
    public PaginaResponse<RequisicionCompraDto> listar(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioRequisiciones.listarRequisiciones(estado, pageable));
    }
}
