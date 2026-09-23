package com.dessti.crm.contabilidad.polizas.adapter.in.rest;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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

import com.dessti.crm.contabilidad.polizas.application.PolizaContableDto;
import com.dessti.crm.contabilidad.polizas.application.RegistrarPolizaCommand;
import com.dessti.crm.contabilidad.polizas.application.RenglonPolizaCommand;
import com.dessti.crm.contabilidad.polizas.application.ServicioContabilidad;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo contabilidad-finanzas para las
 * {@link PolizaContableDto Polizas_Contables} balanceadas (Req 38.2 - 38.6, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /contabilidad/polizas} — registrar
 *       ({@code @autorizador.tiene('poliza_contable','crear')}); 201 Created. 422 si
 *       la poliza no esta balanceada, informando la diferencia (Req 38.4).</li>
 *   <li>{@code POST /contabilidad/polizas/{id}/reverso} — reversar
 *       ({@code @autorizador.tiene('poliza_contable','crear')}); 201 Created con la
 *       poliza de reverso (Req 38.5).</li>
 *   <li>{@code GET /contabilidad/polizas?desde=&hasta=&cuentaContableId=&page=&size=}
 *       — listado paginado ({@code @autorizador.tiene('poliza_contable','listar')});
 *       200 OK con {@link PaginaResponse}, filtrable por rango de fechas y por
 *       Cuenta_Contable (Req 38.6).</li>
 *   <li>{@code GET /contabilidad/polizas/{id}} — consultar
 *       ({@code @autorizador.tiene('poliza_contable','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.11)</h2>
 * <p>Los permisos {@code poliza_contable:{crear,leer,listar}} ya se sembraron en V5 y
 * se asignaron al rol {@code contabilidad}; se reutilizan aqui sin re-sembrarlos. El
 * reverso reutiliza {@code poliza_contable:crear} por generar una nueva poliza.</p>
 */
@RestController
@RequestMapping("/contabilidad/polizas")
public class PolizaContableController {

    private final ServicioContabilidad servicioContabilidad;

    public PolizaContableController(ServicioContabilidad servicioContabilidad) {
        this.servicioContabilidad = servicioContabilidad;
    }

    /**
     * Registra una Poliza_Contable balanceada (Req 38.2, 38.3, 38.4). 422 si la
     * poliza no esta balanceada, informando la diferencia entre cargos y abonos.
     *
     * @param request datos de la poliza y sus renglones.
     * @return 201 Created con el {@link PolizaContableDto} registrado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('poliza_contable','crear')")
    public ResponseEntity<PolizaContableDto> registrar(
            @Valid @RequestBody RegistrarPolizaRequest request) {
        RegistrarPolizaCommand comando = new RegistrarPolizaCommand(
                request.fecha(),
                request.tipoInterpretado(),
                request.concepto(),
                request.origen(),
                request.origenId(),
                request.renglones().stream()
                        .map(linea -> new RenglonPolizaCommand(
                                linea.cuentaContableId(), linea.cargo(), linea.abono()))
                        .toList());
        PolizaContableDto dto = servicioContabilidad.registrarPoliza(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Crea la poliza de reverso de una poliza existente (Req 38.5), preservando la
     * inmutabilidad contable (la poliza original no se modifica ni se borra).
     *
     * @param id      identificador de la poliza a reversar.
     * @param request cuerpo opcional con la fecha del reverso; puede ser {@code null}.
     * @return 201 Created con el {@link PolizaContableDto} de reverso.
     */
    @PostMapping("/{id}/reverso")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('poliza_contable','crear')")
    public ResponseEntity<PolizaContableDto> reversar(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ReversarPolizaRequest request) {
        LocalDate fecha = (request == null) ? null : request.fecha();
        PolizaContableDto dto = servicioContabilidad.reversarPoliza(id, fecha);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista las Polizas_Contables del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por rango de fechas y por Cuenta_Contable
     * (Req 38.6).
     *
     * @param desde            fecha minima (inclusiva); opcional.
     * @param hasta            fecha maxima (inclusiva); opcional.
     * @param cuentaContableId Cuenta_Contable a filtrar; opcional.
     * @param page             numero de pagina 0-index; opcional.
     * @param size             tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link PolizaContableDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('poliza_contable','listar')")
    public PaginaResponse<PolizaContableDto> listar(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "cuentaContableId", required = false) UUID cuentaContableId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(
                servicioContabilidad.listarPolizas(desde, hasta, cuentaContableId, pageable));
    }

    /**
     * Consulta una Poliza_Contable por su identificador con su desglose (Req 23.3).
     * 404 si no es accesible.
     *
     * @param id identificador de la poliza.
     * @return 200 OK con el {@link PolizaContableDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('poliza_contable','leer')")
    public ResponseEntity<PolizaContableDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioContabilidad.consultarPoliza(id));
    }
}
