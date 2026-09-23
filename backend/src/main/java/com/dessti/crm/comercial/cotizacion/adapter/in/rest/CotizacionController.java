package com.dessti.crm.comercial.cotizacion.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import com.dessti.crm.comercial.cotizacion.application.CotizacionDto;
import com.dessti.crm.comercial.cotizacion.application.CrearCotizacionCommand;
import com.dessti.crm.comercial.cotizacion.application.CrearPartidaCommand;
import com.dessti.crm.comercial.cotizacion.application.ServicioCotizaciones;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del submodulo comercial-crm para la gestion de las
 * {@link CotizacionDto Cotizaciones} (Req 6, 12; tarea 17.2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /cotizaciones} — alta ({@code @autorizador.tiene('cotizacion','crear')});
 *       201 Created. 422 si los datos son invalidos; 404 si el Cliente no existe
 *       (Req 6.1, 6.2).</li>
 *   <li>{@code GET /cotizaciones/{id}} — consulta
 *       ({@code @autorizador.tiene('cotizacion','leer')}); 200 OK; 404 si no es
 *       accesible (Req 4.3, 23.3).</li>
 *   <li>{@code POST /cotizaciones/{id}/partidas} — agregar partida
 *       ({@code @autorizador.tiene('cotizacion','actualizar')}); 200 OK; 422 si la
 *       partida es invalida o la Cotizacion no esta en {@code borrador}
 *       (Req 6.3, 6.4).</li>
 *   <li>{@code PUT /cotizaciones/{id}/estado} — cambio de estado
 *       ({@code @autorizador.tiene('cotizacion','cambiar_estado')}); 200 OK; 409 si
 *       la transicion es invalida (Req 6.6, 6.7).</li>
 *   <li>{@code GET /cotizaciones?clienteId=&estado=&page=&size=} — listado
 *       paginado (20/100) con filtros
 *       ({@code @autorizador.tiene('cotizacion','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 6.8, 6.9).</li>
 *   <li>{@code GET /cotizaciones/{id}/pdf} — descarga el PDF PREMIUM
 *       ({@code @autorizador.tiene('cotizacion','leer')}); 200 OK con
 *       {@code application/pdf} inline; 404 si no es accesible (V60).</li>
 *   <li>{@code POST /cotizaciones/{id}/enviar-correo} — envia la Cotizacion por
 *       correo ({@code @autorizador.tiene('cotizacion','actualizar')}); 200 OK con
 *       el DTO actualizado; 422 si no hay correo disponible o el envio falla (V60).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.2)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code cotizacion:{crear,leer,listar,actualizar,cambiar_estado}} ya se sembraron
 * en V5 y se asignaron al rol {@code ventas} (Req 27.2; el {@code cambiar_estado}
 * tambien a {@code gerente}), por lo que la migracion V14 no requiere sembrar
 * permisos adicionales.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/cotizaciones")
public class CotizacionController {

    /** Encabezado informativo que marca datos fiscales del emisor incompletos (Req 3). */
    static final String ENCABEZADO_EMISOR_INCOMPLETO = "X-Emisor-Incompleto";

    private final ServicioCotizaciones servicioCotizaciones;

    public CotizacionController(ServicioCotizaciones servicioCotizaciones) {
        this.servicioCotizaciones = servicioCotizaciones;
    }

    /**
     * Da de alta una Cotizacion en estado inicial {@code borrador} con entre 1 y
     * 500 partidas (Req 6.1, 6.2). 422 si los datos son invalidos; 404 si el
     * Cliente no existe en el tenant.
     *
     * @param request datos de la Cotizacion a crear.
     * @return 201 Created con el {@link CotizacionDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cotizacion','crear')")
    public ResponseEntity<CotizacionDto> crear(@Valid @RequestBody CrearCotizacionRequest request) {
        List<CrearPartidaCommand> partidas = request.partidas().stream()
                .map(CotizacionController::aComando)
                .toList();
        CotizacionDto dto = servicioCotizaciones.crearCotizacion(
                new CrearCotizacionCommand(request.clienteId(), partidas, request.validoHasta(),
                        request.condiciones(), request.notas(), request.moneda()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Cotizacion por su identificador (Req 4.3, 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la Cotizacion.
     * @return 200 OK con el {@link CotizacionDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cotizacion','leer')")
    public ResponseEntity<CotizacionDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCotizaciones.consultarCotizacion(id));
    }

    /**
     * Agrega una Partida_Cotizacion a una Cotizacion en {@code borrador} y
     * recalcula los totales (Req 6.3, 6.5). 422 si la partida es invalida o la
     * Cotizacion no esta en {@code borrador}; 404 si no es accesible.
     *
     * @param id      identificador de la Cotizacion.
     * @param request datos de la partida a agregar.
     * @return 200 OK con el {@link CotizacionDto} actualizado.
     */
    @PostMapping("/{id}/partidas")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cotizacion','actualizar')")
    public ResponseEntity<CotizacionDto> agregarPartida(@PathVariable("id") UUID id,
                                                        @Valid @RequestBody PartidaRequest request) {
        return ResponseEntity.ok(servicioCotizaciones.agregarPartida(id, aComando(request)));
    }

    /**
     * Cambia el estado de una Cotizacion segun la maquina de estados (Req 6.6,
     * 6.7). 409 si la transicion es invalida; 404 si no es accesible; 422 si se
     * intenta enviar sin partidas.
     *
     * @param id      identificador de la Cotizacion.
     * @param request etiqueta del estado destino.
     * @return 200 OK con el {@link CotizacionDto} en su nuevo estado.
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cotizacion','cambiar_estado')")
    public ResponseEntity<CotizacionDto> cambiarEstado(@PathVariable("id") UUID id,
                                                       @Valid @RequestBody CambiarEstadoRequest request) {
        return ResponseEntity.ok(servicioCotizaciones.cambiarEstado(id, request.estado()));
    }

    /**
     * Clasifica una Cotizacion por canal de venta, asigna, modifica o limpia el
     * canal (Req 63.1). Se guarda con el permiso de actualizacion de la Cotizacion
     * ({@code cotizacion:actualizar}), pues es una modificacion del recurso, no un
     * recurso propio. La asignacion se audita (Req 63.3). 404 si la Cotizacion no
     * es accesible o si el canal indicado no existe en el tenant.
     *
     * @param id      identificador de la Cotizacion.
     * @param request identificador del Canal_Venta (nulo para limpiar).
     * @return 200 OK con el {@link CotizacionDto} clasificado.
     */
    @PutMapping("/{id}/canal-venta")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cotizacion','actualizar')")
    public ResponseEntity<CotizacionDto> asignarCanalVenta(@PathVariable("id") UUID id,
                                                           @Valid @RequestBody AsignarCanalVentaRequest request) {
        return ResponseEntity.ok(servicioCotizaciones.asignarCanalVenta(id, request.canalVentaId()));
    }

    /**
     * Descarga el documento PREMIUM de la Cotizacion en PDF (V60). Requiere el
     * permiso de lectura de la Cotizacion. 404 si la Cotizacion no es accesible.
     *
     * <p>Si los datos fiscales de la Empresa emisora estan incompletos (falta RFC
     * o direccion), la respuesta anexa el encabezado informativo
     * {@code X-Emisor-Incompleto: true} para que el frontend muestre un aviso no
     * intrusivo (Req 3), sin bloquear la descarga.</p>
     *
     * @param id identificador de la Cotizacion.
     * @return 200 OK con {@code Content-Type: application/pdf} y
     *         {@code Content-Disposition: inline; filename="cotizacion-<folio>.pdf"}.
     */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cotizacion','leer')")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable("id") UUID id) {
        CotizacionDto dto = servicioCotizaciones.consultarCotizacion(id);
        byte[] pdf = servicioCotizaciones.generarPdf(id);
        boolean emisorIncompleto = servicioCotizaciones.emisorIncompleto();

        String folio = (dto.folio() == null || dto.folio().isBlank())
                ? dto.id().toString() : dto.folio();
        String nombreArchivo = "cotizacion-" + folio + ".pdf";
        ContentDisposition disposicion = ContentDisposition.inline()
                .filename(nombreArchivo).build();

        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString());
        if (emisorIncompleto) {
            respuesta.header(ENCABEZADO_EMISOR_INCOMPLETO, "true");
        }
        return respuesta.body(pdf);
    }

    /**
     * Envia la Cotizacion por correo electronico (V60). Genera el PDF, invoca el
     * puerto de correo, marca {@code enviadaEn} y, si estaba en {@code borrador}
     * (con partidas), la transiciona a {@code enviada}. Requiere el permiso de
     * actualizacion de la Cotizacion, pues modifica su estado/marca de envio.
     *
     * <p>Si el cuerpo indica un {@code email}, se envia a esa direccion; si no, al
     * correo del Cliente. 422 si no hay correo disponible o el envio falla.</p>
     *
     * @param id      identificador de la Cotizacion.
     * @param request cuerpo opcional con el correo destino.
     * @return 200 OK con el {@link CotizacionDto} actualizado (incluye {@code enviadaEn}).
     */
    @PostMapping("/{id}/enviar-correo")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cotizacion','actualizar')")
    public ResponseEntity<CotizacionDto> enviarCorreo(@PathVariable("id") UUID id,
            @RequestBody(required = false) EnviarCorreoRequest request) {
        String email = (request == null) ? null : request.email();
        return ResponseEntity.ok(servicioCotizaciones.enviarPorCorreo(id, email));
    }

    /**
     * Lista las Cotizaciones del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por Cliente y estado (Req 6.8, 6.9).
     *
     * @param clienteId Cliente a filtrar; opcional.
     * @param estado    etiqueta de estado a filtrar; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link CotizacionDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cotizacion','listar')")
    public PaginaResponse<CotizacionDto> listar(
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "canalVentaId", required = false) UUID canalVentaId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(
                servicioCotizaciones.listarCotizaciones(clienteId, estado, canalVentaId, pageable));
    }

    private static CrearPartidaCommand aComando(PartidaRequest request) {
        return new CrearPartidaCommand(
                request.productoId(), request.descripcion(), request.cantidad(), request.precioUnitario());
    }
}
