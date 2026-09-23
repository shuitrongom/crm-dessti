package com.dessti.crm.platform.monetizacion.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.monetizacion.application.EmpresaModuloPrecioDto;
import com.dessti.crm.platform.monetizacion.application.PrecioModuloDto;
import com.dessti.crm.platform.monetizacion.application.ServicioPreciosModulo;

import jakarta.validation.Valid;

/**
 * Adaptador REST de plataforma para los precios de los modulos (Req 24.3):
 * precio de lista por moneda y precio especial negociado por Empresa. Rutas
 * guardadas por permisos {@code precio_modulo:*} del {@code super_admin} (V22).
 */
@RestController
@RequestMapping("/precios-modulo")
public class PrecioModuloController {

    private final ServicioPreciosModulo servicioPrecios;

    public PrecioModuloController(ServicioPreciosModulo servicioPrecios) {
        this.servicioPrecios = servicioPrecios;
    }

    /** Define/actualiza el precio de LISTA de un modulo en una moneda (upsert). */
    @PutMapping("/modulos/{moduloId}")
    @PreAuthorize("@autorizador.tiene('precio_modulo','actualizar')")
    public ResponseEntity<PrecioModuloDto> definirPrecioLista(@PathVariable("moduloId") UUID moduloId,
            @Valid @RequestBody DefinirPrecioModuloRequest request) {
        return ResponseEntity.ok(
                servicioPrecios.definirPrecioLista(moduloId, request.monedaCodigo(), request.precio()));
    }

    /** Lista los precios de lista definidos para un modulo. */
    @GetMapping("/modulos/{moduloId}")
    @PreAuthorize("@autorizador.tiene('precio_modulo','listar')")
    public List<PrecioModuloDto> listarPreciosLista(@PathVariable("moduloId") UUID moduloId) {
        return servicioPrecios.listarPreciosLista(moduloId);
    }

    /** Define/actualiza el precio ESPECIAL de una Empresa para un modulo en una moneda. */
    @PutMapping("/empresas/{tenantId}/modulos/{moduloId}")
    @PreAuthorize("@autorizador.tiene('precio_modulo','actualizar')")
    public ResponseEntity<EmpresaModuloPrecioDto> definirPrecioEspecial(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("moduloId") UUID moduloId,
            @Valid @RequestBody DefinirPrecioModuloRequest request) {
        return ResponseEntity.ok(servicioPrecios.definirPrecioEspecial(
                tenantId, moduloId, request.monedaCodigo(), request.precio()));
    }

    /** Lista los precios especiales negociados por una Empresa. */
    @GetMapping("/empresas/{tenantId}")
    @PreAuthorize("@autorizador.tiene('precio_modulo','listar')")
    public List<EmpresaModuloPrecioDto> listarPreciosEspeciales(@PathVariable("tenantId") UUID tenantId) {
        return servicioPrecios.listarPreciosEspeciales(tenantId);
    }
}