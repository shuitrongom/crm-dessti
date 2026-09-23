package com.dessti.crm.operacion.produccion.adapter.in.rest;

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

import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;
import com.dessti.crm.operacion.produccion.application.CrearOrdenDirectaCommand;
import com.dessti.crm.operacion.produccion.application.OrdenFabricacionDetalleDto;
import com.dessti.crm.operacion.produccion.application.OrdenFabricacionDto;
import com.dessti.crm.operacion.produccion.application.ServicioOrdenesFabricacion;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del vertical de anuncios para la gestion de las
 * {@link OrdenFabricacionDto Ordenes de Fabricacion} (Req 7, 12, 15.5; tarea 19.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /ordenes-fabricacion} — generar a partir de una Cotizacion
 *       aprobada ({@code @autorizador.tiene('orden_fabricacion','crear')}); 201
 *       Created con el id (Req 7.1). 404 si la Cotizacion no existe; 422 si no esta
 *       aprobada (Req 7.2) o no tiene Prueba_Diseno aprobada (Req 15.5); 409 si ya
 *       tiene una OF (Req 7.3).</li>
 *   <li>{@code GET /ordenes-fabricacion/{id}} — consulta
 *       ({@code @autorizador.tiene('orden_fabricacion','leer')}); 200 OK; 404 si no
 *       es accesible (Req 23.3).</li>
 *   <li>{@code PUT /ordenes-fabricacion/{id}/estado} — cambio de estado
 *       ({@code @autorizador.tiene('orden_fabricacion','cambiar_estado')}); 200 OK;
 *       409 si la transicion es invalida (Req 7.5, 7.6).</li>
 *   <li>{@code GET /ordenes-fabricacion?estado=&clienteId=&page=&size=} — listado
 *       paginado (20/100) con filtros
 *       ({@code @autorizador.tiene('orden_fabricacion','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 7.7, 7.9).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.4)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code orden_fabricacion:{crear,leer,listar,cambiar_estado}} ya se sembraron en
 * V5 y se asignaron al rol {@code produccion} (Req 27.4), por lo que la migracion
 * V17 no requiere sembrar permisos adicionales.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida / conflicto de unicidad, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/ordenes-fabricacion")
public class OrdenFabricacionController {

    private final ServicioOrdenesFabricacion servicioOrdenesFabricacion;

    public OrdenFabricacionController(ServicioOrdenesFabricacion servicioOrdenesFabricacion) {
        this.servicioOrdenesFabricacion = servicioOrdenesFabricacion;
    }

    /**
     * Genera una Orden_Fabricacion a partir de una Cotizacion aprobada, aplicando
     * las tres precondiciones (Property 7). Devuelve 201 con el id (Req 7.1).
     *
     * @param request cuerpo con el identificador de la Cotizacion.
     * @return 201 Created con el {@link OrdenFabricacionDto} generado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('orden_fabricacion','crear')")
    public ResponseEntity<OrdenFabricacionDto> generar(
            @Valid @RequestBody GenerarOrdenFabricacionRequest request) {
        OrdenFabricacionDto dto = servicioOrdenesFabricacion.generar(request.cotizacionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Crea una Orden_Fabricacion por el <strong>origen generico</strong> (Req 1.1):
     * asociada directamente a un Cliente, sin Cotizacion. Devuelve 201 con el DTO.
     *
     * <p>Mismo gating que el resto de endpoints de OF: modulo {@code operacion} +
     * permiso {@code orden_fabricacion:crear}, <em>sin</em> {@code giroCorresponde}
     * (§A2), por lo que es accesible a cualquier giro. 422 si falta el Cliente o una
     * cantidad es &le; 0; 404 si el Cliente no es accesible en el tenant (Req 1.4,
     * 1.5). La accesibilidad del Material y la persistencia de partidas las cablea la
     * tarea 3.1 (§B1); aqui se persiste la OF y se aceptan las partidas del cuerpo.</p>
     *
     * @param request cuerpo con {@code clienteId} y las partidas iniciales.
     * @return 201 Created con el {@link OrdenFabricacionDto} creado ({@code cotizacionId}
     *         nulo).
     */
    @PostMapping("/directa")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('orden_fabricacion','crear')")
    public ResponseEntity<OrdenFabricacionDto> crearDirecta(
            @Valid @RequestBody CrearOrdenDirectaRequest request) {
        List<CrearOrdenDirectaCommand.PartidaInicial> partidas = request.partidas().stream()
                .map(p -> new CrearOrdenDirectaCommand.PartidaInicial(p.materialId(), p.cantidad()))
                .toList();
        OrdenFabricacionDto dto = servicioOrdenesFabricacion.crearDirecta(
                new CrearOrdenDirectaCommand(request.clienteId(), partidas));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Orden_Fabricacion por su identificador, con el detalle de sus
     * partidas de consumo (Req 5.5, 23.3). 404 si no es accesible.
     *
     * @param id identificador de la Orden_Fabricacion.
     * @return 200 OK con el {@link OrdenFabricacionDetalleDto} (OF + partidas).
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('orden_fabricacion','leer')")
    public ResponseEntity<OrdenFabricacionDetalleDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioOrdenesFabricacion.consultar(id));
    }

    /**
     * Reemplaza las partidas de consumo de una Orden_Fabricacion (§B1, Req 5). Solo
     * se permite mientras la OF esta en {@code pendiente}.
     *
     * <p>Mismo patron de gating que el cambio de estado: modulo {@code operacion} +
     * permiso {@code orden_fabricacion:cambiar_estado}, <em>sin</em>
     * {@code giroCorresponde} (§A2), por lo que es accesible a cualquier giro. 404 si
     * la OF o un Material no son accesibles en el tenant (Req 5.3); 422 si la OF no
     * esta en {@code pendiente} o una cantidad es &le; 0 (Req 5.2).</p>
     *
     * @param id      identificador de la Orden_Fabricacion.
     * @param request cuerpo con las partidas que reemplazan a las existentes.
     * @return 200 OK con el {@link OrdenFabricacionDetalleDto} ya actualizado.
     */
    @PostMapping("/{id}/partidas")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('orden_fabricacion','cambiar_estado')")
    public ResponseEntity<OrdenFabricacionDetalleDto> reemplazarPartidas(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReemplazarPartidasRequest request) {
        List<CrearOrdenDirectaCommand.PartidaInicial> partidas = request.partidas().stream()
                .map(p -> new CrearOrdenDirectaCommand.PartidaInicial(p.materialId(), p.cantidad()))
                .toList();
        return ResponseEntity.ok(servicioOrdenesFabricacion.reemplazarPartidas(id, partidas));
    }

    /**
     * Cambia el estado de una Orden_Fabricacion segun la maquina de estados
     * (Req 7.5, 7.6). 409 si la transicion es invalida; 404 si no es accesible;
     * 422 si la etiqueta es desconocida.
     *
     * @param id      identificador de la Orden_Fabricacion.
     * @param request etiqueta del estado destino.
     * @return 200 OK con el {@link OrdenFabricacionDto} en su nuevo estado.
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('orden_fabricacion','cambiar_estado')")
    public ResponseEntity<OrdenFabricacionDto> cambiarEstado(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CambiarEstadoOrdenFabricacionRequest request) {
        return ResponseEntity.ok(servicioOrdenesFabricacion.cambiarEstado(id, request.estado()));
    }

    /**
     * Lista las Ordenes de Fabricacion del tenant de forma paginada (20 por
     * defecto, 100 maximo) con filtros opcionales por estado y por Cliente
     * (Req 7.7, 7.9).
     *
     * @param estado    etiqueta de estado a filtrar; opcional.
     * @param clienteId Cliente a filtrar; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link OrdenFabricacionDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('orden_fabricacion','listar')")
    public PaginaResponse<OrdenFabricacionDto> listar(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioOrdenesFabricacion.listar(estado, clienteId, pageable));
    }
}
