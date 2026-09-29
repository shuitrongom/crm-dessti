package com.dessti.crm.operacion.proyecto.adapter.in.rest;

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

import com.dessti.crm.operacion.proyecto.application.AgregarSitioCommand;
import com.dessti.crm.operacion.proyecto.application.CrearProyectoCommand;
import com.dessti.crm.operacion.proyecto.application.ProyectoDto;
import com.dessti.crm.operacion.proyecto.application.ServicioProyectos;
import com.dessti.crm.operacion.proyecto.application.SitioDto;
import com.dessti.crm.operacion.proyecto.domain.FaseSitioGenerica;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del submodulo proyecto (Req 21; tarea 22.2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /proyectos} — crear un Proyecto asociado a un Cliente
 *       ({@code @autorizador.tiene('proyecto','crear')}); 201 Created con el id
 *       (Req 21.1).</li>
 *   <li>{@code GET /proyectos/{id}} — consultar un Proyecto con su estado
 *       consolidado y el avance de sus Sitios
 *       ({@code @autorizador.tiene('proyecto','leer')}); 200 OK; 404 si no es
 *       accesible (Req 21.4, 23.3).</li>
 *   <li>{@code POST /proyectos/{id}/sitios} — agregar un Sitio a un Proyecto
 *       ({@code @autorizador.tiene('proyecto','actualizar')}); 201 Created con el
 *       Sitio; 404 si el Proyecto no es accesible (Req 21.2).</li>
 *   <li>{@code GET /proyectos?clienteId=&page=&size=} — listado paginado (20/100)
 *       con filtro por Cliente ({@code @autorizador.tiene('proyecto','listar')});
 *       200 OK con {@link PaginaResponse} (Req 21.5).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code proyecto:{crear,leer,listar,actualizar}} ya se sembraron en V5 y se
 * asignaron a los roles ventas/supervisor/gerente, por lo que V25 no siembra
 * permisos. <strong>No existe un recurso {@code sitio} en V5</strong>: las
 * operaciones sobre Sitio viajan sobre los permisos de {@code proyecto} (el Sitio
 * se gestiona como parte del agregado Proyecto): agregar un Sitio exige
 * {@code proyecto:actualizar}, y consultar un Proyecto con sus Sitios exige
 * {@code proyecto:leer}.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 404 no
 * encontrado).</p>
 */
@RestController
@RequestMapping("/proyectos")
public class ProyectoController {

    private final ServicioProyectos servicioProyectos;

    public ProyectoController(ServicioProyectos servicioProyectos) {
        this.servicioProyectos = servicioProyectos;
    }

    /**
     * Crea un Proyecto asociado a un Cliente existente (Req 21.1). Devuelve 201 con
     * el id.
     *
     * @param request cuerpo con el Cliente y el nombre del Proyecto.
     * @return 201 Created con el {@link ProyectoDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','crear')")
    public ResponseEntity<ProyectoDto> crear(@Valid @RequestBody CrearProyectoRequest request) {
        ProyectoDto dto = servicioProyectos.crear(
                new CrearProyectoCommand(request.clienteId(), request.nombre()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Proyecto por su identificador, devolviendo su estado consolidado
     * y el avance de sus Sitios (Req 21.4). 404 si no es accesible (Req 23.3).
     *
     * @param id identificador del Proyecto.
     * @return 200 OK con el {@link ProyectoDto} detallado.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','leer')")
    public ResponseEntity<ProyectoDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioProyectos.consultar(id));
    }

    /**
     * Agrega un Sitio a un Proyecto existente (Req 21.2). Devuelve 201 con el Sitio;
     * 404 si el Proyecto no es accesible.
     *
     * @param id      identificador del Proyecto al que se agrega el Sitio.
     * @param request cuerpo con el nombre y la direccion opcional del Sitio.
     * @return 201 Created con el {@link SitioDto} creado.
     */
    @PostMapping("/{id}/sitios")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','actualizar')")
    public ResponseEntity<SitioDto> agregarSitio(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AgregarSitioRequest request) {
        SitioDto dto = servicioProyectos.agregarSitio(
                new AgregarSitioCommand(id, request.nombre(), request.direccion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Edita el nombre de un Proyecto existente (Req 21.1, 21.6). No altera el Cliente
     * asociado. Devuelve el Proyecto detallado tras el cambio. 404 si no es accesible;
     * 422 si el nombre es invalido.
     *
     * @param id      identificador del Proyecto.
     * @param request nuevo nombre.
     * @return 200 OK con el {@link ProyectoDto} detallado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','actualizar')")
    public ResponseEntity<ProyectoDto> editar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarProyectoRequest request) {
        return ResponseEntity.ok(servicioProyectos.editarProyecto(id, request.nombre()));
    }

    /**
     * Edita los datos descriptivos (nombre y direccion) de un Sitio de un Proyecto
     * (Req 21.2, 21.6). Devuelve el Proyecto detallado tras el cambio. 404 si el
     * Proyecto o el Sitio no son accesibles; 422 si los datos son invalidos.
     *
     * @param id      identificador del Proyecto.
     * @param sitioId identificador del Sitio.
     * @param request nuevos nombre y direccion del Sitio.
     * @return 200 OK con el {@link ProyectoDto} detallado.
     */
    @PutMapping("/{id}/sitios/{sitioId}")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','actualizar')")
    public ResponseEntity<ProyectoDto> editarSitio(
            @PathVariable("id") UUID id,
            @PathVariable("sitioId") UUID sitioId,
            @Valid @RequestBody ActualizarSitioRequest request) {
        return ResponseEntity.ok(
                servicioProyectos.editarSitio(id, sitioId, request.nombre(), request.direccion()));
    }

    /**
     * Actualiza la fase operativa generica de un Sitio de un Proyecto multi-sitio
     * (Req 3.2), avanzando el despliegue sitio por sitio para giros que no son
     * anuncios. Devuelve el Proyecto detallado (variante multi-sitio) tras el cambio.
     * 404 si el Proyecto o el Sitio no son accesibles; 409 si la transicion de fase
     * es invalida; 422 si la etiqueta de fase es desconocida.
     *
     * @param id      identificador del Proyecto.
     * @param sitioId identificador del Sitio.
     * @param request fase destino y nota opcional.
     * @return 200 OK con el {@link ProyectoDto} detallado multi-sitio.
     */
    @PutMapping("/{id}/sitios/{sitioId}/avance")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','actualizar')")
    public ResponseEntity<ProyectoDto> avanzarAvanceSitio(
            @PathVariable("id") UUID id,
            @PathVariable("sitioId") UUID sitioId,
            @Valid @RequestBody ActualizarAvanceSitioRequest request) {
        FaseSitioGenerica destino = interpretarFase(request.fase());
        return ResponseEntity.ok(servicioProyectos.avanzarAvanceSitio(
                id, sitioId, destino, request.nota(), request.evidenciaUrl()));
    }

    /**
     * Corrige (incluido RETROCESO) la fase operativa de un Sitio de un Proyecto
     * multi-sitio (Req 3.2). Operacion administrativa sensible: exige
     * {@code proyecto:cambiar_estado} (sembrado en V78 para admin_empresa y gerente),
     * a diferencia del avance normal que usa {@code proyecto:actualizar}. Permite ir a
     * cualquier fase para deshacer avances marcados por error. 404 si el Proyecto o el
     * Sitio no son accesibles; 422 si la etiqueta de fase es desconocida.
     *
     * @param id      identificador del Proyecto.
     * @param sitioId identificador del Sitio.
     * @param request fase destino, nota (motivo) y evidencia opcional.
     * @return 200 OK con el {@link ProyectoDto} detallado multi-sitio.
     */
    @PutMapping("/{id}/sitios/{sitioId}/correccion-fase")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','cambiar_estado')")
    public ResponseEntity<ProyectoDto> corregirAvanceSitio(
            @PathVariable("id") UUID id,
            @PathVariable("sitioId") UUID sitioId,
            @Valid @RequestBody ActualizarAvanceSitioRequest request) {
        FaseSitioGenerica destino = interpretarFase(request.fase());
        return ResponseEntity.ok(servicioProyectos.corregirAvanceSitio(
                id, sitioId, destino, request.nota(), request.evidenciaUrl()));
    }

    /** Interpreta la etiqueta de fase, traduciendo un valor desconocido a 422. */
    private static FaseSitioGenerica interpretarFase(String etiqueta) {
        try {
            return FaseSitioGenerica.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("La fase indicada no es valida: " + etiqueta + ".");
        }
    }

    /**
     * Lista los Proyectos del tenant de forma paginada (20 por defecto, 100 maximo)
     * con filtro opcional por Cliente (Req 21.5).
     *
     * @param clienteId Cliente a filtrar; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ProyectoDto} (proyeccion de resumen).
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','listar')")
    public PaginaResponse<ProyectoDto> listar(
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioProyectos.listar(clienteId, pageable));
    }
}
