package com.dessti.crm.contabilidad.cxc.adapter.in.rest;

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

import com.dessti.crm.contabilidad.cxc.application.AplicacionPagoCommand;
import com.dessti.crm.contabilidad.cxc.application.PagoClienteDto;
import com.dessti.crm.contabilidad.cxc.application.RegistrarPagoClienteCommand;
import com.dessti.crm.contabilidad.cxc.application.ServicioCuentasPorCobrar;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo contabilidad-finanzas para la gestion de los
 * {@link PagoClienteDto Pagos de Cliente} (Req 36, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /contabilidad/pagos-cliente} — registrar y aplicar
 *       ({@code @autorizador.tiene('pago_cliente','crear')}); 201 Created. 422 si un
 *       monto excede el saldo de una CxC o el PAC rechaza el complemento; 404 si una
 *       Factura no tiene CxC (Req 36.2, 36.3, 36.4).</li>
 *   <li>{@code GET /contabilidad/pagos-cliente/{id}} — consultar
 *       ({@code @autorizador.tiene('pago_cliente','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 *   <li>{@code GET /contabilidad/pagos-cliente?clienteId=&page=&size=} — listado
 *       paginado ({@code @autorizador.tiene('pago_cliente','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 36.6).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.11)</h2>
 * <p>Los permisos {@code pago_cliente:{crear,leer,listar}} ya se sembraron en V5 y
 * se asignaron al rol {@code contabilidad}; se reutilizan aqui sin re-sembrarlos.</p>
 */
@RestController
@RequestMapping("/contabilidad/pagos-cliente")
public class PagoClienteController {

    private final ServicioCuentasPorCobrar servicioCuentasPorCobrar;

    public PagoClienteController(ServicioCuentasPorCobrar servicioCuentasPorCobrar) {
        this.servicioCuentasPorCobrar = servicioCuentasPorCobrar;
    }

    /**
     * Registra un Pago_Cliente y lo aplica a una o varias Facturas (Req 36.2, 36.3,
     * 36.4). 422 si un monto excede el saldo pendiente o el PAC rechaza el
     * Complemento_Pago; 404 si una Factura no tiene CxC accesible.
     *
     * @param request datos del pago y su desglose de aplicaciones.
     * @return 201 Created con el {@link PagoClienteDto} registrado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('pago_cliente','crear')")
    public ResponseEntity<PagoClienteDto> registrar(
            @Valid @RequestBody RegistrarPagoClienteRequest request) {
        RegistrarPagoClienteCommand comando = new RegistrarPagoClienteCommand(
                request.clienteId(),
                request.monto(),
                request.formaPago(),
                request.esParcialidadEfectiva(),
                request.aplicaciones().stream()
                        .map(linea -> new AplicacionPagoCommand(linea.facturaId(), linea.monto()))
                        .toList());
        PagoClienteDto dto = servicioCuentasPorCobrar.registrarPago(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Pago_Cliente por su identificador con su desglose (Req 23.3). 404
     * si no es accesible.
     *
     * @param id identificador del Pago_Cliente.
     * @return 200 OK con el {@link PagoClienteDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('pago_cliente','leer')")
    public ResponseEntity<PagoClienteDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCuentasPorCobrar.consultarPago(id));
    }

    /**
     * Lista los Pagos de Cliente del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtro opcional por Cliente (Req 36.6).
     *
     * @param clienteId Cliente a filtrar; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link PagoClienteDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('pago_cliente','listar')")
    public PaginaResponse<PagoClienteDto> listar(
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioCuentasPorCobrar.listarPagos(clienteId, pageable));
    }
}
