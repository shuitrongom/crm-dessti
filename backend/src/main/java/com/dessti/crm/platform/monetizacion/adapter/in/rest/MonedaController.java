package com.dessti.crm.platform.monetizacion.adapter.in.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.monetizacion.application.MonedaDto;
import com.dessti.crm.platform.monetizacion.application.ServicioMonedas;

import jakarta.validation.Valid;

/**
 * Adaptador REST de plataforma para el catalogo de monedas (Req 24.3). Todas las
 * rutas exigen permisos de plataforma {@code moneda:*}, reservados al
 * {@code super_admin} (V22).
 */
@RestController
@RequestMapping("/monedas")
public class MonedaController {

    private final ServicioMonedas servicioMonedas;

    public MonedaController(ServicioMonedas servicioMonedas) {
        this.servicioMonedas = servicioMonedas;
    }

    @PostMapping
    @PreAuthorize("@autorizador.tiene('moneda','crear')")
    public ResponseEntity<MonedaDto> crear(@Valid @RequestBody CrearMonedaRequest request) {
        MonedaDto dto = servicioMonedas.crear(request.codigo(), request.nombre());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    @PreAuthorize("@autorizador.tiene('moneda','listar')")
    public List<MonedaDto> listar() {
        return servicioMonedas.listarActivas();
    }

    @PostMapping("/{codigo}/activar")
    @PreAuthorize("@autorizador.tiene('moneda','crear')")
    public ResponseEntity<MonedaDto> activar(@PathVariable("codigo") String codigo) {
        return ResponseEntity.ok(servicioMonedas.activar(codigo));
    }

    @PostMapping("/{codigo}/desactivar")
    @PreAuthorize("@autorizador.tiene('moneda','crear')")
    public ResponseEntity<MonedaDto> desactivar(@PathVariable("codigo") String codigo) {
        return ResponseEntity.ok(servicioMonedas.desactivar(codigo));
    }
}