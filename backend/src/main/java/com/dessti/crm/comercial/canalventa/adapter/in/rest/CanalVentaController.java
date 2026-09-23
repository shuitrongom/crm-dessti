package com.dessti.crm.comercial.canalventa.adapter.in.rest;

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

import com.dessti.crm.comercial.canalventa.application.ActualizarCanalVentaCommand;
import com.dessti.crm.comercial.canalventa.application.CanalVentaDto;
import com.dessti.crm.comercial.canalventa.application.CrearCanalVentaCommand;
import com.dessti.crm.comercial.canalventa.application.ServicioCanalesVenta;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del submodulo comercial-crm para la gestion del
 * catalogo de {@link CanalVentaDto Canales de Venta} (Req 63, 12; tarea 17.3).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /canales-venta} — alta
 *       ({@code @autorizador.tiene('canal_venta','crear')}); 201 Created. 422 si
 *       los datos son invalidos; 409 si el nombre ya existe entre activos
 *       (Req 63.1).</li>
 *   <li>{@code GET /canales-venta/{id}} — consulta
 *       ({@code @autorizador.tiene('canal_venta','leer')}); 200 OK. 404 si no
 *       existe/otro tenant (Req 4.3, 23.3).</li>
 *   <li>{@code PUT /canales-venta/{id}} — actualizacion
 *       ({@code @autorizador.tiene('canal_venta','actualizar')}); 200 OK.
 *       404/409/422 segun corresponda.</li>
 *   <li>{@code DELETE /canales-venta/{id}} — baja logica
 *       ({@code @autorizador.tiene('canal_venta','eliminar')}); 200 OK con el DTO
 *       desactivado (Req 63.1).</li>
 *   <li>{@code GET /canales-venta?filtro=&page=&size=} — listado paginado (20/100)
 *       filtrable por nombre ({@code @autorizador.tiene('canal_venta','listar')});
 *       200 OK con {@link PaginaResponse} (Req 63.1).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 63)</h2>
 * <p>Cada endpoint exige el permiso atomico {@code canal_venta:{crear,leer,listar,
 * actualizar,eliminar}} via {@code @autorizador.tiene(recurso, operacion)}. Esos
 * permisos se sembraron en V15 y se asignaron al rol {@code ventas} (gestion
 * completa) y, en lectura, a {@code gerente} y {@code marketing} (Req 27.2/27.8/
 * 27.15). La <em>asignacion</em> del canal a una Oportunidad/Cotizacion NO se
 * gestiona aqui: la exponen los controladores de esos recursos bajo su permiso de
 * actualizacion (ver {@code OportunidadController}/{@code CotizacionController}).</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 conflicto
 * de unicidad, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/canales-venta")
public class CanalVentaController {

    private final ServicioCanalesVenta servicioCanalesVenta;

    public CanalVentaController(ServicioCanalesVenta servicioCanalesVenta) {
        this.servicioCanalesVenta = servicioCanalesVenta;
    }

    /**
     * Da de alta un Canal_Venta (Req 63.1). 422 si los datos son invalidos; 409
     * si ya existe un canal activo con el mismo nombre.
     *
     * @param request datos del canal a crear.
     * @return 201 Created con el {@link CanalVentaDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('canal_venta','crear')")
    public ResponseEntity<CanalVentaDto> crear(@Valid @RequestBody CrearCanalVentaRequest request) {
        CanalVentaDto dto = servicioCanalesVenta.crearCanal(
                new CrearCanalVentaCommand(request.nombre(), request.descripcion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Canal_Venta por su identificador (Req 4.3, 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador del canal.
     * @return 200 OK con el {@link CanalVentaDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('canal_venta','leer')")
    public ResponseEntity<CanalVentaDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCanalesVenta.consultarCanal(id));
    }

    /**
     * Actualiza los datos de un Canal_Venta activo (Req 63.1). 404 si no es
     * accesible; 409 si el nuevo nombre colisiona con otro canal activo; 422 si
     * los datos son invalidos.
     *
     * @param id      identificador del canal.
     * @param request nuevos datos.
     * @return 200 OK con el {@link CanalVentaDto} actualizado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('canal_venta','actualizar')")
    public ResponseEntity<CanalVentaDto> actualizar(@PathVariable("id") UUID id,
                                                    @Valid @RequestBody ActualizarCanalVentaRequest request) {
        CanalVentaDto dto = servicioCanalesVenta.actualizarCanal(id,
                new ActualizarCanalVentaCommand(request.nombre(), request.descripcion()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Realiza el borrado logico de un Canal_Venta activo (Req 63.1). 200 OK con
     * el {@link CanalVentaDto} desactivado; 404 si no existe/ya inactivo/otro
     * tenant.
     *
     * @param id identificador del canal.
     * @return 200 OK con el {@link CanalVentaDto} desactivado.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('canal_venta','eliminar')")
    public ResponseEntity<CanalVentaDto> eliminar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCanalesVenta.desactivarCanal(id));
    }

    /**
     * Lista los Canales de Venta activos del tenant de forma paginada (20 por
     * defecto, 100 maximo) filtrando por nombre sin distinguir mayusculas
     * (Req 63.1).
     *
     * @param filtro subcadena a buscar en el nombre; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link CanalVentaDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('canal_venta','listar')")
    public PaginaResponse<CanalVentaDto> listar(
            @RequestParam(name = "filtro", required = false) String filtro,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioCanalesVenta.listarCanales(filtro, pageable));
    }
}
