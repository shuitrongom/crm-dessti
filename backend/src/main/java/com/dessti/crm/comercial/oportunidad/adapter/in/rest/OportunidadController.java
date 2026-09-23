package com.dessti.crm.comercial.oportunidad.adapter.in.rest;

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

import com.dessti.crm.comercial.oportunidad.application.CrearOportunidadCommand;
import com.dessti.crm.comercial.oportunidad.application.OportunidadDto;
import com.dessti.crm.comercial.oportunidad.application.ServicioOportunidades;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del submodulo comercial-crm para la gestion de las
 * {@link OportunidadDto Oportunidades} del pipeline de ventas (Req 14, 12;
 * tarea 17.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /oportunidades} — alta ({@code @autorizador.tiene('oportunidad','crear')});
 *       201 Created. 422 si los datos son invalidos; 404 si el Cliente no existe
 *       (Req 14.1).</li>
 *   <li>{@code GET /oportunidades/{id}} — consulta
 *       ({@code @autorizador.tiene('oportunidad','leer')}); 200 OK; 404 si no es
 *       accesible (Req 4.3, 23.3).</li>
 *   <li>{@code PUT /oportunidades/{id}/etapa} — cambio de etapa
 *       ({@code @autorizador.tiene('oportunidad','cambiar_estado')}); 200 OK;
 *       409 si la transicion es invalida (Req 14.3, 14.4).</li>
 *   <li>{@code PUT /oportunidades/{id}/responsable} — asignacion de responsable
 *       ({@code @autorizador.tiene('oportunidad','actualizar')}); 200 OK (Req 14.2).</li>
 *   <li>{@code POST /oportunidades/{id}/convertir} — conversion a Cotizacion
 *       ({@code @autorizador.tiene('oportunidad','actualizar')}); 200 OK con el
 *       identificador de la Cotizacion; 422 si la etapa no es {@code ganado}
 *       (Req 14.5, 14.6).</li>
 *   <li>{@code GET /oportunidades?clienteId=&etapa=&responsableId=&page=&size=} —
 *       listado paginado (20/100) con filtros
 *       ({@code @autorizador.tiene('oportunidad','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 14.7, 14.8).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.2)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code oportunidad:{crear,leer,listar,actualizar,cambiar_estado}} ya se
 * sembraron en V5 y se asignaron al rol {@code ventas} (Req 27.2), por lo que la
 * migracion V13 no requiere sembrar permisos adicionales.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/oportunidades")
public class OportunidadController {

    private final ServicioOportunidades servicioOportunidades;

    public OportunidadController(ServicioOportunidades servicioOportunidades) {
        this.servicioOportunidades = servicioOportunidades;
    }

    /**
     * Da de alta una Oportunidad en etapa inicial {@code nuevo} (Req 14.1). 422 si
     * los datos son invalidos; 404 si el Cliente no existe en el tenant.
     *
     * @param request datos de la Oportunidad a crear.
     * @return 201 Created con el {@link OportunidadDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('oportunidad','crear')")
    public ResponseEntity<OportunidadDto> crear(@Valid @RequestBody CrearOportunidadRequest request) {
        OportunidadDto dto = servicioOportunidades.crearOportunidad(new CrearOportunidadCommand(
                request.clienteId(), request.titulo(), request.valorEstimado()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Oportunidad por su identificador (Req 4.3, 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la Oportunidad.
     * @return 200 OK con el {@link OportunidadDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('oportunidad','leer')")
    public ResponseEntity<OportunidadDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioOportunidades.consultarOportunidad(id));
    }

    /**
     * Cambia la etapa de una Oportunidad segun la maquina de estados del pipeline
     * (Req 14.3, 14.4). 409 si la transicion es invalida; 404 si no es accesible.
     *
     * @param id      identificador de la Oportunidad.
     * @param request etiqueta de la etapa destino.
     * @return 200 OK con el {@link OportunidadDto} en su nueva etapa.
     */
    @PutMapping("/{id}/etapa")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('oportunidad','cambiar_estado')")
    public ResponseEntity<OportunidadDto> cambiarEtapa(@PathVariable("id") UUID id,
                                                       @Valid @RequestBody CambiarEtapaRequest request) {
        return ResponseEntity.ok(servicioOportunidades.cambiarEtapa(id, request.etapa()));
    }

    /**
     * Asigna el Usuario de ventas responsable de una Oportunidad (Req 14.2). 404
     * si no es accesible; 422 si el Usuario es nulo.
     *
     * @param id      identificador de la Oportunidad.
     * @param request identificador del Usuario responsable.
     * @return 200 OK con el {@link OportunidadDto} actualizado.
     */
    @PutMapping("/{id}/responsable")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('oportunidad','actualizar')")
    public ResponseEntity<OportunidadDto> asignarResponsable(@PathVariable("id") UUID id,
                                                             @Valid @RequestBody AsignarResponsableRequest request) {
        return ResponseEntity.ok(servicioOportunidades.asignarResponsable(id, request.usuarioId()));
    }

    /**
     * Clasifica una Oportunidad por canal de venta, asigna, modifica o limpia el
     * canal (Req 63.1). Se guarda con el permiso de actualizacion de la
     * Oportunidad ({@code oportunidad:actualizar}), pues es una modificacion del
     * recurso, no un recurso propio. La asignacion se audita (Req 63.3). 404 si la
     * Oportunidad no es accesible o si el canal indicado no existe en el tenant.
     *
     * @param id      identificador de la Oportunidad.
     * @param request identificador del Canal_Venta (nulo para limpiar).
     * @return 200 OK con el {@link OportunidadDto} clasificado.
     */
    @PutMapping("/{id}/canal-venta")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('oportunidad','actualizar')")
    public ResponseEntity<OportunidadDto> asignarCanalVenta(@PathVariable("id") UUID id,
                                                            @Valid @RequestBody AsignarCanalVentaRequest request) {
        return ResponseEntity.ok(servicioOportunidades.asignarCanalVenta(id, request.canalVentaId()));
    }

    /**
     * Convierte una Oportunidad en etapa {@code ganado} en una Cotizacion
     * (Req 14.5, 14.6). 200 OK con el identificador de la Cotizacion; 422 si la
     * etapa no es {@code ganado}; 404 si no es accesible.
     *
     * @param id identificador de la Oportunidad.
     * @return 200 OK con el {@link ConversionCotizacionResponse}.
     */
    @PostMapping("/{id}/convertir")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('oportunidad','actualizar')")
    public ResponseEntity<ConversionCotizacionResponse> convertir(@PathVariable("id") UUID id) {
        UUID cotizacionId = servicioOportunidades.convertirEnCotizacion(id);
        return ResponseEntity.ok(new ConversionCotizacionResponse(cotizacionId));
    }

    /**
     * Lista las Oportunidades del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por Cliente, etapa y responsable (Req 14.7,
     * 14.8).
     *
     * @param clienteId     Cliente a filtrar; opcional.
     * @param etapa         etiqueta de etapa a filtrar; opcional.
     * @param responsableId Usuario responsable a filtrar; opcional.
     * @param page          numero de pagina 0-index; opcional.
     * @param size          tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link OportunidadDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('oportunidad','listar')")
    public PaginaResponse<OportunidadDto> listar(
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "etapa", required = false) String etapa,
            @RequestParam(name = "responsableId", required = false) UUID responsableId,
            @RequestParam(name = "canalVentaId", required = false) UUID canalVentaId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(
                servicioOportunidades.listarOportunidades(clienteId, etapa, responsableId, canalVentaId, pageable));
    }
}
