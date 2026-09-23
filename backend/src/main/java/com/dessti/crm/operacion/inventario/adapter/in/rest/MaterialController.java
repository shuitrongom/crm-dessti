package com.dessti.crm.operacion.inventario.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.operacion.inventario.application.MaterialDto;
import com.dessti.crm.operacion.inventario.application.MovimientoInventarioDto;
import com.dessti.crm.operacion.inventario.application.ServicioInventario;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo operacion-produccion para el inventario de
 * Materiales (Req 18, 12; tarea 20.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /materiales} — alta de Material
 *       ({@code @autorizador.tiene('material','crear')}); 201 Created con el id y
 *       existencias 0 (Req 18.1). 422 si los datos son invalidos.</li>
 *   <li>{@code GET /materiales/{id}} — consulta
 *       ({@code @autorizador.tiene('material','leer')}); 200 OK; 404 si no es accesible
 *       (Req 23.3).</li>
 *   <li>{@code GET /materiales?nombre=&stockBajo=&page=&size=} — listado paginado
 *       (20/100) con filtros por nombre y por condicion de stock bajo
 *       ({@code @autorizador.tiene('material','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 18.6).</li>
 *   <li>{@code POST /materiales/{id}/movimientos} — registra un Movimiento_Inventario
 *       ({@code @autorizador.tiene('movimiento_inventario','crear')}); 201 Created; 422
 *       si una salida dejaria existencias &lt; 0 (Req 18.2, 18.3); 404 si el Material no
 *       es accesible.</li>
 *   <li>{@code DELETE /materiales/{id}} — baja logica del Material
 *       ({@code @autorizador.tiene('material','eliminar')}); 200 OK con el Material
 *       desactivado.</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.5)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code material:{crear,leer,listar,actualizar}} y
 * {@code movimiento_inventario:{crear,leer,listar}} ya se sembraron en V5 y se asignaron
 * al rol {@code almacen} (Req 27.5); la migracion V18 agrega {@code material:eliminar}
 * para la baja logica.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el {@code tenant_id} y
 * el actor se derivan del contexto. El manejo de errores lo centraliza
 * {@code ManejadorGlobalErrores} (422 regla de negocio, 404 no encontrado, 409 conflicto
 * de concurrencia optimista).</p>
 */
@RestController
@RequestMapping("/materiales")
public class MaterialController {

    private final ServicioInventario servicioInventario;

    public MaterialController(ServicioInventario servicioInventario) {
        this.servicioInventario = servicioInventario;
    }

    /**
     * Da de alta un Material con existencias iniciales 0 (Req 18.1). Devuelve 201 con el id.
     *
     * @param request cuerpo con nombre, unidad de medida y stock minimo.
     * @return 201 Created con el {@link MaterialDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('material','crear')")
    public ResponseEntity<MaterialDto> crear(@Valid @RequestBody CrearMaterialRequest request) {
        MaterialDto dto = servicioInventario.crearMaterial(
                request.nombre(), request.unidadMedida(), request.stockMinimo());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Material por su identificador (Req 23.3). 404 si no es accesible.
     *
     * @param id identificador del Material.
     * @return 200 OK con el {@link MaterialDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('material','leer')")
    public ResponseEntity<MaterialDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioInventario.consultar(id));
    }

    /**
     * Lista los Materiales del tenant de forma paginada (20 por defecto, 100 maximo) con
     * filtros opcionales por nombre y por condicion de stock bajo (Req 18.6).
     *
     * @param nombre    fragmento del nombre a filtrar; opcional.
     * @param stockBajo si {@code true}, restringe a Materiales en stock bajo; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link MaterialDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('material','listar')")
    public PaginaResponse<MaterialDto> listar(
            @RequestParam(name = "nombre", required = false) String nombre,
            @RequestParam(name = "stockBajo", required = false, defaultValue = "false") boolean stockBajo,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioInventario.listarMateriales(nombre, stockBajo, pageable));
    }

    /**
     * Registra un Movimiento_Inventario sobre un Material y actualiza sus existencias
     * (Req 18.2). 422 si una salida dejaria existencias &lt; 0 (Req 18.3); 404 si el
     * Material no es accesible.
     *
     * @param id      identificador del Material.
     * @param request tipo, cantidad y motivo del movimiento.
     * @return 201 Created con el {@link MovimientoInventarioDto} registrado.
     */
    @PostMapping("/{id}/movimientos")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('movimiento_inventario','crear')")
    public ResponseEntity<MovimientoInventarioDto> registrarMovimiento(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RegistrarMovimientoRequest request) {
        MovimientoInventarioDto dto = servicioInventario.registrarMovimiento(
                id, request.tipo(), request.cantidad(), request.motivo());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Da de baja logica un Material (Req 18, 3.1). 404 si no es accesible.
     *
     * @param id identificador del Material.
     * @return 200 OK con el {@link MaterialDto} desactivado.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('material','eliminar')")
    public ResponseEntity<MaterialDto> desactivar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioInventario.desactivar(id));
    }
}
