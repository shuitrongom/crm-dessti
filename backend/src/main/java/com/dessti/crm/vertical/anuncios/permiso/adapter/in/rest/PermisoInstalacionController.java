package com.dessti.crm.vertical.anuncios.permiso.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
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

import com.dessti.crm.vertical.anuncios.permiso.application.PermisoInstalacionDto;
import com.dessti.crm.vertical.anuncios.permiso.application.ServicioPermisos;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del submodulo de permisos para la gestion de los
 * {@link PermisoInstalacionDto Permisos de Instalacion} (Req 17, 12; tarea 21.2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /permisos-instalacion} — alta con datos obligatorios
 *       ({@code @autorizador.tiene('permiso_instalacion','crear')}); 201 Created con
 *       el id y estado {@code solicitado} (Req 17.1). 422 si un dato obligatorio
 *       falta/es invalido.</li>
 *   <li>{@code GET /permisos-instalacion/{id}} — consulta
 *       ({@code @autorizador.tiene('permiso_instalacion','leer')}); 200 OK; 404 si no
 *       es accesible (Req 23.3).</li>
 *   <li>{@code PUT /permisos-instalacion/{id}/estado?accion=aprobar|rechazar} —
 *       cambio de estado ({@code @autorizador.tiene('permiso_instalacion','cambiar_estado')});
 *       200 OK; 409 si la transicion es invalida (Req 17.2, 17.3).</li>
 *   <li>{@code GET /permisos-instalacion?sitioId=&tipo=&estado=&page=&size=} —
 *       listado paginado (20/100) con filtros
 *       ({@code @autorizador.tiene('permiso_instalacion','listar')}); 200 OK con
 *       {@link PaginaResponse} (Req 17.6).</li>
 *   <li>{@code POST /permisos-instalacion/notificar-vencimientos} — disparo manual
 *       del barrido de vencimientos proximos sobre el tenant en contexto
 *       ({@code @autorizador.tiene('permiso_instalacion','cambiar_estado')}); 200 OK
 *       con el numero de notificaciones emitidas (Req 13.1, 13.4).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.6)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code permiso_instalacion:{crear,leer,listar,cambiar_estado}} ya se sembraron en
 * V5 y se asignaron al rol {@code instalacion} (Req 27.6), por lo que la migracion
 * V20 no requiere sembrar permisos adicionales. La aprobacion y el rechazo
 * reutilizan el permiso {@code cambiar_estado} (son cambios de estado del agregado).</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/permisos-instalacion")
public class PermisoInstalacionController {

    private final ServicioPermisos servicioPermisos;

    public PermisoInstalacionController(ServicioPermisos servicioPermisos) {
        this.servicioPermisos = servicioPermisos;
    }

    /**
     * Crea un Permiso_Instalacion con los datos obligatorios (Req 17.1). Devuelve
     * 201 con el id y estado {@code solicitado}.
     *
     * @param request cuerpo con los datos del permiso.
     * @return 201 Created con el {@link PermisoInstalacionDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('permiso_instalacion','crear')")
    public ResponseEntity<PermisoInstalacionDto> crear(
            @Valid @RequestBody CrearPermisoInstalacionRequest request) {
        PermisoInstalacionDto dto = servicioPermisos.crear(request.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Permiso_Instalacion por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador del Permiso_Instalacion.
     * @return 200 OK con el {@link PermisoInstalacionDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('permiso_instalacion','leer')")
    public ResponseEntity<PermisoInstalacionDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPermisos.consultar(id));
    }

    /**
     * Cambia el estado de un Permiso_Instalacion segun la accion indicada
     * ({@code aprobar} o {@code rechazar}), aplicando la maquina de estados
     * {@code solicitado -> {aprobado|rechazado}} (Req 17.2). 409 si la transicion es
     * invalida (Req 17.3); 404 si no es accesible.
     *
     * @param id     identificador del Permiso_Instalacion.
     * @param accion accion de decision: {@code aprobar} o {@code rechazar}.
     * @return 200 OK con el {@link PermisoInstalacionDto} actualizado.
     * @throws ReglaNegocioException si la accion es desconocida (422).
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('permiso_instalacion','cambiar_estado')")
    public ResponseEntity<PermisoInstalacionDto> cambiarEstado(
            @PathVariable("id") UUID id,
            @RequestParam("accion") String accion) {
        String normalizada = accion == null ? "" : accion.strip().toLowerCase();
        return switch (normalizada) {
            case "aprobar" -> ResponseEntity.ok(servicioPermisos.aprobar(id));
            case "rechazar" -> ResponseEntity.ok(servicioPermisos.rechazar(id));
            default -> throw new ReglaNegocioException(
                    "Accion de cambio de estado desconocida: '" + accion
                            + "'. Use 'aprobar' o 'rechazar'.");
        };
    }

    /**
     * Lista los permisos del tenant de forma paginada (20 por defecto, 100 maximo)
     * con filtros opcionales por Sitio, tipo y estado (Req 17.6).
     *
     * @param sitioId Sitio a filtrar; opcional.
     * @param tipo    etiqueta de tipo a filtrar; opcional.
     * @param estado  etiqueta de estado a filtrar; opcional.
     * @param page    numero de pagina 0-index; opcional.
     * @param size    tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link PermisoInstalacionDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('permiso_instalacion','listar')")
    public PaginaResponse<PermisoInstalacionDto> listar(
            @RequestParam(name = "sitioId", required = false) UUID sitioId,
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPermisos.listar(sitioId, tipo, estado, pageable));
    }

    /**
     * Dispara manualmente el barrido de vencimientos proximos de permisos sobre el
     * tenant en contexto e invoca {@link ServicioPermisos#notificarVencimientosProximos()}
     * (Req 13.1, 13.4). Complementa al planificador diario multi-tenant: permite
     * ejecutar el barrido bajo demanda para el tenant actual (RLS ya fijada por el
     * contexto de la peticion).
     *
     * @return 200 OK con el numero de notificaciones emitidas para el tenant en contexto.
     */
    @PostMapping("/notificar-vencimientos")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('permiso_instalacion','cambiar_estado')")
    public ResponseEntity<NotificarVencimientosResponse> notificarVencimientos() {
        int notificados = servicioPermisos.notificarVencimientosProximos();
        return ResponseEntity.ok(new NotificarVencimientosResponse(notificados));
    }

    /**
     * Respuesta simple del disparo manual de notificaciones de vencimiento.
     *
     * @param notificados numero de permisos por vencer notificados en el tenant en contexto.
     */
    public record NotificarVencimientosResponse(int notificados) {
    }
}
