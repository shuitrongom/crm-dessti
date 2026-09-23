package com.dessti.crm.activosfijos.adapter.in.rest;

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

import com.dessti.crm.activosfijos.application.ActivoFijoDto;
import com.dessti.crm.activosfijos.application.CrearActivoFijoCommand;
import com.dessti.crm.activosfijos.application.DepreciacionDto;
import com.dessti.crm.activosfijos.application.ServicioActivosFijos;
import com.dessti.crm.activosfijos.domain.MetodoDepreciacion;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo de activos fijos para la gestion de los
 * {@link ActivoFijoDto Activos_Fijos} y sus {@link DepreciacionDto Depreciaciones}
 * (Req 44; tarea 32.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /activos-fijos} — alta
 *       ({@code @autorizador.tiene('activo_fijo','crear')}); 201 Created con el id
 *       (Req 44.1). 422 si faltan datos o son invalidos (Req 44.2).</li>
 *   <li>{@code GET /activos-fijos/{id}} — consulta
 *       ({@code @autorizador.tiene('activo_fijo','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 *   <li>{@code GET /activos-fijos?estado=&page=&size=} — listado paginado (20/100)
 *       con filtro por estado ({@code @autorizador.tiene('activo_fijo','listar')});
 *       200 OK con {@link PaginaResponse} (Req 44.5).</li>
 *   <li>{@code POST /activos-fijos/{id}/depreciacion} — corre la depreciacion de un
 *       periodo ({@code @autorizador.tiene('depreciacion','crear')}); 201 Created
 *       con la Depreciacion (Req 44.3). 422 si el bien esta dado de baja o ya esta
 *       totalmente depreciado; 409 si el periodo ya se depreciO.</li>
 *   <li>{@code POST /activos-fijos/{id}/baja} — baja/venta conservando historico
 *       ({@code @autorizador.tiene('activo_fijo','cambiar_estado')}); 200 OK
 *       (Req 44.4); 409 si ya estaba dado de baja.</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. V5 sembro
 * {@code activo_fijo:{crear,leer,listar}} y {@code depreciacion:{crear,leer}}; la
 * migracion V37 siembra {@code activo_fijo:cambiar_estado} para el endpoint de baja
 * (Req 44.4). Todos se asignan al rol {@code contabilidad}.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 conflicto de
 * unicidad / transicion invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/activos-fijos")
public class ActivoFijoController {

    private final ServicioActivosFijos servicioActivosFijos;

    public ActivoFijoController(ServicioActivosFijos servicioActivosFijos) {
        this.servicioActivosFijos = servicioActivosFijos;
    }

    /**
     * Da de alta un Activo_Fijo (Req 44.1). Devuelve 201 con el id.
     *
     * @param request cuerpo con los datos del Activo_Fijo.
     * @return 201 Created con el {@link ActivoFijoDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('activos-fijos') and @autorizador.tiene('activo_fijo','crear')")
    public ResponseEntity<ActivoFijoDto> crear(
            @Valid @RequestBody CrearActivoFijoRequest request) {
        CrearActivoFijoCommand comando = new CrearActivoFijoCommand(
                request.nombre(),
                request.costo(),
                request.fechaAdquisicion(),
                request.vidaUtilMeses(),
                interpretarMetodo(request.metodoDepreciacion()),
                request.valorResidual());
        ActivoFijoDto dto = servicioActivosFijos.crearActivo(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Activo_Fijo por su identificador (Req 23.3). 404 si no es accesible.
     *
     * @param id identificador del Activo_Fijo.
     * @return 200 OK con el {@link ActivoFijoDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('activos-fijos') and @autorizador.tiene('activo_fijo','leer')")
    public ResponseEntity<ActivoFijoDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioActivosFijos.consultar(id));
    }

    /**
     * Lista los Activos_Fijos del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtro opcional por estado (Req 44.5).
     *
     * @param estado etiqueta de estado a filtrar ({@code activo}/{@code baja}); opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ActivoFijoDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('activos-fijos') and @autorizador.tiene('activo_fijo','listar')")
    public PaginaResponse<ActivoFijoDto> listar(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioActivosFijos.listar(estado, pageable));
    }

    /**
     * Corre la depreciacion de un periodo para un Activo_Fijo y genera la
     * Poliza_Contable correspondiente (Req 44.3, 38.2). Devuelve 201 con la
     * Depreciacion. 422 si el bien esta dado de baja o ya esta totalmente
     * depreciado; 409 si el periodo ya se depreciO; 404 si no es accesible.
     *
     * @param id      identificador del Activo_Fijo.
     * @param request cuerpo con el periodo mensual a depreciar.
     * @return 201 Created con el {@link DepreciacionDto} registrado.
     */
    @PostMapping("/{id}/depreciacion")
    @PreAuthorize("@autorizador.moduloHabilitado('activos-fijos') and @autorizador.tiene('depreciacion','crear')")
    public ResponseEntity<DepreciacionDto> depreciar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RegistrarDepreciacionRequest request) {
        DepreciacionDto dto = servicioActivosFijos.depreciar(id, request.periodo());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Da de baja (o registra la venta de) un Activo_Fijo conservando el historico
     * (Req 44.4). 200 OK con el bien en estado {@code baja}; 409 si ya estaba dado
     * de baja; 404 si no es accesible.
     *
     * @param id identificador del Activo_Fijo.
     * @return 200 OK con el {@link ActivoFijoDto} en estado {@code baja}.
     */
    @PostMapping("/{id}/baja")
    @PreAuthorize("@autorizador.moduloHabilitado('activos-fijos') and @autorizador.tiene('activo_fijo','cambiar_estado')")
    public ResponseEntity<ActivoFijoDto> darDeBaja(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioActivosFijos.darDeBaja(id));
    }

    /**
     * Traduce la etiqueta del metodo de depreciacion a su enum de dominio,
     * respondiendo 422 si es desconocida (Req 44.2).
     */
    private MetodoDepreciacion interpretarMetodo(String etiqueta) {
        try {
            return MetodoDepreciacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Metodo de depreciacion desconocido: " + etiqueta);
        }
    }
}
