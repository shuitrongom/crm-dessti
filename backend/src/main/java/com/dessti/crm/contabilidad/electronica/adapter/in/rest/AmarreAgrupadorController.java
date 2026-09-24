package com.dessti.crm.contabilidad.electronica.adapter.in.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.contabilidad.electronica.application.ServicioAmarreAgrupador;
import com.dessti.crm.contabilidad.polizas.application.CuentaContableDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Adaptador de entrada REST para el <strong>amarre</strong> de una Cuenta_Contable
 * a un codigo agrupador del SAT (Anexo 24), parte de la Contabilidad Electronica
 * (Req 1).
 *
 * <p>Ruta (relativa al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code PATCH /contabilidad/cuentas-contables/{id}/codigo-agrupador} —
 *       amarra la cuenta al codigo agrupador
 *       ({@code cuenta_contable:actualizar}); 200 OK con la cuenta actualizada.
 *       422 si el codigo no existe en el catalogo del SAT; 404 si la cuenta no es
 *       accesible.</li>
 * </ul>
 *
 * <p>El permiso {@code cuenta_contable:actualizar} y el enlace al rol
 * {@code contabilidad} se sembraron en V71.</p>
 */
@RestController
@RequestMapping("/contabilidad/cuentas-contables")
public class AmarreAgrupadorController {

    private final ServicioAmarreAgrupador servicioAmarreAgrupador;

    public AmarreAgrupadorController(ServicioAmarreAgrupador servicioAmarreAgrupador) {
        this.servicioAmarreAgrupador = servicioAmarreAgrupador;
    }

    /**
     * Amarra una Cuenta_Contable a un codigo agrupador del SAT (Req 1.3, 1.7).
     *
     * @param id      identificador de la Cuenta_Contable.
     * @param request cuerpo con el codigo agrupador a amarrar.
     * @return 200 OK con el {@link CuentaContableDto} actualizado.
     */
    @PatchMapping("/{id}/codigo-agrupador")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_contable','actualizar')")
    public ResponseEntity<CuentaContableDto> amarrar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AmarrarAgrupadorRequest request) {
        return ResponseEntity.ok(
                servicioAmarreAgrupador.amarrarCodigoAgrupador(id, request.codigoAgrupadorSat()));
    }

    /**
     * Cuerpo de la peticion de amarre.
     *
     * @param codigoAgrupadorSat codigo agrupador del SAT a amarrar; obligatorio.
     */
    public record AmarrarAgrupadorRequest(
            @NotBlank(message = "El codigo agrupador del SAT es obligatorio.")
            String codigoAgrupadorSat) {
    }
}
