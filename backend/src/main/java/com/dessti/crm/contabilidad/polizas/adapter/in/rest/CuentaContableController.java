package com.dessti.crm.contabilidad.polizas.adapter.in.rest;

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

import com.dessti.crm.contabilidad.polizas.application.CrearCuentaContableCommand;
import com.dessti.crm.contabilidad.polizas.application.CuentaContableDto;
import com.dessti.crm.contabilidad.polizas.application.ServicioContabilidad;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo contabilidad-finanzas para el catalogo de
 * {@link CuentaContableDto Cuentas_Contables} (Req 38.1, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /contabilidad/cuentas-contables} — crear
 *       ({@code @autorizador.tiene('cuenta_contable','crear')}); 201 Created. 409 si
 *       el codigo ya existe (Req 38.1).</li>
 *   <li>{@code GET /contabilidad/cuentas-contables?activa=&page=&size=} — listado
 *       paginado ({@code @autorizador.tiene('cuenta_contable','listar')}); 200 OK con
 *       {@link PaginaResponse}.</li>
 *   <li>{@code GET /contabilidad/cuentas-contables/{id}} — consultar
 *       ({@code @autorizador.tiene('cuenta_contable','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.11)</h2>
 * <p>Los permisos {@code cuenta_contable:{crear,leer,listar}} ya se sembraron en V5 y
 * se asignaron al rol {@code contabilidad}; se reutilizan aqui sin re-sembrarlos.</p>
 */
@RestController
@RequestMapping("/contabilidad/cuentas-contables")
public class CuentaContableController {

    private final ServicioContabilidad servicioContabilidad;

    public CuentaContableController(ServicioContabilidad servicioContabilidad) {
        this.servicioContabilidad = servicioContabilidad;
    }

    /**
     * Crea una Cuenta_Contable en el catalogo del tenant (Req 38.1). 409 si el codigo
     * ya existe en la Empresa.
     *
     * @param request datos de la cuenta a crear.
     * @return 201 Created con el {@link CuentaContableDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_contable','crear')")
    public ResponseEntity<CuentaContableDto> crear(
            @Valid @RequestBody CrearCuentaContableRequest request) {
        CrearCuentaContableCommand comando = new CrearCuentaContableCommand(
                request.codigo(), request.nombre(),
                request.tipoInterpretado(), request.naturalezaInterpretada());
        CuentaContableDto dto = servicioContabilidad.crearCuenta(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista las Cuentas_Contables del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtro opcional por bandera de actividad (Req 38.1).
     *
     * @param activa filtro de actividad; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link CuentaContableDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_contable','listar')")
    public PaginaResponse<CuentaContableDto> listar(
            @RequestParam(name = "activa", required = false) Boolean activa,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioContabilidad.listarCuentas(activa, pageable));
    }

    /**
     * Consulta una Cuenta_Contable por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la cuenta.
     * @return 200 OK con el {@link CuentaContableDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('cuenta_contable','leer')")
    public ResponseEntity<CuentaContableDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioContabilidad.consultarCuenta(id));
    }
}
