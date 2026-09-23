package com.dessti.crm.contabilidad.cxc.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.contabilidad.cxc.application.AntiguedadSaldosDto;
import com.dessti.crm.contabilidad.cxc.application.CuentaPorCobrarDto;
import com.dessti.crm.contabilidad.cxc.application.ServicioCuentasPorCobrar;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

/**
 * Adaptador de entrada REST del modulo contabilidad-finanzas para la consulta de
 * las {@link CuentaPorCobrarDto Cuentas_Por_Cobrar} (CxC) y su antiguedad de saldos
 * (Req 36.5, 36.6, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /contabilidad/cuentas-por-cobrar?clienteId=&estado=&page=&size=}
 *       — listado paginado ({@code @autorizador.tiene('cuenta_por_cobrar','listar')});
 *       200 OK con {@link PaginaResponse} (Req 36.6).</li>
 *   <li>{@code GET /contabilidad/cuentas-por-cobrar/aging?clienteId=} — antiguedad
 *       de saldos ({@code @autorizador.tiene('cuenta_por_cobrar','leer')}); 200 OK
 *       con {@link AntiguedadSaldosDto} (Req 36.5).</li>
 *   <li>{@code GET /contabilidad/cuentas-por-cobrar/{id}} — consultar
 *       ({@code @autorizador.tiene('cuenta_por_cobrar','leer')}); 200 OK; 404 si no
 *       es accesible (Req 23.3).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.11)</h2>
 * <p>V5 no sembro el recurso {@code cuenta_por_cobrar} (a diferencia de
 * {@code pago_cliente} y {@code complemento_pago}). La migracion V31 siembra
 * {@code cuenta_por_cobrar:{leer,listar}} y los asigna al rol {@code contabilidad}
 * (a0000000-0000-0000-0000-00000000000b), siguiendo el patron de V30/V9.</p>
 *
 * <p>El orden de las rutas GET declara {@code /aging} antes que {@code /{id}} para
 * que Spring MVC no intente interpretar el literal "aging" como un UUID de {id}.</p>
 */
@RestController
@RequestMapping("/contabilidad/cuentas-por-cobrar")
public class CuentaPorCobrarController {

    private final ServicioCuentasPorCobrar servicioCuentasPorCobrar;

    public CuentaPorCobrarController(ServicioCuentasPorCobrar servicioCuentasPorCobrar) {
        this.servicioCuentasPorCobrar = servicioCuentasPorCobrar;
    }

    /**
     * Lista las Cuentas_Por_Cobrar del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por Cliente y estado (Req 36.6).
     *
     * @param clienteId Cliente a filtrar; opcional.
     * @param estado    etiqueta de estado a filtrar; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link CuentaPorCobrarDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_por_cobrar','listar')")
    public PaginaResponse<CuentaPorCobrarDto> listar(
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioCuentasPorCobrar.listarCxC(clienteId, estado, pageable));
    }

    /**
     * Antiguedad de saldos (aging) de las CxC del tenant con saldo pendiente,
     * agrupada por Cliente y por rango de dias de vencimiento (Req 36.5).
     *
     * @param clienteId Cliente a filtrar; opcional (si es nulo incluye a todos).
     * @return 200 OK con el {@link AntiguedadSaldosDto}.
     */
    @GetMapping("/aging")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_por_cobrar','leer')")
    public ResponseEntity<AntiguedadSaldosDto> aging(
            @RequestParam(name = "clienteId", required = false) UUID clienteId) {
        return ResponseEntity.ok(servicioCuentasPorCobrar.consultarAging(clienteId));
    }

    /**
     * Consulta una Cuenta_Por_Cobrar por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la CxC.
     * @return 200 OK con el {@link CuentaPorCobrarDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_por_cobrar','leer')")
    public ResponseEntity<CuentaPorCobrarDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCuentasPorCobrar.consultarCxC(id));
    }
}
