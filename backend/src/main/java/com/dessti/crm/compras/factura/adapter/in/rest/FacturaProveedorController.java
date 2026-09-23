package com.dessti.crm.compras.factura.adapter.in.rest;

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

import com.dessti.crm.compras.factura.application.FacturaProveedorDto;
import com.dessti.crm.compras.factura.application.RegistrarFacturaProveedorCommand;
import com.dessti.crm.compras.factura.application.ServicioFacturasProveedor;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo compras-abastecimiento para la gestion de
 * las {@link FacturaProveedorDto Facturas de Proveedor} y su Conciliacion_Tres_Vias
 * (Req 33, 12; tarea 27.2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /compras/facturas-proveedor} — alta
 *       ({@code @autorizador.tiene('factura_proveedor','crear')}); 201 Created. 422
 *       si los datos son invalidos; 404 si la Orden_Compra no existe (Req 33.1,
 *       33.2).</li>
 *   <li>{@code GET /compras/facturas-proveedor/{id}} — consulta
 *       ({@code @autorizador.tiene('factura_proveedor','leer')}); 200 OK; 404 si no
 *       es accesible (Req 23.3).</li>
 *   <li>{@code GET /compras/facturas-proveedor?proveedorId=&ordenCompraId=&estado=&page=&size=}
 *       — listado paginado (20/100) con filtros
 *       ({@code @autorizador.tiene('factura_proveedor','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 33.8).</li>
 *   <li>{@code POST /compras/facturas-proveedor/{id}/conciliar} — ejecuta la
 *       Conciliacion_Tres_Vias
 *       ({@code @autorizador.tiene('factura_proveedor','cambiar_estado')}); 200 OK;
 *       marca {@code conciliada} o {@code discrepancia} (Req 33.3–33.5).</li>
 *   <li>{@code POST /compras/facturas-proveedor/{id}/autorizar-pago} — autoriza el
 *       pago desde {@code conciliada}
 *       ({@code @autorizador.tiene('factura_proveedor','cambiar_estado')}); 200 OK;
 *       422 si la factura no esta conciliada (Req 33.7).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.5)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code factura_proveedor:{crear,leer,listar}} se sembraron en V5 y
 * {@code factura_proveedor:cambiar_estado} se siembra en V29; todos se asignan al
 * rol {@code almacen} (Req 27.5).</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/compras/facturas-proveedor")
public class FacturaProveedorController {

    private final ServicioFacturasProveedor servicioFacturas;

    public FacturaProveedorController(ServicioFacturasProveedor servicioFacturas) {
        this.servicioFacturas = servicioFacturas;
    }

    /**
     * Registra una Factura_Proveedor en estado {@code registrada} asociada a una
     * Orden_Compra existente (Req 33.1, 33.2). 422 si los datos son invalidos; 404
     * si la Orden_Compra no existe.
     *
     * @param request datos de la factura a registrar.
     * @return 201 Created con el {@link FacturaProveedorDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('factura_proveedor','crear')")
    public ResponseEntity<FacturaProveedorDto> registrar(
            @Valid @RequestBody RegistrarFacturaProveedorRequest request) {
        FacturaProveedorDto dto = servicioFacturas.registrarFactura(
                new RegistrarFacturaProveedorCommand(
                        request.ordenCompraId(), request.folioProveedor(), request.monto()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Factura_Proveedor por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la factura.
     * @return 200 OK con el {@link FacturaProveedorDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('factura_proveedor','leer')")
    public ResponseEntity<FacturaProveedorDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioFacturas.consultar(id));
    }

    /**
     * Ejecuta la Conciliacion_Tres_Vias de la factura (Req 33.3–33.5); marca la
     * factura {@code conciliada} o {@code discrepancia}. 422 si la factura no esta en
     * estado {@code registrada}; 404 si no es accesible.
     *
     * @param id identificador de la factura.
     * @return 200 OK con el {@link FacturaProveedorDto} en su nuevo estado.
     */
    @PostMapping("/{id}/conciliar")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('factura_proveedor','cambiar_estado')")
    public ResponseEntity<FacturaProveedorDto> conciliar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioFacturas.conciliar(id));
    }

    /**
     * Autoriza el pago de la factura desde el estado {@code conciliada}, llevandola a
     * {@code pagada} (Req 33.7). 422 si la factura no esta conciliada; 404 si no es
     * accesible.
     *
     * @param id identificador de la factura.
     * @return 200 OK con el {@link FacturaProveedorDto} en estado {@code pagada}.
     */
    @PostMapping("/{id}/autorizar-pago")
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('factura_proveedor','cambiar_estado')")
    public ResponseEntity<FacturaProveedorDto> autorizarPago(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioFacturas.autorizarPago(id));
    }

    /**
     * Lista las Facturas de Proveedor del tenant de forma paginada (20 por defecto,
     * 100 maximo) con filtros opcionales por Proveedor, Orden_Compra y estado
     * (Req 33.8).
     *
     * @param proveedorId   Proveedor a filtrar; opcional.
     * @param ordenCompraId Orden_Compra a filtrar; opcional.
     * @param estado        etiqueta de estado a filtrar; opcional.
     * @param page          numero de pagina 0-index; opcional.
     * @param size          tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link FacturaProveedorDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('compras') and @autorizador.tiene('factura_proveedor','listar')")
    public PaginaResponse<FacturaProveedorDto> listar(
            @RequestParam(name = "proveedorId", required = false) UUID proveedorId,
            @RequestParam(name = "ordenCompraId", required = false) UUID ordenCompraId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(
                servicioFacturas.listar(proveedorId, ordenCompraId, estado, pageable));
    }
}
