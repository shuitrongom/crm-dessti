package com.dessti.crm.platform.empresas.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

import com.dessti.crm.platform.empresas.CrearSuscripcionCommand;
import com.dessti.crm.platform.empresas.ServicioSuscripciones;
import com.dessti.crm.platform.empresas.SuscripcionDto;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para la gestion de plataforma de
 * {@link SuscripcionDto Suscripciones} por el {@code super_admin} (Req 25,
 * tarea 14.2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /suscripciones} — asocia una Empresa con un Plan (Req 25.2).</li>
 *   <li>{@code POST /suscripciones/{id}/activar|suspender|cancelar} —
 *       transiciones de estado (Req 25.2).</li>
 *   <li>{@code PUT /suscripciones/{id}/vigencia} — actualiza la vigencia
 *       (Req 25.2).</li>
 *   <li>{@code POST /suscripciones/{id}/activar-facturacion} — activa la
 *       facturacion de un Contrato en prueba ({@code EN_PRUEBA} &rarr;
 *       {@code ACTIVA}; Req 8.1, 8.4).</li>
 *   <li>{@code POST /suscripciones/{id}/extender-prueba} — extiende el periodo
 *       de prueba del Contrato (Req 8).</li>
 *   <li>{@code POST /suscripciones/{id}/convertir-a-plan} — convierte un
 *       Contrato de Suscripcion a un Contrato de Plan (Req 9.2).</li>
 *   <li>{@code GET /suscripciones/{id}} — consulta una Suscripcion (Req 25.2).</li>
 *   <li>{@code GET /suscripciones?tenantId=...} — lista las Suscripciones de una
 *       Empresa (Req 25.2).</li>
 * </ul>
 *
 * <h2>Autorizacion de plataforma</h2>
 * <p>Cada operacion exige el permiso atomico de plataforma correspondiente sobre
 * el recurso {@code suscripcion} ({@code suscripcion:crear},
 * {@code suscripcion:cambiar_estado}, {@code suscripcion:actualizar},
 * {@code suscripcion:leer}, {@code suscripcion:listar}), sembrados en V5 y
 * asignados <strong>unicamente</strong> al rol {@code super_admin}; cualquier
 * otro usuario recibe 403 (denegacion por defecto, Req 3.2).</p>
 */
@RestController
@RequestMapping("/suscripciones")
public class SuscripcionController {

    private final ServicioSuscripciones servicioSuscripciones;

    public SuscripcionController(ServicioSuscripciones servicioSuscripciones) {
        this.servicioSuscripciones = servicioSuscripciones;
    }

    /**
     * Asocia una Suscripcion entre una Empresa y un Plan (Req 25.2). Una Empresa
     * o Plan inexistente produce 404; una vigencia invalida, 422.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('suscripcion','crear')")
    public ResponseEntity<SuscripcionDto> crear(@Valid @RequestBody CrearSuscripcionRequest request) {
        SuscripcionDto dto = servicioSuscripciones.crearSuscripcion(new CrearSuscripcionCommand(
                request.tenantId(), request.planId(),
                request.vigenciaInicio(), request.vigenciaFin()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /** Activa una Suscripcion (Req 25.2). Cancelada produce 422; inexistente 404. */
    @PostMapping("/{id}/activar")
    @PreAuthorize("@autorizador.tiene('suscripcion','cambiar_estado')")
    public ResponseEntity<SuscripcionDto> activar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioSuscripciones.activar(id));
    }

    /** Suspende una Suscripcion (Req 25.2). Cancelada produce 422; inexistente 404. */
    @PostMapping("/{id}/suspender")
    @PreAuthorize("@autorizador.tiene('suscripcion','cambiar_estado')")
    public ResponseEntity<SuscripcionDto> suspender(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioSuscripciones.suspender(id));
    }

    /** Cancela una Suscripcion (Req 25.2); estado final. Inexistente produce 404. */
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("@autorizador.tiene('suscripcion','cambiar_estado')")
    public ResponseEntity<SuscripcionDto> cancelar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioSuscripciones.cancelar(id));
    }

    /**
     * Actualiza la vigencia de una Suscripcion (Req 25.2). Una vigencia invalida
     * produce 422; una Suscripcion inexistente, 404.
     */
    @PutMapping("/{id}/vigencia")
    @PreAuthorize("@autorizador.tiene('suscripcion','actualizar')")
    public ResponseEntity<SuscripcionDto> actualizarVigencia(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarVigenciaRequest request) {
        return ResponseEntity.ok(servicioSuscripciones.actualizarVigencia(
                id, request.vigenciaInicio(), request.vigenciaFin()));
    }

    /**
     * Activa la facturacion de un Contrato en periodo de prueba, transicionando
     * de {@code EN_PRUEBA} a {@code ACTIVA} (Req 8.1, 8.4). El cuerpo es
     * <strong>opcional</strong>: si se omite (o si sus campos son {@code null}),
     * el servicio calcula el inicio de facturacion y el nuevo fin de vigencia
     * por omision a partir del paquete. Un Contrato que no esta {@code EN_PRUEBA}
     * produce 422; uno inexistente, 404.
     */
    @PostMapping("/{id}/activar-facturacion")
    @PreAuthorize("@autorizador.tiene('suscripcion','cambiar_estado')")
    public ResponseEntity<SuscripcionDto> activarFacturacion(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ActivarFacturacionRequest request) {
        LocalDate inicioFacturacion = request != null ? request.inicioFacturacion() : null;
        LocalDate nuevaVigenciaFin = request != null ? request.nuevaVigenciaFin() : null;
        return ResponseEntity.ok(servicioSuscripciones.activarFacturacion(
                id, inicioFacturacion, nuevaVigenciaFin));
    }

    /**
     * Extiende el periodo de prueba de un Contrato (Req 8). El nuevo fin de
     * vigencia queda acotado a la duracion del paquete en el servicio; un
     * Contrato inexistente produce 404 y una fecha invalida, 422.
     */
    @PostMapping("/{id}/extender-prueba")
    @PreAuthorize("@autorizador.tiene('suscripcion','actualizar')")
    public ResponseEntity<SuscripcionDto> extenderPrueba(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ExtenderPruebaRequest request) {
        return ResponseEntity.ok(servicioSuscripciones.extenderPrueba(id, request.nuevaVigenciaFin()));
    }

    /**
     * Convierte un Contrato de Suscripcion a un Contrato de Plan (Req 9.2): crea
     * un Contrato de Plan nuevo y cierra el anterior, preservando la
     * exclusividad del instrumento. Un Plan o Contrato inexistente produce 404;
     * una regla de negocio incumplida, 422.
     */
    @PostMapping("/{id}/convertir-a-plan")
    @PreAuthorize("@autorizador.tiene('suscripcion','crear')")
    public ResponseEntity<SuscripcionDto> convertirAPlan(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ConvertirAPlanRequest request) {
        return ResponseEntity.ok(servicioSuscripciones.convertirAPlan(id, request.planId()));
    }

    /**
     * Consulta una Suscripcion por su identificador (Req 25.2). Inexistente
     * produce 404.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('suscripcion','leer')")
    public ResponseEntity<SuscripcionDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioSuscripciones.consultar(id));
    }

    /**
     * Lista las Suscripciones de una Empresa (Req 25.2).
     *
     * @param tenantId Empresa (tenant) titular.
     * @return la lista de Suscripciones de la Empresa.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('suscripcion','listar')")
    public List<SuscripcionDto> listar(@RequestParam(name = "tenantId") UUID tenantId) {
        return servicioSuscripciones.listarPorEmpresa(tenantId);
    }
}
