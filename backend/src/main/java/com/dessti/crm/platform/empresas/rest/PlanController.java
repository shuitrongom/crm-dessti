package com.dessti.crm.platform.empresas.rest;

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

import com.dessti.crm.platform.empresas.ActualizarPlanCommand;
import com.dessti.crm.platform.empresas.CrearPlanCommand;
import com.dessti.crm.platform.empresas.PlanDto;
import com.dessti.crm.platform.empresas.ServicioPlanes;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para la gestion de plataforma de {@link PlanDto
 * Planes} por el {@code super_admin} (Req 25, tarea 14.2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /planes} — define un Plan (Req 25.1).</li>
 *   <li>{@code PUT /planes/{id}} — actualiza los limites de un Plan (Req 25.1).</li>
 *   <li>{@code GET /planes/{id}} — consulta un Plan (Req 25.1).</li>
 *   <li>{@code GET /planes} — lista paginada (20/100) (Req 25.1).</li>
 *   <li>{@code DELETE /planes/{id}} — elimina un Plan (Req 25.1); 204. Rechazado
 *       con 422 si alguna Empresa lo tiene asignado.</li>
 * </ul>
 *
 * <h2>Autorizacion de plataforma</h2>
 * <p>Cada operacion exige el permiso atomico de plataforma correspondiente sobre
 * el recurso {@code plan} ({@code plan:crear}, {@code plan:actualizar},
 * {@code plan:leer}, {@code plan:listar}, {@code plan:eliminar}), sembrados en
 * V5 y V56 y asignados <strong>unicamente</strong> al rol {@code super_admin};
 * cualquier otro usuario recibe 403 (denegacion por defecto, Req 3.2).</p>
 */
@RestController
@RequestMapping("/planes")
public class PlanController {

    private final ServicioPlanes servicioPlanes;

    public PlanController(ServicioPlanes servicioPlanes) {
        this.servicioPlanes = servicioPlanes;
    }

    /**
     * Define un Plan (Req 25.1). Un nombre duplicado produce 409.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('plan','crear')")
    public ResponseEntity<PlanDto> crear(@Valid @RequestBody CrearPlanRequest request) {
        PlanDto dto = servicioPlanes.crearPlan(new CrearPlanCommand(
                request.nombre(), request.maxUsuarios(), request.duracionDias(),
                request.giroId(), request.monedaCodigo(), request.preciosModulos()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Actualiza los limites de un Plan (Req 25.1). Un Plan inexistente produce
     * 404; un nombre en conflicto con otro Plan, 409.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('plan','actualizar')")
    public ResponseEntity<PlanDto> actualizar(@PathVariable("id") UUID id,
                                              @Valid @RequestBody ActualizarPlanRequest request) {
        PlanDto dto = servicioPlanes.actualizarPlan(id, new ActualizarPlanCommand(
                request.nombre(), request.maxUsuarios(), request.duracionDias(),
                request.giroId(), request.monedaCodigo(), request.preciosModulos()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Consulta un Plan por su identificador (Req 25.1). Un Plan inexistente
     * produce 404.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('plan','leer')")
    public ResponseEntity<PlanDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPlanes.consultarPlan(id));
    }

    /**
     * Lista Planes de forma paginada (20 por defecto, 100 maximo) (Req 25.1).
     *
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return la pagina de Planes proyectada a {@link PlanDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('plan','listar')")
    public PaginaResponse<PlanDto> listar(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPlanes.listarPlanes(pageable), PlanDto::de);
    }

    /**
     * Elimina definitivamente un Plan (Req 25.1). Un Plan inexistente produce
     * 404; un Plan aun asignado a alguna Empresa (referenciado por una
     * Suscripcion) produce 422 con el conteo y como resolverlo.
     *
     * @param id identificador del Plan.
     * @return 204 No Content si la eliminacion se aplica.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('plan','eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable("id") UUID id) {
        servicioPlanes.eliminarPlan(id);
        return ResponseEntity.noContent().build();
    }
}
