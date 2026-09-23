package com.dessti.crm.facturacion.notacredito.adapter.in.rest;

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

import com.dessti.crm.facturacion.notacredito.application.EmitirNotaCreditoCommand;
import com.dessti.crm.facturacion.notacredito.application.NotaCreditoDto;
import com.dessti.crm.facturacion.notacredito.application.ServicioNotasCredito;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo facturacion-cfdi para la gestion de las
 * {@link NotaCreditoDto Notas de Credito} (CFDI de egreso) (Req 37, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /facturacion/notas-credito} — emitir
 *       ({@code @autorizador.tiene('nota_credito','crear')}); 201 Created. 422 si
 *       el monto excede el saldo; 404 si la Factura no existe (Req 37.1, 37.2).</li>
 *   <li>{@code GET /facturacion/notas-credito/{id}} — consultar
 *       ({@code @autorizador.tiene('nota_credito','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 *   <li>{@code GET /facturacion/notas-credito?facturaId=&estado=&page=&size=} —
 *       listado paginado ({@code @autorizador.tiene('nota_credito','listar')});
 *       200 OK con {@link PaginaResponse}.</li>
 *   <li>{@code POST /facturacion/notas-credito/{id}/timbrado} — timbrar
 *       ({@code @autorizador.tiene('nota_credito','cambiar_estado')}); 200 OK; 422
 *       si el PAC rechaza (Req 37.1).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.11)</h2>
 * <p>Los permisos {@code nota_credito:{crear,leer}} ya se sembraron en V5 y se
 * asignaron al rol {@code contabilidad}; V30 agrega
 * {@code nota_credito:{listar,cambiar_estado}} al mismo rol para el listado y el
 * timbrado.</p>
 */
@RestController
@RequestMapping("/facturacion/notas-credito")
public class NotaCreditoController {

    private final ServicioNotasCredito servicioNotasCredito;

    public NotaCreditoController(ServicioNotasCredito servicioNotasCredito) {
        this.servicioNotasCredito = servicioNotasCredito;
    }

    /**
     * Emite una Nota de Credito en {@code borrador} referenciando una Factura
     * timbrada (Req 37.1). 422 si el monto excede el saldo disponible; 404 si la
     * Factura no existe en el tenant.
     *
     * @param request datos de la Nota de Credito a emitir.
     * @return 201 Created con el {@link NotaCreditoDto} emitido.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('facturacion') and @autorizador.tiene('nota_credito','crear')")
    public ResponseEntity<NotaCreditoDto> emitir(
            @Valid @RequestBody EmitirNotaCreditoRequest request) {
        NotaCreditoDto dto = servicioNotasCredito.emitir(
                new EmitirNotaCreditoCommand(request.facturaId(), request.monto()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Nota de Credito por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la Nota de Credito.
     * @return 200 OK con el {@link NotaCreditoDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('facturacion') and @autorizador.tiene('nota_credito','leer')")
    public ResponseEntity<NotaCreditoDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioNotasCredito.consultar(id));
    }

    /**
     * Lista las Notas de Credito del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por Factura y estado.
     *
     * @param facturaId Factura a filtrar; opcional.
     * @param estado    etiqueta de estado a filtrar; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link NotaCreditoDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('facturacion') and @autorizador.tiene('nota_credito','listar')")
    public PaginaResponse<NotaCreditoDto> listar(
            @RequestParam(name = "facturaId", required = false) UUID facturaId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioNotasCredito.listar(facturaId, estado, pageable));
    }

    /**
     * Solicita el Timbrado del CFDI de egreso de una Nota de Credito en
     * {@code borrador} ante el PAC (Req 37.1). 200 OK con la Nota {@code timbrada};
     * 422 si el PAC rechaza o la Nota no esta en {@code borrador}; 404 si no es
     * accesible.
     *
     * @param id identificador de la Nota de Credito a timbrar.
     * @return 200 OK con el {@link NotaCreditoDto} timbrado.
     */
    @PostMapping("/{id}/timbrado")
    @PreAuthorize("@autorizador.moduloHabilitado('facturacion') and @autorizador.tiene('nota_credito','cambiar_estado')")
    public ResponseEntity<NotaCreditoDto> timbrar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioNotasCredito.timbrar(id));
    }
}
