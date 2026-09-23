package com.dessti.crm.contabilidad.cxp.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.contabilidad.cxp.application.AntiguedadSaldosProveedorDto;
import com.dessti.crm.contabilidad.cxp.application.CuentaPorPagarDto;
import com.dessti.crm.contabilidad.cxp.application.ServicioCuentasPorPagar;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo contabilidad-finanzas para la consulta de las
 * {@link CuentaPorPagarDto Cuentas_Por_Pagar} (CxP), su antiguedad de saldos y la
 * aplicacion de pagos (Req 42.3, 42.4, 42.5, 42.6, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /contabilidad/cuentas-por-pagar?proveedorId=&estado=&page=&size=}
 *       — listado paginado ({@code @autorizador.tiene('cuenta_por_pagar','listar')});
 *       200 OK con {@link PaginaResponse} (Req 42.6).</li>
 *   <li>{@code GET /contabilidad/cuentas-por-pagar/aging?proveedorId=} — antiguedad
 *       de saldos por Proveedor ({@code @autorizador.tiene('cuenta_por_pagar','leer')});
 *       200 OK (Req 42.5).</li>
 *   <li>{@code GET /contabilidad/cuentas-por-pagar/{id}} — consultar
 *       ({@code @autorizador.tiene('cuenta_por_pagar','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 *   <li>{@code POST /contabilidad/cuentas-por-pagar/{id}/pagos} — aplicar un pago
 *       ({@code @autorizador.tiene('cuenta_por_pagar','aplicar_pago')}); 200 OK con la
 *       CxP actualizada; 422 si el monto excede el saldo (Req 42.3, 42.4).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.11)</h2>
 * <p>{@code cuenta_por_pagar:{leer,listar}} se sembraron en V5. La operacion de
 * aplicacion de pago usa el permiso {@code cuenta_por_pagar:aplicar_pago} que la
 * migracion V33 siembra y asigna al rol {@code contabilidad}
 * (a0000000-0000-0000-0000-00000000000b), pues es una operacion distinta de
 * leer/listar (Req 42.3).</p>
 *
 * <p>El orden de las rutas GET declara {@code /aging} antes que {@code /{id}} para
 * que Spring MVC no intente interpretar el literal "aging" como un UUID de {id}.</p>
 */
@RestController
@RequestMapping("/contabilidad/cuentas-por-pagar")
public class CuentaPorPagarController {

    private final ServicioCuentasPorPagar servicioCuentasPorPagar;

    public CuentaPorPagarController(ServicioCuentasPorPagar servicioCuentasPorPagar) {
        this.servicioCuentasPorPagar = servicioCuentasPorPagar;
    }

    /**
     * Lista las Cuentas_Por_Pagar del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por Proveedor y estado (Req 42.6).
     *
     * @param proveedorId Proveedor a filtrar; opcional.
     * @param estado      etiqueta de estado a filtrar; opcional.
     * @param page        numero de pagina 0-index; opcional.
     * @param size        tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link CuentaPorPagarDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_por_pagar','listar')")
    public PaginaResponse<CuentaPorPagarDto> listar(
            @RequestParam(name = "proveedorId", required = false) UUID proveedorId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioCuentasPorPagar.listarCxP(proveedorId, estado, pageable));
    }

    /**
     * Antiguedad de saldos (aging) de las CxP del tenant con saldo pendiente,
     * agrupada por Proveedor y por rango de dias de vencimiento (Req 42.5).
     *
     * @param proveedorId Proveedor a filtrar; opcional (si es nulo incluye a todos).
     * @return 200 OK con el {@link AntiguedadSaldosProveedorDto}.
     */
    @GetMapping("/aging")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_por_pagar','leer')")
    public ResponseEntity<AntiguedadSaldosProveedorDto> aging(
            @RequestParam(name = "proveedorId", required = false) UUID proveedorId) {
        return ResponseEntity.ok(servicioCuentasPorPagar.consultarAging(proveedorId));
    }

    /**
     * Consulta una Cuenta_Por_Pagar por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la CxP.
     * @return 200 OK con el {@link CuentaPorPagarDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_por_pagar','leer')")
    public ResponseEntity<CuentaPorPagarDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCuentasPorPagar.consultarCxP(id));
    }

    /**
     * Aplica un pago a una Cuenta_Por_Pagar disminuyendo su saldo, acotado por el
     * saldo (Req 42.3, 42.4). 422 si el monto excede el saldo, informando el
     * excedente y conservando la CxP; al liquidar la CxP marca la Factura_Proveedor
     * asociada como pagada (Req 42.3).
     *
     * @param id      identificador de la CxP.
     * @param request monto a aplicar.
     * @return 200 OK con el {@link CuentaPorPagarDto} actualizado.
     */
    @PostMapping("/{id}/pagos")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_por_pagar','aplicar_pago')")
    public ResponseEntity<CuentaPorPagarDto> aplicarPago(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AplicarPagoCxpRequest request) {
        return ResponseEntity.ok(servicioCuentasPorPagar.aplicarPago(id, request.monto()));
    }
}
