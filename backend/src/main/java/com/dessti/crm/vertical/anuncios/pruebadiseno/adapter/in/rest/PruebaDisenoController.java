package com.dessti.crm.vertical.anuncios.pruebadiseno.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.vertical.anuncios.pruebadiseno.application.PruebaDisenoDto;
import com.dessti.crm.vertical.anuncios.pruebadiseno.application.ResultadoRechazoPruebaDiseno;
import com.dessti.crm.vertical.anuncios.pruebadiseno.application.ServicioPruebasDiseno;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

/**
 * Adaptador de entrada REST del vertical de anuncios para la gestion de las
 * {@link PruebaDisenoDto Pruebas de Diseno} versionadas (Req 15, 12; tarea 18.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /cotizaciones/{cotizacionId}/pruebas-diseno} — generar version 1
 *       ({@code @autorizador.tiene('prueba_diseno','crear')}); 201 Created. 404 si
 *       la Cotizacion no existe (Req 15.1).</li>
 *   <li>{@code POST /pruebas-diseno/{id}/aprobar} — aprobar
 *       ({@code @autorizador.tiene('prueba_diseno','cambiar_estado')}); 200 OK; 409
 *       si no esta {@code pendiente} (Req 15.2, 15.4).</li>
 *   <li>{@code POST /pruebas-diseno/{id}/rechazar} — rechazar y generar la nueva
 *       version ({@code @autorizador.tiene('prueba_diseno','cambiar_estado')}); 200
 *       OK con la version rechazada y la nueva pendiente; 409 si no esta
 *       {@code pendiente} (Req 15.3, 15.4).</li>
 *   <li>{@code GET /cotizaciones/{cotizacionId}/pruebas-diseno?page=&size=} —
 *       listado paginado (20/100)
 *       ({@code @autorizador.tiene('prueba_diseno','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 15.6).</li>
 * </ul>
 *
 * <h2>Inmutabilidad del historial (Req 15.4)</h2>
 * <p>No se exponen endpoints de modificacion ni de borrado de Prueba_Diseno. Solo
 * la transicion de estado {@code pendiente -> {aprobada|rechazada}} sobre la
 * version pendiente vigente; toda decision sobre una prueba ya decidida devuelve
 * 409.</p>
 *
 * <h2>Autorizacion (Req 3, 27.3)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code prueba_diseno:{crear,leer,listar,cambiar_estado}} ya se sembraron en V5 y
 * se asignaron al rol {@code diseno} (Req 27.3), por lo que V16 no siembra permisos
 * adicionales. La aprobacion y el rechazo usan {@code cambiar_estado}.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se devuelven DTOs distintos de las entidades JPA; el {@code tenant_id} y el
 * actor se derivan del contexto. El manejo de errores lo centraliza
 * {@code ManejadorGlobalErrores} (409 transicion invalida, 404 no encontrado).</p>
 */
@RestController
public class PruebaDisenoController {

    private final ServicioPruebasDiseno servicioPruebasDiseno;

    public PruebaDisenoController(ServicioPruebasDiseno servicioPruebasDiseno) {
        this.servicioPruebasDiseno = servicioPruebasDiseno;
    }

    /**
     * Genera la Prueba_Diseno inicial (version 1, {@code pendiente}) de una
     * Cotizacion existente (Req 15.1). 404 si la Cotizacion no existe en el tenant.
     *
     * @param cotizacionId identificador de la Cotizacion.
     * @return 201 Created con el {@link PruebaDisenoDto} generado.
     */
    @PostMapping("/cotizaciones/{cotizacionId}/pruebas-diseno")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('prueba_diseno','crear')")
    public ResponseEntity<PruebaDisenoDto> generar(@PathVariable("cotizacionId") UUID cotizacionId) {
        PruebaDisenoDto dto = servicioPruebasDiseno.generar(cotizacionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Aprueba una Prueba_Diseno en estado {@code pendiente} (Req 15.2). 409 si ya
     * esta decidida; 404 si no es accesible.
     *
     * @param id identificador de la Prueba_Diseno.
     * @return 200 OK con el {@link PruebaDisenoDto} aprobado.
     */
    @PostMapping("/pruebas-diseno/{id}/aprobar")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('prueba_diseno','cambiar_estado')")
    public ResponseEntity<PruebaDisenoDto> aprobar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPruebasDiseno.aprobar(id));
    }

    /**
     * Rechaza una Prueba_Diseno en estado {@code pendiente} y genera la nueva
     * version pendiente con numero incrementado en 1 (Req 15.3, Property 8). 409 si
     * ya esta decidida; 404 si no es accesible.
     *
     * @param id identificador de la Prueba_Diseno a rechazar.
     * @return 200 OK con la version rechazada y la nueva version pendiente.
     */
    @PostMapping("/pruebas-diseno/{id}/rechazar")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('prueba_diseno','cambiar_estado')")
    public ResponseEntity<ResultadoRechazoPruebaDiseno> rechazar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPruebasDiseno.rechazar(id));
    }

    /**
     * Lista de forma paginada (20 por defecto, 100 maximo) las Prueba_Diseno de una
     * Cotizacion, mas reciente primero (Req 15.6).
     *
     * @param cotizacionId Cotizacion cuyas pruebas se listan.
     * @param page         numero de pagina 0-index; opcional.
     * @param size         tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link PruebaDisenoDto}.
     */
    @GetMapping("/cotizaciones/{cotizacionId}/pruebas-diseno")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('prueba_diseno','listar')")
    public PaginaResponse<PruebaDisenoDto> listar(
            @PathVariable("cotizacionId") UUID cotizacionId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPruebasDiseno.listar(cotizacionId, pageable));
    }
}
