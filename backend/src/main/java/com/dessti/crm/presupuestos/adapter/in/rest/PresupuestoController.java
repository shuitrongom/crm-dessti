package com.dessti.crm.presupuestos.adapter.in.rest;

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
import com.dessti.crm.presupuestos.application.CrearPresupuestoCommand;
import com.dessti.crm.presupuestos.application.PresupuestoDto;
import com.dessti.crm.presupuestos.application.ServicioPresupuestos;
import com.dessti.crm.presupuestos.application.VariacionPresupuestoDto;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo presupuestos para la gestion de los
 * {@link PresupuestoDto Presupuestos} por area y periodo y la consulta de su variacion
 * (Req 62; Bloque 38). Replica el patron de {@code OrdenFabricacionController}.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /presupuestos} — crear
 *       ({@code @autorizador.tiene('presupuesto','crear')}); 201 Created con el DTO
 *       (Req 62.1). 409 si ya existe para el area y periodo.</li>
 *   <li>{@code GET /presupuestos/{id}} — consulta
 *       ({@code @autorizador.tiene('presupuesto','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 *   <li>{@code PUT /presupuestos/{id}} — actualizar montos estimados
 *       ({@code @autorizador.tiene('presupuesto','actualizar')}); 200 OK (Req 62.5).</li>
 *   <li>{@code GET /presupuestos?area=&periodo=&page=&size=} — listado paginado
 *       (20/100) con filtros ({@code @autorizador.tiene('presupuesto','listar')});
 *       200 OK con {@link PaginaResponse} (Req 62.4).</li>
 *   <li>{@code GET /presupuestos/{id}/variacion} — variacion de solo lectura frente al
 *       real ({@code @autorizador.tiene('presupuesto','leer')}); 200 OK (Req 62.2,
 *       62.3, 62.6, 62.7).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.14)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code presupuesto:{crear,leer,listar,actualizar}} ya se sembraron en V5 y se
 * asignaron a los roles {@code admin_empresa} y {@code gerente} (Req 27.14), por lo que
 * la migracion V40 no requiere sembrar permisos adicionales.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el {@code tenant_id} y
 * el actor se derivan del contexto. El manejo de errores lo centraliza
 * {@code ManejadorGlobalErrores} (422 regla de negocio, 409 conflicto de unicidad,
 * 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/presupuestos")
public class PresupuestoController {

    private final ServicioPresupuestos servicioPresupuestos;

    public PresupuestoController(ServicioPresupuestos servicioPresupuestos) {
        this.servicioPresupuestos = servicioPresupuestos;
    }

    /**
     * Crea un Presupuesto por area y periodo con montos estimados (Req 62.1). Devuelve
     * 201 con el DTO.
     *
     * @param request cuerpo con area, periodo y montos estimados.
     * @return 201 Created con el {@link PresupuestoDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('presupuestos') and @autorizador.tiene('presupuesto','crear')")
    public ResponseEntity<PresupuestoDto> crear(
            @Valid @RequestBody CrearPresupuestoRequest request) {
        PresupuestoDto dto = servicioPresupuestos.crearPresupuesto(new CrearPresupuestoCommand(
                request.area(), request.periodo(),
                request.ingresosEstimados(), request.egresosEstimados()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Presupuesto por su identificador (Req 23.3). 404 si no es accesible.
     *
     * @param id identificador del Presupuesto.
     * @return 200 OK con el {@link PresupuestoDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('presupuestos') and @autorizador.tiene('presupuesto','leer')")
    public ResponseEntity<PresupuestoDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPresupuestos.consultar(id));
    }

    /**
     * Actualiza los montos estimados de un Presupuesto (Req 62.5). 404 si no es
     * accesible; 422 si los montos son invalidos.
     *
     * @param id      identificador del Presupuesto.
     * @param request nuevos montos estimados.
     * @return 200 OK con el {@link PresupuestoDto} actualizado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('presupuestos') and @autorizador.tiene('presupuesto','actualizar')")
    public ResponseEntity<PresupuestoDto> actualizar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarPresupuestoRequest request) {
        return ResponseEntity.ok(servicioPresupuestos.actualizarPresupuesto(
                id, request.ingresosEstimados(), request.egresosEstimados()));
    }

    /**
     * Lista los Presupuestos del tenant de forma paginada (20 por defecto, 100 maximo)
     * con filtros opcionales por area y periodo (Req 62.4).
     *
     * @param area    area a filtrar; opcional.
     * @param periodo periodo a filtrar; opcional.
     * @param page    numero de pagina 0-index; opcional.
     * @param size    tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link PresupuestoDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('presupuestos') and @autorizador.tiene('presupuesto','listar')")
    public PaginaResponse<PresupuestoDto> listar(
            @RequestParam(name = "area", required = false) String area,
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPresupuestos.listar(area, periodo, pageable));
    }

    /**
     * Consulta la variacion de un Presupuesto frente al ejercicio real (Req 62.2,
     * 62.3, 62.6, 62.7). Es de solo lectura: no modifica ningun origen de datos.
     *
     * @param id identificador del Presupuesto.
     * @return 200 OK con el {@link VariacionPresupuestoDto}.
     */
    @GetMapping("/{id}/variacion")
    @PreAuthorize("@autorizador.moduloHabilitado('presupuestos') and @autorizador.tiene('presupuesto','leer')")
    public ResponseEntity<VariacionPresupuestoDto> consultarVariacion(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPresupuestos.consultarVariacion(id));
    }
}
