package com.dessti.crm.vertical.anuncios.levantamiento.adapter.in.rest;

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

import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoFotoDto;
import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoSitioDetalleDto;
import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoSitioDto;
import com.dessti.crm.vertical.anuncios.levantamiento.application.ServicioLevantamientos;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo operacion-produccion para la gestion de los
 * {@link LevantamientoSitioDto Levantamientos de Sitio} (Req 16, 12; tarea 21.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /levantamientos} — alta con datos obligatorios y vinculos
 *       opcionales ({@code @autorizador.tiene('levantamiento_sitio','crear')});
 *       201 Created con el id y estado {@code en_proceso} (Req 16.1, 16.2). 404 si
 *       un vinculo no existe (Req 16.2, 23.3).</li>
 *   <li>{@code GET /levantamientos/{id}} — consulta de detalle enriquecida con las
 *       fotos vinculadas ({@code @autorizador.tiene('levantamiento_sitio','leer')});
 *       200 OK con {@link LevantamientoSitioDetalleDto}; 404 si no es accesible
 *       (Req 7.1, 7.2, 23.3).</li>
 *   <li>{@code GET /levantamientos/{id}/fotos} — lista de fotos vinculadas
 *       ({@code @autorizador.tiene('levantamiento_sitio','leer')}); 200 OK con la
 *       lista (posiblemente vacia); 404 si no es accesible (Req 7.2, 7.3).</li>
 *   <li>{@code POST /levantamientos/{id}/fotos} — adjuntar fotografias
 *       ({@code @autorizador.tiene('levantamiento_sitio','crear')}); 201 Created
 *       con las fotos (Req 16.3).</li>
 *   <li>{@code POST /levantamientos/{id}/completar} — marcar completado
 *       ({@code @autorizador.tiene('levantamiento_sitio','cambiar_estado')}); 200
 *       OK; 409 si ya estaba completado (Req 16.4).</li>
 *   <li>{@code GET /levantamientos?estado=&sitioId=&page=&size=} — listado
 *       paginado (20/100) con filtros
 *       ({@code @autorizador.tiene('levantamiento_sitio','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 16.6).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.6)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code levantamiento_sitio:{crear,leer,listar,cambiar_estado}} ya se sembraron en
 * V5 y se asignaron al rol {@code instalacion} (Req 27.6), por lo que la migracion
 * V19 no requiere sembrar permisos adicionales. La operacion "completar" reutiliza
 * el permiso {@code cambiar_estado} (es un cambio de estado del agregado); adjuntar
 * fotografias reutiliza {@code crear}.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/levantamientos")
public class LevantamientoSitioController {

    private final ServicioLevantamientos servicioLevantamientos;

    public LevantamientoSitioController(ServicioLevantamientos servicioLevantamientos) {
        this.servicioLevantamientos = servicioLevantamientos;
    }

    /**
     * Crea un Levantamiento_Sitio con los datos obligatorios y vinculos opcionales
     * (Req 16.1, 16.2). Devuelve 201 con el id y estado {@code en_proceso}.
     *
     * @param request cuerpo con los datos del levantamiento.
     * @return 201 Created con el {@link LevantamientoSitioDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('levantamiento_sitio','crear')")
    public ResponseEntity<LevantamientoSitioDto> crear(
            @Valid @RequestBody CrearLevantamientoRequest request) {
        LevantamientoSitioDto dto = servicioLevantamientos.crear(request.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Levantamiento_Sitio por su identificador, enriquecido con sus
     * fotografias vinculadas (Req 7.1, 7.2, 23.3; diseno §C1). La lista de fotos es
     * vacia si el Levantamiento no tiene ninguna. 404 si no es accesible.
     *
     * @param id identificador del Levantamiento_Sitio.
     * @return 200 OK con el {@link LevantamientoSitioDetalleDto} (info + fotos).
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('levantamiento_sitio','leer')")
    public ResponseEntity<LevantamientoSitioDetalleDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioLevantamientos.consultarDetalle(id));
    }

    /**
     * Lista las fotografias vinculadas a un Levantamiento_Sitio accesible (Req 7.2;
     * diseno §C1). Devuelve una lista vacia cuando el Levantamiento no tiene fotos;
     * 404 si el Levantamiento no es accesible (Req 7.3).
     *
     * @param id identificador del Levantamiento_Sitio.
     * @return 200 OK con la lista de {@link LevantamientoFotoDto} (posiblemente vacia).
     */
    @GetMapping("/{id}/fotos")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('levantamiento_sitio','leer')")
    public ResponseEntity<List<LevantamientoFotoDto>> fotos(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioLevantamientos.fotosDe(id));
    }

    /**
     * Adjunta fotografias a un Levantamiento_Sitio (Req 16.3). Devuelve 201 con las
     * fotografias adjuntadas.
     *
     * @param id      identificador del Levantamiento_Sitio.
     * @param request cuerpo con las referencias de las fotografias.
     * @return 201 Created con la lista de {@link LevantamientoFotoDto}.
     */
    @PostMapping("/{id}/fotos")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('levantamiento_sitio','crear')")
    public ResponseEntity<List<LevantamientoFotoDto>> agregarFotos(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AgregarFotosLevantamientoRequest request) {
        List<LevantamientoFotoDto> fotos =
                servicioLevantamientos.agregarFotos(id, request.referencias());
        return ResponseEntity.status(HttpStatus.CREATED).body(fotos);
    }

    /**
     * Marca un Levantamiento_Sitio como {@code completado}, registrando actor y
     * marca UTC (Req 16.4). 409 si ya estaba completado; 404 si no es accesible.
     *
     * @param id identificador del Levantamiento_Sitio.
     * @return 200 OK con el {@link LevantamientoSitioDto} completado.
     */
    @PostMapping("/{id}/completar")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('levantamiento_sitio','cambiar_estado')")
    public ResponseEntity<LevantamientoSitioDto> completar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioLevantamientos.completar(id));
    }

    /**
     * Lista los Levantamientos del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por estado y por Sitio (Req 16.6).
     *
     * @param estado  etiqueta de estado a filtrar; opcional.
     * @param sitioId Sitio a filtrar; opcional.
     * @param page    numero de pagina 0-index; opcional.
     * @param size    tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link LevantamientoSitioDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('levantamiento_sitio','listar')")
    public PaginaResponse<LevantamientoSitioDto> listar(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "sitioId", required = false) UUID sitioId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioLevantamientos.listar(estado, sitioId, pageable));
    }
}
