package com.dessti.crm.social.adapter.in.rest;

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

import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;
import com.dessti.crm.social.application.CampanaPublicitariaDto;
import com.dessti.crm.social.application.CrearCampanaPublicitariaCommand;
import com.dessti.crm.social.application.EstadoCampanaExternoDto;
import com.dessti.crm.social.application.ServicioCampanas;
import com.dessti.crm.social.domain.CanalSocial;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST de la Campaña_Publicitaria (Req 65.7-65.11; tarea 41.1).
 * Expone la creacion con validacion de presupuesto/periodo, la consulta, el listado
 * con filtro por Canal_Social y la consulta del estado externo de SOLO LECTURA.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /social/campanas} — crear
 *       ({@code @autorizador.tiene('campana_publicitaria','crear')}); 201; 422 si el
 *       presupuesto o el periodo son invalidos (Req 65.7, 65.8).</li>
 *   <li>{@code GET /social/campanas/{id}} — consultar
 *       ({@code @autorizador.tiene('campana_publicitaria','leer')}); 404 si no accesible.</li>
 *   <li>{@code GET /social/campanas?canal=&page=&size=} — listado paginado
 *       ({@code @autorizador.tiene('campana_publicitaria','listar')}) (Req 65.10).</li>
 *   <li>{@code GET /social/campanas/{id}/estado-externo} — estado externo de SOLO
 *       LECTURA desde la Marketing API
 *       ({@code @autorizador.tiene('campana_publicitaria','leer')}) (Req 65.9).</li>
 * </ul>
 *
 * <p>Los permisos {@code campana_publicitaria:{crear,leer,listar}} se sembraron en
 * V5 y estan asignados al rol {@code marketing}.</p>
 */
@RestController
@RequestMapping("/social/campanas")
public class CampanasController {

    private final ServicioCampanas servicioCampanas;

    public CampanasController(ServicioCampanas servicioCampanas) {
        this.servicioCampanas = servicioCampanas;
    }

    /**
     * Crea una Campaña_Publicitaria validando su presupuesto y periodo (Req 65.7,
     * 65.8; Property 39). 201.
     *
     * @param request nombre, presupuesto, periodo y datos opcionales de la campaña.
     * @return 201 Created con el {@link CampanaPublicitariaDto}.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('campana_publicitaria','crear')")
    public ResponseEntity<CampanaPublicitariaDto> crear(
            @Valid @RequestBody CrearCampanaPublicitariaRequest request) {
        CanalSocial canal = ParseoPublicacion.canalOpcional(request.canal());
        CrearCampanaPublicitariaCommand comando = new CrearCampanaPublicitariaCommand(
                request.cuentaCanalSocialId(), canal, request.nombre(), request.presupuesto(),
                request.fechaInicio(), request.fechaFin(), request.externoId());
        CampanaPublicitariaDto dto = servicioCampanas.crearCampana(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Campaña_Publicitaria por su identificador (Req 23.3).
     *
     * @param id identificador de la Campaña_Publicitaria.
     * @return 200 OK con el {@link CampanaPublicitariaDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('campana_publicitaria','leer')")
    public ResponseEntity<CampanaPublicitariaDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCampanas.consultar(id));
    }

    /**
     * Lista las Campaña_Publicitaria del tenant de forma paginada (20/100) con filtro
     * opcional por Canal_Social (Req 65.10).
     *
     * @param canal etiqueta del canal a filtrar; opcional.
     * @param page  numero de pagina 0-index; opcional.
     * @param size  tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link CampanaPublicitariaDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('campana_publicitaria','listar')")
    public PaginaResponse<CampanaPublicitariaDto> listar(
            @RequestParam(name = "canal", required = false) String canal,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        CanalSocial canalFiltro = ParseoPublicacion.canalOpcional(canal);
        return PaginaResponse.de(servicioCampanas.listarCampanas(canalFiltro, pageable));
    }

    /**
     * Consulta el estado externo de una Campaña_Publicitaria en la Marketing API de
     * Meta, como instantanea de SOLO LECTURA (Req 65.9).
     *
     * @param id identificador de la Campaña_Publicitaria.
     * @return 200 OK con el {@link EstadoCampanaExternoDto} de SOLO LECTURA.
     */
    @GetMapping("/{id}/estado-externo")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('campana_publicitaria','leer')")
    public ResponseEntity<EstadoCampanaExternoDto> consultarEstadoExterno(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCampanas.consultarEstadoExterno(id));
    }
}
