package com.dessti.crm.rhnomina.nomina.adapter.in.rest;

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
import com.dessti.crm.rhnomina.nomina.application.CalcularNominaCommand;
import com.dessti.crm.rhnomina.nomina.application.CrearNominaCommand;
import com.dessti.crm.rhnomina.nomina.application.NominaDto;
import com.dessti.crm.rhnomina.nomina.application.ReciboNominaDto;
import com.dessti.crm.rhnomina.nomina.application.ServicioNomina;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo de calculo de Nomina y CFDI de nomina
 * (Req 41; tarea 35.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /rh-nomina/nominas} — crea una Nomina de un Periodo_Nomina
 *       ({@code @autorizador.tiene('nomina','crear')}); <strong>201 Created</strong>
 *       con el {@link NominaDto} en estado {@code borrador}. 422 si el periodo es
 *       invalido (Req 41.1).</li>
 *   <li>{@code GET /rh-nomina/nominas/{id}} — consulta una Nomina
 *       ({@code @autorizador.tiene('nomina','leer')}); <strong>200 OK</strong>. 404 si
 *       no existe/otro tenant (Req 23.3).</li>
 *   <li>{@code GET /rh-nomina/nominas?periodo=&estado=&page=&size=} — listado paginado
 *       ({@code @autorizador.tiene('nomina','listar')}); <strong>200 OK</strong> con
 *       {@link PaginaResponse} (20/100).</li>
 *   <li>{@code POST /rh-nomina/nominas/{id}/calculo} — calcula la Nomina
 *       ({@code @autorizador.tiene('nomina','cambiar_estado')}); <strong>200 OK</strong>.
 *       422 si a algun Empleado le faltan datos fiscales (Req 41.3), 409 si la
 *       transicion es invalida (Req 41.6).</li>
 *   <li>{@code POST /rh-nomina/nominas/{id}/autorizacion} — autoriza la Nomina
 *       ({@code @autorizador.tiene('nomina','cambiar_estado')}); <strong>200 OK</strong>
 *       (Req 41.4).</li>
 *   <li>{@code POST /rh-nomina/nominas/{id}/timbrado} — timbra los Recibo_Nomina como
 *       CFDI de nomina via PAC ({@code @autorizador.tiene('nomina','cambiar_estado')});
 *       <strong>200 OK</strong>. 422 si el PAC rechaza algun Timbrado (Req 41.4).</li>
 *   <li>{@code POST /rh-nomina/nominas/{id}/pago} — marca la Nomina como pagada
 *       ({@code @autorizador.tiene('nomina','cambiar_estado')}); <strong>200 OK</strong>
 *       (Req 41.5).</li>
 *   <li>{@code GET /rh-nomina/nominas/{id}/recibos} — lista los Recibo_Nomina de la
 *       Nomina ({@code @autorizador.tiene('recibo_nomina','leer')});
 *       <strong>200 OK</strong> con {@link PaginaResponse} (Req 41.1).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 41)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code nomina:{crear,leer,cambiar_estado}} se sembraron en V5; {@code nomina:listar}
 * y {@code recibo_nomina:leer} se agregan en V34 y se enlazan al rol {@code rh}. Sin el
 * permiso, Spring Security responde 403 por denegacion por defecto (Req 3.2).</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA. Los cuerpos de
 * entrada ({@code *Request}) se traducen a comandos de aplicacion en el controlador; el
 * {@code tenant_id} y el actor se derivan del contexto y nunca se aceptan en la
 * peticion. El manejo de errores lo centraliza el manejador global.</p>
 */
@RestController
@RequestMapping("/rh-nomina")
public class NominaController {

    private final ServicioNomina servicioNomina;

    public NominaController(ServicioNomina servicioNomina) {
        this.servicioNomina = servicioNomina;
    }

    /**
     * Crea una Nomina de un Periodo_Nomina en estado {@code borrador} (Req 41.1).
     *
     * @param request datos de la Nomina a crear.
     * @return 201 Created con el {@link NominaDto} creado.
     */
    @PostMapping("/nominas")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('nomina','crear')")
    public ResponseEntity<NominaDto> crear(@Valid @RequestBody CrearNominaRequest request) {
        NominaDto dto = servicioNomina.crearNomina(new CrearNominaCommand(request.periodoNomina()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Nomina por su identificador (Req 23.3). 404 si no existe o pertenece
     * a otro tenant.
     *
     * @param id identificador de la Nomina.
     * @return 200 OK con el {@link NominaDto}.
     */
    @GetMapping("/nominas/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('nomina','leer')")
    public ResponseEntity<NominaDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioNomina.consultar(id));
    }

    /**
     * Lista las Nominas del tenant de forma paginada (20 por defecto, 100 maximo) con
     * filtros opcionales por Periodo_Nomina y por estado (Req 41).
     *
     * @param periodo codigo de Periodo_Nomina a filtrar; opcional.
     * @param estado  etiqueta de estado a filtrar; opcional.
     * @param page    numero de pagina 0-index; opcional.
     * @param size    tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link NominaDto}.
     */
    @GetMapping("/nominas")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('nomina','listar')")
    public PaginaResponse<NominaDto> listar(
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioNomina.listar(periodo, estado, pageable));
    }

    /**
     * Calcula la Nomina (Req 41.1, 41.2, 41.3): genera un Recibo_Nomina por Empleado y
     * transita {@code borrador -> calculada}. El cuerpo es opcional (aguinaldo, PTU,
     * tasa de Infonavit). 422 si a algun Empleado le faltan datos fiscales.
     *
     * @param id      identificador de la Nomina.
     * @param request parametros del calculo; opcional.
     * @return 200 OK con el {@link NominaDto} {@code calculada}.
     */
    @PostMapping("/nominas/{id}/calculo")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('nomina','cambiar_estado')")
    public ResponseEntity<NominaDto> calcular(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) CalcularNominaRequest request) {
        CalcularNominaRequest datos =
                (request == null) ? new CalcularNominaRequest(null, null, null) : request;
        NominaDto dto = servicioNomina.calcular(new CalcularNominaCommand(
                id, datos.aguinaldo(), datos.ptu(), datos.tasaInfonavit()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Autoriza la Nomina y transita {@code calculada -> autorizada} (Req 41.4, 41.5).
     *
     * @param id identificador de la Nomina.
     * @return 200 OK con el {@link NominaDto} {@code autorizada}.
     */
    @PostMapping("/nominas/{id}/autorizacion")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('nomina','cambiar_estado')")
    public ResponseEntity<NominaDto> autorizar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioNomina.autorizar(id));
    }

    /**
     * Timbra los Recibo_Nomina de la Nomina como CFDI de nomina via PAC y transita
     * {@code autorizada -> timbrada} (Req 41.4). 422 si el PAC rechaza algun Timbrado.
     *
     * @param id identificador de la Nomina.
     * @return 200 OK con el {@link NominaDto} {@code timbrada}.
     */
    @PostMapping("/nominas/{id}/timbrado")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('nomina','cambiar_estado')")
    public ResponseEntity<NominaDto> timbrar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioNomina.timbrar(id));
    }

    /**
     * Marca la Nomina como pagada y transita {@code timbrada -> pagada} (Req 41.5).
     *
     * @param id identificador de la Nomina.
     * @return 200 OK con el {@link NominaDto} {@code pagada}.
     */
    @PostMapping("/nominas/{id}/pago")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('nomina','cambiar_estado')")
    public ResponseEntity<NominaDto> pagar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioNomina.marcarPagada(id));
    }

    /**
     * Lista los Recibo_Nomina de una Nomina accesible del tenant de forma paginada
     * (Req 41.1). 404 si la Nomina no es accesible.
     *
     * @param id   identificador de la Nomina.
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ReciboNominaDto}.
     */
    @GetMapping("/nominas/{id}/recibos")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('recibo_nomina','leer')")
    public PaginaResponse<ReciboNominaDto> listarRecibos(
            @PathVariable("id") UUID id,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioNomina.listarRecibos(id, pageable));
    }
}
