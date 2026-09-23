package com.dessti.crm.contabilidad.cxp.adapter.in.rest;

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

import com.dessti.crm.contabilidad.cxp.application.CrearProgramacionPagoCommand;
import com.dessti.crm.contabilidad.cxp.application.ProgramacionPagoDto;
import com.dessti.crm.contabilidad.cxp.application.ServicioCuentasPorPagar;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo contabilidad-finanzas para las
 * {@link ProgramacionPagoDto Programaciones de Pago} (Req 42.2, 42.6, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /contabilidad/programaciones-pago} — crear
 *       ({@code @autorizador.tiene('programacion_pago','crear')}); 201 Created
 *       (Req 42.2).</li>
 *   <li>{@code GET /contabilidad/programaciones-pago?cuentaPorPagarId=&page=&size=} —
 *       listado paginado ({@code @autorizador.tiene('programacion_pago','listar')});
 *       200 OK con {@link PaginaResponse} (Req 42.6).</li>
 *   <li>{@code GET /contabilidad/programaciones-pago/{id}} — consultar
 *       ({@code @autorizador.tiene('programacion_pago','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.11)</h2>
 * <p>{@code programacion_pago:{crear,leer}} se sembraron en V5. El listado usa
 * {@code programacion_pago:listar}, que V5 no sembro; la migracion V33 lo siembra y
 * lo asigna al rol {@code contabilidad} (a0000000-0000-0000-0000-00000000000b).</p>
 */
@RestController
@RequestMapping("/contabilidad/programaciones-pago")
public class ProgramacionPagoController {

    private final ServicioCuentasPorPagar servicioCuentasPorPagar;

    public ProgramacionPagoController(ServicioCuentasPorPagar servicioCuentasPorPagar) {
        this.servicioCuentasPorPagar = servicioCuentasPorPagar;
    }

    /**
     * Crea una Programacion_Pago (fecha + monto) para una Cuenta_Por_Pagar (Req 42.2).
     *
     * @param request datos de la programacion.
     * @return 201 Created con el {@link ProgramacionPagoDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('programacion_pago','crear')")
    public ResponseEntity<ProgramacionPagoDto> crear(
            @Valid @RequestBody CrearProgramacionPagoRequest request) {
        CrearProgramacionPagoCommand comando = new CrearProgramacionPagoCommand(
                request.cuentaPorPagarId(), request.fechaProgramada(), request.monto());
        ProgramacionPagoDto dto = servicioCuentasPorPagar.crearProgramacionPago(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista las Programaciones de Pago del tenant de forma paginada (20 por defecto,
     * 100 maximo) con filtro opcional por Cuenta_Por_Pagar (Req 42.6).
     *
     * @param cuentaPorPagarId Cuenta_Por_Pagar a filtrar; opcional.
     * @param page             numero de pagina 0-index; opcional.
     * @param size             tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ProgramacionPagoDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('programacion_pago','listar')")
    public PaginaResponse<ProgramacionPagoDto> listar(
            @RequestParam(name = "cuentaPorPagarId", required = false) UUID cuentaPorPagarId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(
                servicioCuentasPorPagar.listarProgramaciones(cuentaPorPagarId, pageable));
    }

    /**
     * Consulta una Programacion_Pago por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la Programacion_Pago.
     * @return 200 OK con el {@link ProgramacionPagoDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('programacion_pago','leer')")
    public ResponseEntity<ProgramacionPagoDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCuentasPorPagar.consultarProgramacion(id));
    }
}
