package com.dessti.crm.calidad.adapter.in.rest;

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

import com.dessti.crm.calidad.application.AbrirAccionCorrectivaCommand;
import com.dessti.crm.calidad.application.AccionCorrectivaDto;
import com.dessti.crm.calidad.application.ServicioNoConformidades;
import com.dessti.crm.calidad.domain.EstadoAccionCorrectiva;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST de la Accion_Correctiva (Req 70.2, 70.9; tarea 54.2).
 * Apertura, avance de estado, verificacion de eficacia, cierre (con guarda de eficacia,
 * Property 43) y listado con filtros.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /calidad/acciones-correctivas} — abrir
 *       ({@code @autorizador.tiene('accion_correctiva','crear')}); 201.</li>
 *   <li>{@code GET /calidad/acciones-correctivas?estado=&noConformidadId=&page=&size=} —
 *       listado ({@code @autorizador.tiene('accion_correctiva','listar')}).</li>
 *   <li>{@code GET /calidad/acciones-correctivas/{id}} — consulta
 *       ({@code @autorizador.tiene('accion_correctiva','leer')}); 404 si no accesible.</li>
 *   <li>{@code PUT /calidad/acciones-correctivas/{id}/estado} — avanzar estado (no cierre)
 *       ({@code @autorizador.tiene('accion_correctiva','cambiar_estado')}).</li>
 *   <li>{@code PUT /calidad/acciones-correctivas/{id}/verificacion-eficacia} — verificar
 *       eficacia ({@code @autorizador.tiene('accion_correctiva','cambiar_estado')}).</li>
 *   <li>{@code PUT /calidad/acciones-correctivas/{id}/cierre} — cerrar; 422 si la eficacia
 *       no esta verificada (Property 43)
 *       ({@code @autorizador.tiene('accion_correctiva','cambiar_estado')}).</li>
 * </ul>
 * <p>Los permisos se sembraron en V47 (rol {@code calidad}; lectura para {@code gerente} y
 * {@code admin_empresa}).</p>
 */
@RestController
@RequestMapping("/calidad/acciones-correctivas")
public class AccionesCorrectivasController {

    private final ServicioNoConformidades servicioNoConformidades;

    public AccionesCorrectivasController(ServicioNoConformidades servicioNoConformidades) {
        this.servicioNoConformidades = servicioNoConformidades;
    }

    /**
     * Abre una Accion_Correctiva (Req 70.2). 201 con el DTO.
     *
     * @param request datos de la Accion_Correctiva.
     * @return 201 Created con el {@link AccionCorrectivaDto}.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('accion_correctiva','crear')")
    public ResponseEntity<AccionCorrectivaDto> abrir(
            @Valid @RequestBody AbrirAccionCorrectivaRequest request) {
        AbrirAccionCorrectivaCommand comando = new AbrirAccionCorrectivaCommand(
                request.noConformidadId(), request.responsableId(),
                request.causaRaiz(), request.accionesPlanificadas());
        AccionCorrectivaDto dto = servicioNoConformidades.abrirAccionCorrectiva(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista las Acciones_Correctivas del tenant de forma paginada (20/100) con filtros
     * opcionales (Req 70.2, 12).
     *
     * @param estado          etiqueta de estado a filtrar; opcional.
     * @param noConformidadId No_Conformidad a filtrar; opcional.
     * @param page            numero de pagina 0-index; opcional.
     * @param size            tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link AccionCorrectivaDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('accion_correctiva','listar')")
    public PaginaResponse<AccionCorrectivaDto> listar(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "noConformidadId", required = false) UUID noConformidadId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        EstadoAccionCorrectiva estadoFiltro = ParseoCalidad.estadoAccionCorrectivaOpcional(estado);
        return PaginaResponse.de(
                servicioNoConformidades.listarAccionesCorrectivas(estadoFiltro, noConformidadId, pageable));
    }

    /**
     * Consulta una Accion_Correctiva por su identificador (Req 23.3).
     *
     * @param id identificador de la Accion_Correctiva.
     * @return 200 OK con el {@link AccionCorrectivaDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('accion_correctiva','leer')")
    public ResponseEntity<AccionCorrectivaDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioNoConformidades.consultarAccionCorrectiva(id));
    }

    /**
     * Avanza el estado de una Accion_Correctiva por la maquina de estados (Req 70.2). No
     * se admite alcanzar {@code cerrada} por esta ruta: el cierre pasa por {@code /cierre}.
     *
     * @param id      identificador de la Accion_Correctiva.
     * @param request etiqueta del estado destino (distinto de {@code cerrada}).
     * @return 200 OK con el {@link AccionCorrectivaDto}.
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.tiene('accion_correctiva','cambiar_estado')")
    public ResponseEntity<AccionCorrectivaDto> avanzar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CambiarEstadoRequest request) {
        EstadoAccionCorrectiva destino = ParseoCalidad.estadoAccionCorrectivaRequerido(request.estado());
        return ResponseEntity.ok(servicioNoConformidades.avanzarAccionCorrectiva(id, destino));
    }

    /**
     * Registra la verificacion de la eficacia de una Accion_Correctiva, requisito previo
     * del cierre (Req 70.2; Property 43).
     *
     * @param id      identificador de la Accion_Correctiva.
     * @param request evidencia opcional de la verificacion.
     * @return 200 OK con el {@link AccionCorrectivaDto}.
     */
    @PutMapping("/{id}/verificacion-eficacia")
    @PreAuthorize("@autorizador.tiene('accion_correctiva','cambiar_estado')")
    public ResponseEntity<AccionCorrectivaDto> verificarEficacia(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VerificarEficaciaRequest request) {
        return ResponseEntity.ok(servicioNoConformidades.verificarEficacia(id, request.evidencia()));
    }

    /**
     * Cierra una Accion_Correctiva; 422 si la eficacia no esta verificada (Req 70.2;
     * Property 43).
     *
     * @param id identificador de la Accion_Correctiva.
     * @return 200 OK con el {@link AccionCorrectivaDto} cerrado.
     */
    @PutMapping("/{id}/cierre")
    @PreAuthorize("@autorizador.tiene('accion_correctiva','cambiar_estado')")
    public ResponseEntity<AccionCorrectivaDto> cerrar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioNoConformidades.cerrarAccionCorrectiva(id));
    }
}
