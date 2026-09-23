package com.dessti.crm.platform.empresas.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.empresas.ActualizarPaqueteSuscripcionCommand;
import com.dessti.crm.platform.empresas.CrearPaqueteSuscripcionCommand;
import com.dessti.crm.platform.empresas.PaqueteSuscripcionDto;
import com.dessti.crm.platform.empresas.ServicioPaquetesSuscripcion;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para la gestion de plataforma de
 * {@link PaqueteSuscripcionDto Paquetes de Suscripcion} por el
 * {@code super_admin} (Req 3.1, 10). Es el <strong>espejo</strong> de
 * {@link PlanController}: mientras aquel administra el catalogo de
 * <strong>contratos de largo plazo</strong> (Planes, mas de un año), este expone
 * el catalogo de <strong>contratos de corto plazo</strong> (Paquetes de
 * Suscripcion, un año o menos) con opcion de periodo de prueba.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /paquetes-suscripcion} — define un Paquete (Req 3.1, 10.1);
 *       201.</li>
 *   <li>{@code PUT /paquetes-suscripcion/{id}} — actualiza un Paquete
 *       (Req 3.1, 10.4).</li>
 *   <li>{@code GET /paquetes-suscripcion/{id}} — consulta un Paquete
 *       (Req 3.1, 10.3).</li>
 *   <li>{@code GET /paquetes-suscripcion} — lista paginada (20/100)
 *       (Req 3.1, 10.3).</li>
 *   <li>{@code DELETE /paquetes-suscripcion/{id}} — elimina un Paquete
 *       (Req 10.5); 204. Rechazado con 422 si algun Contrato (Suscripcion) lo
 *       referencia.</li>
 * </ul>
 *
 * <h2>Autorizacion de plataforma (D8)</h2>
 * <p>Cada operacion exige el permiso atomico de plataforma correspondiente sobre
 * el recurso {@code suscripcion}, sembrado en V5 y asignado
 * <strong>unicamente</strong> al rol {@code super_admin}; cualquier otro usuario
 * recibe 403 (denegacion por defecto, Req 3.2):</p>
 * <ul>
 *   <li>crear → {@code suscripcion:crear};</li>
 *   <li>actualizar → {@code suscripcion:actualizar};</li>
 *   <li>consultar → {@code suscripcion:leer};</li>
 *   <li>listar → {@code suscripcion:listar};</li>
 *   <li>eliminar → {@code suscripcion:cambiar_estado}. No existe un permiso
 *       propio de eliminacion para Suscripciones (D8 reutiliza los permisos
 *       {@code suscripcion:*} de V5 sin sembrar permisos nuevos); se reutiliza
 *       {@code suscripcion:cambiar_estado} por ser la operacion administrativa de
 *       mayor privilegio disponible sobre el recurso.</li>
 * </ul>
 */
@RestController
@RequestMapping("/paquetes-suscripcion")
public class PaqueteSuscripcionController {

    private final ServicioPaquetesSuscripcion servicioPaquetesSuscripcion;

    public PaqueteSuscripcionController(ServicioPaquetesSuscripcion servicioPaquetesSuscripcion) {
        this.servicioPaquetesSuscripcion = servicioPaquetesSuscripcion;
    }

    /**
     * Define un Paquete de Suscripcion (Req 3.1, 10.1). Un nombre duplicado
     * produce 409.
     *
     * @param request datos del Paquete a crear.
     * @return 201 Created con el Paquete definido.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('suscripcion','crear')")
    public ResponseEntity<PaqueteSuscripcionDto> crear(
            @Valid @RequestBody CrearPaqueteSuscripcionRequest request) {
        PaqueteSuscripcionDto dto = servicioPaquetesSuscripcion.crearPaquete(new CrearPaqueteSuscripcionCommand(
                request.nombre(), request.maxUsuarios(), request.duracionDias(),
                request.admitePrueba(), request.duracionPruebaMeses(), request.giroId(),
                request.monedaCodigo(), request.preciosModulos()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Actualiza un Paquete de Suscripcion (Req 3.1, 10.4). Un Paquete inexistente
     * produce 404; un nombre en conflicto con otro Paquete, 409.
     *
     * @param id      identificador del Paquete.
     * @param request nuevos datos del Paquete.
     * @return 200 OK con el Paquete actualizado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('suscripcion','actualizar')")
    public ResponseEntity<PaqueteSuscripcionDto> actualizar(@PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarPaqueteSuscripcionRequest request) {
        PaqueteSuscripcionDto dto = servicioPaquetesSuscripcion.actualizarPaquete(id,
                new ActualizarPaqueteSuscripcionCommand(
                        request.nombre(), request.maxUsuarios(), request.duracionDias(),
                        request.admitePrueba(), request.duracionPruebaMeses(), request.giroId(),
                        request.monedaCodigo(), request.preciosModulos()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Consulta un Paquete de Suscripcion por su identificador (Req 3.1, 10.3). Un
     * Paquete inexistente produce 404.
     *
     * @param id identificador del Paquete.
     * @return 200 OK con el Paquete.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('suscripcion','leer')")
    public ResponseEntity<PaqueteSuscripcionDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPaquetesSuscripcion.consultarPaquete(id));
    }

    /**
     * Lista Paquetes de Suscripcion de forma paginada (20 por defecto, 100
     * maximo) (Req 3.1, 10.3).
     *
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return la pagina de Paquetes proyectada a {@link PaqueteSuscripcionDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('suscripcion','listar')")
    public PaginaResponse<PaqueteSuscripcionDto> listar(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPaquetesSuscripcion.listarPaquetes(pageable), PaqueteSuscripcionDto::de);
    }

    /**
     * Elimina definitivamente un Paquete de Suscripcion (Req 10.5). Un Paquete
     * inexistente produce 404; un Paquete aun referenciado por un Contrato
     * (Suscripcion) produce 422 con el conteo y como resolverlo.
     *
     * @param id identificador del Paquete.
     * @return 204 No Content si la eliminacion se aplica.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('suscripcion','cambiar_estado')")
    public ResponseEntity<Void> eliminar(@PathVariable("id") UUID id) {
        servicioPaquetesSuscripcion.eliminarPaquete(id);
        return ResponseEntity.noContent().build();
    }
}
