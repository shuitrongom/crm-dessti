package com.dessti.crm.compras.recepcion.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.compras.recepcion.application.RecepcionMercanciaDto;
import com.dessti.crm.compras.recepcion.application.RegistrarPartidaRecepcionCommand;
import com.dessti.crm.compras.recepcion.application.RegistrarRecepcionCommand;
import com.dessti.crm.compras.recepcion.application.ServicioRecepciones;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo compras-abastecimiento para la gestion de
 * las {@link RecepcionMercanciaDto Recepciones de Mercancia} (Req 32, 12; tarea
 * 27.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /compras/recepciones} — alta
 *       ({@code @autorizador.tiene('recepcion_mercancia','crear')}); 201 Created.
 *       422 si la Orden_Compra no admite recepciones (Req 32.2) o si el acumulado
 *       excede lo ordenado (Req 32.3); 404 si la Orden_Compra no es accesible.</li>
 *   <li>{@code GET /compras/recepciones/{id}} — consulta
 *       ({@code @autorizador.tiene('recepcion_mercancia','leer')}); 200 OK; 404 si
 *       no es accesible (Req 23.3).</li>
 *   <li>{@code GET /compras/recepciones?ordenCompraId=&page=&size=} — listado
 *       paginado (20/100) con filtro por Orden_Compra
 *       ({@code @autorizador.tiene('recepcion_mercancia','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 32.7).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.5)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code recepcion_mercancia:{crear,leer,listar}} ya se sembraron en V5 y se
 * asignaron al rol {@code almacen} (Req 27.5), por lo que V29 no requiere sembrar
 * permisos de recepcion.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 404 no
 * encontrado).</p>
 */
@RestController
@RequestMapping("/compras/recepciones")
public class RecepcionMercanciaController {

    private final ServicioRecepciones servicioRecepciones;

    public RecepcionMercanciaController(ServicioRecepciones servicioRecepciones) {
        this.servicioRecepciones = servicioRecepciones;
    }

    /**
     * Registra una Recepcion_Mercancia contra una Orden_Compra, actualizando el
     * inventario y derivando el estado de la Orden_Compra (Req 32.1–32.6). 422 si la
     * Orden_Compra no admite recepciones o si el acumulado excede lo ordenado; 404
     * si la Orden_Compra no es accesible.
     *
     * @param request datos de la recepcion a registrar.
     * @return 201 Created con el {@link RecepcionMercanciaDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('recepcion_mercancia','crear')")
    public ResponseEntity<RecepcionMercanciaDto> registrar(
            @Valid @RequestBody RegistrarRecepcionRequest request) {
        List<RegistrarPartidaRecepcionCommand> partidas = request.partidas().stream()
                .map(RecepcionMercanciaController::aComando)
                .toList();
        RecepcionMercanciaDto dto = servicioRecepciones.registrarRecepcion(
                new RegistrarRecepcionCommand(request.ordenCompraId(), partidas));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Recepcion_Mercancia por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la recepcion.
     * @return 200 OK con el {@link RecepcionMercanciaDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('recepcion_mercancia','leer')")
    public ResponseEntity<RecepcionMercanciaDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioRecepciones.consultar(id));
    }

    /**
     * Lista las Recepciones de Mercancia del tenant de forma paginada (20 por
     * defecto, 100 maximo) con filtro opcional por Orden_Compra (Req 32.7).
     *
     * @param ordenCompraId Orden_Compra a filtrar; opcional.
     * @param page          numero de pagina 0-index; opcional.
     * @param size          tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link RecepcionMercanciaDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('recepcion_mercancia','listar')")
    public PaginaResponse<RecepcionMercanciaDto> listar(
            @RequestParam(name = "ordenCompraId", required = false) UUID ordenCompraId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioRecepciones.listar(ordenCompraId, pageable));
    }

    private static RegistrarPartidaRecepcionCommand aComando(PartidaRecepcionRequest request) {
        return new RegistrarPartidaRecepcionCommand(
                request.partidaOrdenCompraId(), request.cantidadRecibida());
    }
}
