package com.dessti.crm.social.adapter.in.rest;

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

import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;
import com.dessti.crm.social.application.CuentaCanalSocialDto;
import com.dessti.crm.social.application.ServicioCuentasCanalSocial;
import com.dessti.crm.social.domain.CanalSocial;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para la gestion de las {@link CuentaCanalSocialDto
 * Cuentas de Canal_Social} (Req 64.1, 64.2, 11, 12; tarea 40.2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /social/cuentas-canal} — registrar
 *       ({@code @autorizador.tiene('cuenta_canal_social','crear')}); 201 con el id.
 *       409 si ya existe la cuenta para el canal e identificador.</li>
 *   <li>{@code GET /social/cuentas-canal/{id}} — consulta
 *       ({@code @autorizador.tiene('cuenta_canal_social','leer')}); 200; 404 si no
 *       es accesible (Req 23.3).</li>
 *   <li>{@code GET /social/cuentas-canal?canal=&page=&size=} — listado paginado
 *       ({@code @autorizador.tiene('cuenta_canal_social','listar')}); 200 con
 *       {@link PaginaResponse}.</li>
 * </ul>
 *
 * <p>Los permisos {@code cuenta_canal_social:{crear,leer,listar}} ya se sembraron en
 * V5 (roles {@code marketing} y {@code ventas}). Se reciben y devuelven DTOs
 * distintos de las entidades; el DTO <strong>no expone credenciales</strong>, solo
 * su referencia (Req 11).</p>
 */
@RestController
@RequestMapping("/social/cuentas-canal")
public class CuentasCanalSocialController {

    private final ServicioCuentasCanalSocial servicioCuentas;

    public CuentasCanalSocialController(ServicioCuentasCanalSocial servicioCuentas) {
        this.servicioCuentas = servicioCuentas;
    }

    /**
     * Registra una Cuenta_Canal_Social (Req 64.1). 201 con el id.
     *
     * @param request cuerpo con canal, identificador, nombre y referencia de credenciales.
     * @return 201 Created con el {@link CuentaCanalSocialDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('cuenta_canal_social','crear')")
    public ResponseEntity<CuentaCanalSocialDto> crear(
            @Valid @RequestBody CrearCuentaCanalSocialRequest request) {
        CanalSocial canal = ParseoSocial.canalRequerido(request.canal());
        CuentaCanalSocialDto dto = servicioCuentas.crear(
                canal, request.identificadorExterno(), request.nombre(), request.credencialesRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Cuenta_Canal_Social por su identificador (Req 23.3).
     *
     * @param id identificador de la cuenta.
     * @return 200 OK con el {@link CuentaCanalSocialDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('cuenta_canal_social','leer')")
    public ResponseEntity<CuentaCanalSocialDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCuentas.consultar(id));
    }

    /**
     * Lista las Cuentas de Canal_Social del tenant de forma paginada (20/100) con
     * filtro opcional por canal (Req 64.1).
     *
     * @param canal etiqueta del canal a filtrar; opcional.
     * @param page  numero de pagina 0-index; opcional.
     * @param size  tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link CuentaCanalSocialDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('cuenta_canal_social','listar')")
    public PaginaResponse<CuentaCanalSocialDto> listar(
            @RequestParam(name = "canal", required = false) String canal,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioCuentas.listar(ParseoSocial.canalOpcional(canal), pageable));
    }
}
