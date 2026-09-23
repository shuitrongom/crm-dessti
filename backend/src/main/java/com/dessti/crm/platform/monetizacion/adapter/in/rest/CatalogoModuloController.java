package com.dessti.crm.platform.monetizacion.adapter.in.rest;

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

import com.dessti.crm.platform.monetizacion.application.CatalogoModuloDto;
import com.dessti.crm.platform.monetizacion.application.ServicioCatalogoModulos;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador REST de plataforma para el catalogo de modulos facturables (Req 24.3).
 * Rutas guardadas por permisos {@code modulo_catalogo:*} del {@code super_admin} (V22).
 */
@RestController
@RequestMapping("/modulos-catalogo")
public class CatalogoModuloController {

    private final ServicioCatalogoModulos servicioCatalogo;

    public CatalogoModuloController(ServicioCatalogoModulos servicioCatalogo) {
        this.servicioCatalogo = servicioCatalogo;
    }

    @PostMapping
    @PreAuthorize("@autorizador.tiene('modulo_catalogo','crear')")
    public ResponseEntity<CatalogoModuloDto> crear(@Valid @RequestBody CrearModuloCatalogoRequest request) {
        CatalogoModuloDto dto = servicioCatalogo.crear(request.clave(), request.nombre(), request.descripcion());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('modulo_catalogo','leer')")
    public ResponseEntity<CatalogoModuloDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCatalogo.consultar(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('modulo_catalogo','actualizar')")
    public ResponseEntity<CatalogoModuloDto> actualizar(@PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarModuloCatalogoRequest request) {
        return ResponseEntity.ok(servicioCatalogo.actualizar(id, request.nombre(), request.descripcion()));
    }

    @PostMapping("/{id}/desactivar")
    @PreAuthorize("@autorizador.tiene('modulo_catalogo','actualizar')")
    public ResponseEntity<CatalogoModuloDto> desactivar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCatalogo.desactivar(id));
    }

    @GetMapping
    @PreAuthorize("@autorizador.tiene('modulo_catalogo','listar')")
    public PaginaResponse<CatalogoModuloDto> listar(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioCatalogo.listar(pageable));
    }
}