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

import com.dessti.crm.calidad.application.QuejaClienteDto;
import com.dessti.crm.calidad.application.RegistrarQuejaClienteCommand;
import com.dessti.crm.calidad.application.ServicioQuejasCliente;
import com.dessti.crm.calidad.domain.EstadoQuejaCliente;
import com.dessti.crm.calidad.domain.OrigenQueja;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST de la Queja_Cliente (Req 70.1, 70.8, 70.9; tarea 54.1).
 * Registro, vinculo opcional a Accion_Correctiva, atencion y listado con filtros.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /calidad/quejas} — registrar
 *       ({@code @autorizador.tiene('queja_cliente','crear')}); 201.</li>
 *   <li>{@code GET /calidad/quejas?origen=&clienteId=&estado=&page=&size=} — listado
 *       ({@code @autorizador.tiene('queja_cliente','listar')}).</li>
 *   <li>{@code GET /calidad/quejas/{id}} — consulta
 *       ({@code @autorizador.tiene('queja_cliente','leer')}); 404 si no accesible.</li>
 *   <li>{@code PUT /calidad/quejas/{id}/vinculo} — vincular a Accion_Correctiva
 *       ({@code @autorizador.tiene('queja_cliente','cambiar_estado')}).</li>
 *   <li>{@code PUT /calidad/quejas/{id}/atencion} — atender
 *       ({@code @autorizador.tiene('queja_cliente','cambiar_estado')}).</li>
 * </ul>
 * <p>Los permisos {@code queja_cliente:{crear,leer,listar,cambiar_estado}} se sembraron en
 * V47 (rol {@code calidad}; lectura para {@code gerente} y {@code admin_empresa}).</p>
 */
@RestController
@RequestMapping("/calidad/quejas")
public class QuejasClienteController {

    private final ServicioQuejasCliente servicioQuejas;

    public QuejasClienteController(ServicioQuejasCliente servicioQuejas) {
        this.servicioQuejas = servicioQuejas;
    }

    /**
     * Registra una Queja_Cliente (Req 70.1, 70.8). 201 con el DTO.
     *
     * @param request datos de la queja.
     * @return 201 Created con el {@link QuejaClienteDto}.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('queja_cliente','crear')")
    public ResponseEntity<QuejaClienteDto> registrar(
            @Valid @RequestBody RegistrarQuejaClienteRequest request) {
        OrigenQueja origen = ParseoCalidad.origenQuejaRequerido(request.origen());
        RegistrarQuejaClienteCommand comando = new RegistrarQuejaClienteCommand(
                request.clienteId(), origen, request.canalSocialId(), request.descripcion());
        QuejaClienteDto dto = servicioQuejas.registrar(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista las Quejas del tenant de forma paginada (20/100) con filtros opcionales
     * (Req 70.1, 12).
     *
     * @param origen    etiqueta del origen a filtrar; opcional.
     * @param clienteId Cliente a filtrar; opcional.
     * @param estado    etiqueta de estado a filtrar; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link QuejaClienteDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('queja_cliente','listar')")
    public PaginaResponse<QuejaClienteDto> listar(
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        OrigenQueja origenFiltro = ParseoCalidad.origenQuejaOpcional(origen);
        EstadoQuejaCliente estadoFiltro = ParseoCalidad.estadoQuejaOpcional(estado);
        return PaginaResponse.de(servicioQuejas.listar(origenFiltro, clienteId, estadoFiltro, pageable));
    }

    /**
     * Consulta una Queja_Cliente por su identificador (Req 23.3).
     *
     * @param id identificador de la Queja_Cliente.
     * @return 200 OK con el {@link QuejaClienteDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('queja_cliente','leer')")
    public ResponseEntity<QuejaClienteDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioQuejas.consultar(id));
    }

    /**
     * Vincula la Queja_Cliente a una Accion_Correctiva (Req 70.1, clausula 10.2).
     *
     * @param id      identificador de la Queja_Cliente.
     * @param request Accion_Correctiva a vincular.
     * @return 200 OK con el {@link QuejaClienteDto}.
     */
    @PutMapping("/{id}/vinculo")
    @PreAuthorize("@autorizador.tiene('queja_cliente','cambiar_estado')")
    public ResponseEntity<QuejaClienteDto> vincular(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VincularAccionCorrectivaRequest request) {
        return ResponseEntity.ok(
                servicioQuejas.vincularAccionCorrectiva(id, request.accionCorrectivaId()));
    }

    /**
     * Marca la Queja_Cliente como atendida (Req 70.1).
     *
     * @param id identificador de la Queja_Cliente.
     * @return 200 OK con el {@link QuejaClienteDto} atendido.
     */
    @PutMapping("/{id}/atencion")
    @PreAuthorize("@autorizador.tiene('queja_cliente','cambiar_estado')")
    public ResponseEntity<QuejaClienteDto> atender(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioQuejas.atender(id));
    }
}
