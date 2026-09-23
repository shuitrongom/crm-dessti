package com.dessti.crm.facturacion.factura.adapter.in.rest;

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

import com.dessti.crm.facturacion.factura.application.EmitirFacturaCommand;
import com.dessti.crm.facturacion.factura.application.FacturaDto;
import com.dessti.crm.facturacion.factura.application.ServicioFacturas;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo facturacion-cfdi para la gestion de las
 * {@link FacturaDto Facturas} (CFDI) (Req 34, 35, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /facturacion/facturas} — emitir
 *       ({@code @autorizador.tiene('factura','crear')}); 201 Created. 422 datos
 *       invalidos; 404 si el origen no existe (Req 34.1, 34.3).</li>
 *   <li>{@code GET /facturacion/facturas/{id}} — consultar
 *       ({@code @autorizador.tiene('factura','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 *   <li>{@code GET /facturacion/facturas?clienteId=&estado=&page=&size=} — listado
 *       paginado (20/100) con filtros ({@code @autorizador.tiene('factura','listar')});
 *       200 OK con {@link PaginaResponse} (Req 34.4).</li>
 *   <li>{@code POST /facturacion/facturas/{id}/timbrado} — timbrar
 *       ({@code @autorizador.tiene('factura','cambiar_estado')}); 200 OK; 422 si el
 *       PAC rechaza (Req 35.1, 35.2).</li>
 *   <li>{@code POST /facturacion/facturas/{id}/cancelacion} — cancelar
 *       ({@code @autorizador.tiene('factura','cambiar_estado')}); 200 OK; 409 si la
 *       transicion es invalida (Req 35.4, 35.5, 35.7).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.11)</h2>
 * <p>Los permisos {@code factura:{crear,leer,listar,cambiar_estado}} ya se
 * sembraron en V5 y se asignaron al rol {@code contabilidad}, por lo que V30 no
 * requiere sembrar permisos de Factura.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/facturacion/facturas")
public class FacturaController {

    private final ServicioFacturas servicioFacturas;

    public FacturaController(ServicioFacturas servicioFacturas) {
        this.servicioFacturas = servicioFacturas;
    }

    /**
     * Emite una Factura CFDI en estado inicial {@code borrador} a partir de una
     * Cotizacion aprobada o de una Orden_Fabricacion (Req 34.1). 422 si los datos
     * son invalidos; 404 si el origen no existe en el tenant.
     *
     * @param request datos de la Factura a emitir.
     * @return 201 Created con el {@link FacturaDto} emitido.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('facturacion') and @autorizador.tiene('factura','crear')")
    public ResponseEntity<FacturaDto> emitir(@Valid @RequestBody EmitirFacturaRequest request) {
        FacturaDto dto = servicioFacturas.emitir(new EmitirFacturaCommand(
                request.cotizacionId(), request.ordenFabricacionId(),
                request.receptorRfc(), request.receptorNombre(), request.receptorCp(),
                request.receptorRegimenFiscal(), request.usoCfdi(), request.tasaRetencion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Factura por su identificador (Req 23.3). 404 si no es accesible.
     *
     * @param id identificador de la Factura.
     * @return 200 OK con el {@link FacturaDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('facturacion') and @autorizador.tiene('factura','leer')")
    public ResponseEntity<FacturaDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioFacturas.consultar(id));
    }

    /**
     * Lista las Facturas del tenant de forma paginada (20 por defecto, 100 maximo)
     * con filtros opcionales por Cliente y estado (Req 34.4).
     *
     * @param clienteId Cliente a filtrar; opcional.
     * @param estado    etiqueta de estado a filtrar; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link FacturaDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('facturacion') and @autorizador.tiene('factura','listar')")
    public PaginaResponse<FacturaDto> listar(
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioFacturas.listar(clienteId, estado, pageable));
    }

    /**
     * Solicita el Timbrado de una Factura en {@code borrador} ante el PAC (Req 35.1,
     * 35.2). 200 OK con la Factura {@code timbrada}; 422 si el PAC rechaza o la
     * Factura no esta en {@code borrador}; 404 si no es accesible.
     *
     * @param id identificador de la Factura a timbrar.
     * @return 200 OK con el {@link FacturaDto} timbrado.
     */
    @PostMapping("/{id}/timbrado")
    @PreAuthorize("@autorizador.moduloHabilitado('facturacion') and @autorizador.tiene('factura','cambiar_estado')")
    public ResponseEntity<FacturaDto> timbrar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioFacturas.timbrar(id));
    }

    /**
     * Solicita la cancelacion de una Factura timbrada con un motivo del catalogo del
     * SAT (Req 35.4, 35.5). 200 OK con la Factura {@code cancelada}; 409 si la
     * transicion es invalida; 422 si el PAC rechaza; 404 si no es accesible.
     *
     * @param id      identificador de la Factura a cancelar.
     * @param request motivo de cancelacion del SAT.
     * @return 200 OK con el {@link FacturaDto} cancelado.
     */
    @PostMapping("/{id}/cancelacion")
    @PreAuthorize("@autorizador.moduloHabilitado('facturacion') and @autorizador.tiene('factura','cambiar_estado')")
    public ResponseEntity<FacturaDto> cancelar(@PathVariable("id") UUID id,
                                               @Valid @RequestBody CancelarFacturaRequest request) {
        return ResponseEntity.ok(servicioFacturas.cancelar(id, request.motivoSat()));
    }
}
