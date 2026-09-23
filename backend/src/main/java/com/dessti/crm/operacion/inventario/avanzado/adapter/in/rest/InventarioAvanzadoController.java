package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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

import com.dessti.crm.operacion.inventario.avanzado.application.ActualizarAlmacenCommand;
import com.dessti.crm.operacion.inventario.avanzado.application.AlmacenDto;
import com.dessti.crm.operacion.inventario.avanzado.application.ConfigInventarioMaterialDto;
import com.dessti.crm.operacion.inventario.avanzado.application.ConfigurarInventarioMaterialCommand;
import com.dessti.crm.operacion.inventario.avanzado.application.CrearAlmacenCommand;
import com.dessti.crm.operacion.inventario.avanzado.application.ExistenciaAlmacenDto;
import com.dessti.crm.operacion.inventario.avanzado.application.CrearLoteCommand;
import com.dessti.crm.operacion.inventario.avanzado.application.LoteDto;
import com.dessti.crm.operacion.inventario.avanzado.application.MovimientoAlmacenDto;
import com.dessti.crm.operacion.inventario.avanzado.application.RegistrarMovimientoAlmacenCommand;
import com.dessti.crm.operacion.inventario.avanzado.application.TransferirCommand;
import com.dessti.crm.operacion.inventario.avanzado.application.ServicioInventarioAvanzado;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo operacion-produccion para el inventario AVANZADO
 * por Almacen (Req 60; tarea 23.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /inventario-avanzado/almacenes} — alta de Almacen
 *       ({@code @autorizador.tiene('almacen','crear')}); 201 Created.</li>
 *   <li>{@code GET /inventario-avanzado/almacenes/{id}} — consulta
 *       ({@code @autorizador.tiene('almacen','leer')}); 200 OK; 404 si no es accesible.</li>
 *   <li>{@code GET /inventario-avanzado/almacenes} — listado paginado (20/100) con filtros
 *       por nombre y estado ({@code @autorizador.tiene('almacen','listar')}); 200 OK.</li>
 *   <li>{@code PUT /inventario-avanzado/almacenes/{id}} — edicion de Almacen
 *       ({@code @autorizador.tiene('almacen','actualizar')}); 200 OK; 404 si no es accesible.</li>
 *   <li>{@code PUT /inventario-avanzado/materiales/{materialId}/config-inventario} —
 *       configuracion de inventario del Material (upsert)
 *       ({@code @autorizador.tiene('material','actualizar')}); 200 OK; 404 si el Material
 *       no es accesible; 422 si algun parametro es invalido.</li>
 *   <li>{@code GET /inventario-avanzado/almacenes/{almacenId}/materiales/{materialId}/kardex}
 *       — Kardex cronologico de solo lectura ({@code @autorizador.tiene('kardex','leer')});
 *       200 OK con {@link PaginaResponse}.</li>
 *   <li>{@code GET /inventario-avanzado/existencias} — listado de existencias por Almacen/
 *       Material ({@code @autorizador.tiene('material','leer')}); 200 OK.</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.5)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code almacen:{crear,leer,listar}}, {@code kardex:leer} y
 * {@code material:{leer,actualizar}} ya se sembraron y asignaron al rol {@code almacen}
 * (V5, Req 27.5); la migracion V26 agrega {@code almacen:actualizar} para la edicion.</p>
 *
 * <h2>Alcance (23.1)</h2>
 * <p>El registro de entradas/salidas y transferencias con costeo (motor promedio/PEPS,
 * lotes) lo aporta la tarea 23.2; este controlador expone CRUD de Almacenes,
 * configuracion por Material, Kardex de solo lectura y listado de existencias.</p>
 */
@RestController
@RequestMapping("/inventario-avanzado")
public class InventarioAvanzadoController {

    private final ServicioInventarioAvanzado servicio;

    public InventarioAvanzadoController(ServicioInventarioAvanzado servicio) {
        this.servicio = servicio;
    }

    /**
     * Da de alta un Almacen (Req 60). Devuelve 201 con el Almacen creado.
     *
     * @param request cuerpo con nombre y tipo.
     * @return 201 Created con el {@link AlmacenDto} creado.
     */
    @PostMapping("/almacenes")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('almacen','crear')")
    public ResponseEntity<AlmacenDto> crearAlmacen(@Valid @RequestBody CrearAlmacenRequest request) {
        AlmacenDto dto = servicio.crearAlmacen(
                new CrearAlmacenCommand(request.nombre(), request.tipo()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Almacen por su identificador (Req 23.3). 404 si no es accesible.
     *
     * @param id identificador del Almacen.
     * @return 200 OK con el {@link AlmacenDto}.
     */
    @GetMapping("/almacenes/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('almacen','leer')")
    public ResponseEntity<AlmacenDto> consultarAlmacen(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicio.consultarAlmacen(id));
    }

    /**
     * Lista los Almacenes del tenant de forma paginada (20 por defecto, 100 maximo) con
     * filtros opcionales por nombre y por estado activo (Req 60).
     *
     * @param nombre fragmento del nombre a filtrar; opcional.
     * @param activo estado activo a filtrar; opcional ({@code null} no filtra).
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link AlmacenDto}.
     */
    @GetMapping("/almacenes")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('almacen','listar')")
    public PaginaResponse<AlmacenDto> listarAlmacenes(
            @RequestParam(name = "nombre", required = false) String nombre,
            @RequestParam(name = "activo", required = false) Boolean activo,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicio.listarAlmacenes(nombre, activo, pageable));
    }

    /**
     * Edita (renombra/reclasifica) un Almacen (Req 60). 404 si no es accesible.
     *
     * @param id      identificador del Almacen.
     * @param request nuevos nombre y tipo.
     * @return 200 OK con el {@link AlmacenDto} actualizado.
     */
    @PutMapping("/almacenes/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('almacen','actualizar')")
    public ResponseEntity<AlmacenDto> actualizarAlmacen(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarAlmacenRequest request) {
        AlmacenDto dto = servicio.actualizarAlmacen(
                id, new ActualizarAlmacenCommand(request.nombre(), request.tipo()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Configura (upsert) el inventario avanzado de un Material (Req 60). 404 si el Material
     * no es accesible; 422 si algun parametro es invalido.
     *
     * @param materialId identificador del Material.
     * @param request    parametros de configuracion.
     * @return 200 OK con el {@link ConfigInventarioMaterialDto} resultante.
     */
    @PutMapping("/materiales/{materialId}/config-inventario")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('material','actualizar')")
    public ResponseEntity<ConfigInventarioMaterialDto> configurarInventarioMaterial(
            @PathVariable("materialId") UUID materialId,
            @Valid @RequestBody ConfigurarInventarioMaterialRequest request) {
        ConfigInventarioMaterialDto dto = servicio.configurarInventarioMaterial(
                materialId,
                new ConfigurarInventarioMaterialCommand(
                        request.metodoCosteo(), request.stockMaximo(), request.controlLote(),
                        request.consumoPromedio(), request.tiempoEntregaDias(),
                        request.stockSeguridad()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Lista el Kardex cronologico de un Material en un Almacen (Req 60), de solo lectura,
     * con rango de fechas opcional. 404 si el Almacen o el Material no son accesibles.
     *
     * @param almacenId  Almacen cuyo Kardex se consulta.
     * @param materialId Material cuyo Kardex se consulta.
     * @param desde      instante minimo ISO-8601 (inclusive); opcional.
     * @param hasta      instante maximo ISO-8601 (inclusive); opcional.
     * @param page       numero de pagina 0-index; opcional.
     * @param size       tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link MovimientoAlmacenDto}.
     */
    @GetMapping("/almacenes/{almacenId}/materiales/{materialId}/kardex")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('kardex','leer')")
    public PaginaResponse<MovimientoAlmacenDto> consultarKardex(
            @PathVariable("almacenId") UUID almacenId,
            @PathVariable("materialId") UUID materialId,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(
                servicio.consultarKardex(almacenId, materialId, desde, hasta, pageable));
    }

    /**
     * Lista los saldos de existencias por Almacen y/o Material (Req 60).
     *
     * @param almacenId  Almacen a filtrar; opcional.
     * @param materialId Material a filtrar; opcional.
     * @param page       numero de pagina 0-index; opcional.
     * @param size       tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ExistenciaAlmacenDto}.
     */
    @GetMapping("/existencias")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('material','leer')")
    public PaginaResponse<ExistenciaAlmacenDto> listarExistencias(
            @RequestParam(name = "almacenId", required = false) UUID almacenId,
            @RequestParam(name = "materialId", required = false) UUID materialId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicio.listarExistencias(almacenId, materialId, pageable));
    }

    // ------------------------------------------------------------------
    // Movimientos con costeo, transferencias y lotes (Req 60, tarea 23.2)
    // ------------------------------------------------------------------

    /**
     * Registra una ENTRADA de inventario en un Almacen con costeo (promedio/PEPS) y
     * actualiza el saldo perpetuo (Req 60.5, 60.10, 60.11). 201 Created con la fila de
     * Kardex. 404 si el Almacen o el Material no son accesibles; 422 si cantidad/costo
     * son invalidos.
     *
     * @param almacenId Almacen destino de la entrada.
     * @param request   Material, lote opcional, cantidad y costo unitario.
     * @return 201 Created con el {@link MovimientoAlmacenDto} registrado.
     */
    @PostMapping("/almacenes/{almacenId}/entradas")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('movimiento_inventario','crear')")
    public ResponseEntity<MovimientoAlmacenDto> registrarEntrada(
            @PathVariable("almacenId") UUID almacenId,
            @Valid @RequestBody RegistrarEntradaRequest request) {
        MovimientoAlmacenDto dto = servicio.registrarEntrada(new RegistrarMovimientoAlmacenCommand(
                almacenId, request.materialId(), request.loteCodigo(), request.cantidad(),
                request.costoUnitario(), request.motivo()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Registra una SALIDA de inventario de un Almacen determinando el costo segun el metodo
     * configurado del Material (Req 60.5, 60.10, 60.11). 201 Created con la fila de Kardex.
     * 404 si el Almacen o el Material no son accesibles; 422 si la cantidad es invalida o
     * excede el saldo ("existencias insuficientes").
     *
     * @param almacenId Almacen origen de la salida.
     * @param request   Material, lote opcional, cantidad.
     * @return 201 Created con el {@link MovimientoAlmacenDto} registrado.
     */
    @PostMapping("/almacenes/{almacenId}/salidas")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('movimiento_inventario','crear')")
    public ResponseEntity<MovimientoAlmacenDto> registrarSalida(
            @PathVariable("almacenId") UUID almacenId,
            @Valid @RequestBody RegistrarSalidaRequest request) {
        MovimientoAlmacenDto dto = servicio.registrarSalida(new RegistrarMovimientoAlmacenCommand(
                almacenId, request.materialId(), request.loteCodigo(), request.cantidad(),
                null, request.motivo()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Transfiere una cantidad de un Material entre dos Almacenes (Req 60.13): salida en el
     * origen y entrada por la misma cantidad en el destino, conservando el costo. 201 Created
     * con la pata de ENTRADA en el destino. 404 si algun Almacen o el Material no son
     * accesibles; 422 si origen == destino, cantidad invalida o saldo insuficiente.
     *
     * @param request Almacen origen, Almacen destino, Material y cantidad.
     * @return 201 Created con el {@link MovimientoAlmacenDto} de la entrada en destino.
     */
    @PostMapping("/transferencias")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('movimiento_inventario','crear')")
    public ResponseEntity<MovimientoAlmacenDto> transferir(
            @Valid @RequestBody TransferirRequest request) {
        MovimientoAlmacenDto dto = servicio.transferir(new TransferirCommand(
                request.almacenOrigenId(), request.almacenDestinoId(), request.materialId(),
                request.cantidad(), request.motivo()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Da de alta un Lote de un Material (Req 60.4). 201 Created con el Lote. 404 si el
     * Material no es accesible; 422 si el codigo es invalido o ya existe.
     *
     * @param materialId Material del Lote.
     * @param request    codigo y caducidad opcional.
     * @return 201 Created con el {@link LoteDto} creado.
     */
    @PostMapping("/materiales/{materialId}/lotes")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('lote','crear')")
    public ResponseEntity<LoteDto> crearLote(
            @PathVariable("materialId") UUID materialId,
            @Valid @RequestBody CrearLoteRequest request) {
        LoteDto dto = servicio.crearLote(new CrearLoteCommand(
                materialId, request.codigo(), request.fechaCaducidad()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista los Lotes de un Material del tenant de forma paginada (Req 60.4). 404 si el
     * Material no es accesible.
     *
     * @param materialId Material cuyos Lotes se listan.
     * @param page       numero de pagina 0-index; opcional.
     * @param size       tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link LoteDto}.
     */
    @GetMapping("/materiales/{materialId}/lotes")
    @PreAuthorize("@autorizador.moduloHabilitado('inventario-avanzado') and @autorizador.tiene('lote','listar')")
    public PaginaResponse<LoteDto> listarLotes(
            @PathVariable("materialId") UUID materialId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicio.listarLotes(materialId, pageable));
    }
}
