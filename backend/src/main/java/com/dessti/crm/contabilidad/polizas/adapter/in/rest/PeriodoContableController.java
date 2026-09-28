package com.dessti.crm.contabilidad.polizas.adapter.in.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.contabilidad.polizas.application.PeriodoContableDto;
import com.dessti.crm.contabilidad.polizas.application.ServicioCierrePeriodo;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del cierre de periodo contable (candado contable) del
 * modulo contabilidad-finanzas.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /contabilidad/periodos?anio=} — lista los 12 meses del anio con su
 *       estado ({@code @autorizador.tiene('periodo_contable','leer')}); 200 OK. Los
 *       meses no cerrados se devuelven como abiertos.</li>
 *   <li>{@code POST /contabilidad/periodos/cerrar} — cierra un periodo
 *       ({@code @autorizador.tiene('periodo_contable','cerrar')}); 200 OK. 422 si la
 *       balanza del periodo no cuadra; 409 si ya estaba cerrado.</li>
 *   <li>{@code POST /contabilidad/periodos/reabrir} — reabre un periodo cerrado con
 *       motivo ({@code @autorizador.tiene('periodo_contable','reabrir')}); 200 OK.
 *       422 si el motivo es vacio; 409 si no estaba cerrado.</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.11)</h2>
 * <p>Todos los endpoints aplican el doble gating
 * {@code @autorizador.moduloHabilitado('contabilidad') and
 * @autorizador.tiene('periodo_contable', <operacion>)}. Los permisos se sembraron en
 * V72 y se enlazaron al rol predefinido {@code contabilidad}.</p>
 */
@RestController
@RequestMapping("/contabilidad/periodos")
public class PeriodoContableController {

    private final ServicioCierrePeriodo servicioCierrePeriodo;

    public PeriodoContableController(ServicioCierrePeriodo servicioCierrePeriodo) {
        this.servicioCierrePeriodo = servicioCierrePeriodo;
    }

    /**
     * Lista los 12 meses del anio indicado con su estado de cierre. Los meses sin
     * registro explicito se devuelven como abiertos por defecto.
     *
     * @param anio anio a consultar (2000..2100).
     * @return 200 OK con la lista ordenada por mes de {@link PeriodoContableDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('periodo_contable','leer')")
    public ResponseEntity<List<PeriodoContableDto>> listar(@RequestParam("anio") int anio) {
        return ResponseEntity.ok(servicioCierrePeriodo.consultarAnio(anio));
    }

    /**
     * Cierra un periodo mensual. Valida primero que la balanza del periodo cuadre;
     * si no cuadra, responde 422 informando la diferencia. 409 si ya estaba cerrado.
     *
     * @param request anio y mes a cerrar.
     * @return 200 OK con el {@link PeriodoContableDto} del periodo cerrado.
     */
    @PostMapping("/cerrar")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('periodo_contable','cerrar')")
    public ResponseEntity<PeriodoContableDto> cerrar(@Valid @RequestBody CerrarPeriodoRequest request) {
        return ResponseEntity.ok(
                servicioCierrePeriodo.cerrarPeriodo(request.anio(), request.mes()));
    }

    /**
     * Reabre un periodo mensual cerrado indicando un motivo obligatorio (auditado).
     * 422 si el motivo es vacio; 409 si el periodo no estaba cerrado.
     *
     * @param request anio, mes y motivo de la reapertura.
     * @return 200 OK con el {@link PeriodoContableDto} del periodo reabierto.
     */
    @PostMapping("/reabrir")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('periodo_contable','reabrir')")
    public ResponseEntity<PeriodoContableDto> reabrir(@Valid @RequestBody ReabrirPeriodoRequest request) {
        return ResponseEntity.ok(
                servicioCierrePeriodo.reabrirPeriodo(request.anio(), request.mes(), request.motivo()));
    }
}
