package com.dessti.crm.tesoreria.adapter.in.rest;

import java.time.Instant;
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

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;
import com.dessti.crm.tesoreria.application.ConciliacionBancariaDto;
import com.dessti.crm.tesoreria.application.CrearCuentaBancariaCommand;
import com.dessti.crm.tesoreria.application.CuentaBancariaDto;
import com.dessti.crm.tesoreria.application.EstadoCuentaBancarioDto;
import com.dessti.crm.tesoreria.application.ImportarEstadoCuentaCommand;
import com.dessti.crm.tesoreria.application.MovimientoBancarioDto;
import com.dessti.crm.tesoreria.application.MovimientoImportado;
import com.dessti.crm.tesoreria.application.ServicioTesoreria;
import com.dessti.crm.tesoreria.domain.EstadoConciliacionBancaria;
import com.dessti.crm.tesoreria.domain.EstadoConciliacionMovimiento;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo de tesoreria para cuentas bancarias, estados
 * de cuenta y conciliacion bancaria (Req 43, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /tesoreria/cuentas-bancarias} — alta
 *       ({@code cuenta_bancaria:crear}); 201 Created (Req 43.1).</li>
 *   <li>{@code GET /tesoreria/cuentas-bancarias/{id}} — consulta
 *       ({@code cuenta_bancaria:leer}); 200 OK; 404 si no es accesible (Req 23.3).</li>
 *   <li>{@code GET /tesoreria/cuentas-bancarias} — listado paginado
 *       ({@code cuenta_bancaria:listar}); 200 OK (Req 43.1).</li>
 *   <li>{@code POST /tesoreria/cuentas-bancarias/{id}/estados-cuenta} — importar
 *       estado de cuenta ({@code conciliacion_bancaria:crear}); 201 Created
 *       (Req 43.2).</li>
 *   <li>{@code POST /tesoreria/estados-cuenta/{id}/conciliacion} — conciliar
 *       ({@code conciliacion_bancaria:crear}); 201 Created (Req 43.3, 43.4, 43.5).</li>
 *   <li>{@code GET /tesoreria/movimientos-bancarios} — listado paginado
 *       ({@code movimiento_bancario:leer}); 200 OK, filtrable por Cuenta_Bancaria,
 *       periodo y estado de conciliacion (Req 43.6).</li>
 *   <li>{@code GET /tesoreria/conciliaciones} — listado paginado
 *       ({@code conciliacion_bancaria:leer}); 200 OK, filtrable por Cuenta_Bancaria,
 *       periodo y estado (Req 43.6).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.11)</h2>
 * <p>Los permisos {@code cuenta_bancaria:{crear,leer,listar}} y
 * {@code conciliacion_bancaria:{crear,leer}} se sembraron en V5 y se asignaron al rol
 * {@code contabilidad}; se reutilizan aqui. La importacion de estado de cuenta
 * reutiliza {@code conciliacion_bancaria:crear} (operacion de tesoreria previa a la
 * conciliacion). El listado de movimientos usa {@code movimiento_bancario:leer} y el
 * de conciliaciones {@code conciliacion_bancaria:leer}; los permisos que V5 no sembro
 * ({@code movimiento_bancario:leer}, {@code conciliacion_bancaria:listar}) se
 * completan en V35 y se enlazan al rol {@code contabilidad}.</p>
 */
@RestController
@RequestMapping("/tesoreria")
public class TesoreriaController {

    private final ServicioTesoreria servicioTesoreria;

    public TesoreriaController(ServicioTesoreria servicioTesoreria) {
        this.servicioTesoreria = servicioTesoreria;
    }

    // ------------------------------------------------------------------
    // Cuenta_Bancaria (Req 43.1)
    // ------------------------------------------------------------------

    /**
     * Da de alta una Cuenta_Bancaria (Req 43.1).
     *
     * @param request datos de la cuenta.
     * @return 201 Created con el {@link CuentaBancariaDto} creado.
     */
    @PostMapping("/cuentas-bancarias")
    @PreAuthorize("@autorizador.moduloHabilitado('tesoreria') and @autorizador.tiene('cuenta_bancaria','crear')")
    public ResponseEntity<CuentaBancariaDto> crearCuenta(
            @Valid @RequestBody CrearCuentaBancariaRequest request) {
        CrearCuentaBancariaCommand comando = new CrearCuentaBancariaCommand(
                request.nombre(), request.banco(), request.clabe(), request.moneda());
        CuentaBancariaDto dto = servicioTesoreria.crearCuentaBancaria(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Cuenta_Bancaria por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la cuenta.
     * @return 200 OK con el {@link CuentaBancariaDto}.
     */
    @GetMapping("/cuentas-bancarias/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('tesoreria') and @autorizador.tiene('cuenta_bancaria','leer')")
    public ResponseEntity<CuentaBancariaDto> consultarCuenta(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioTesoreria.consultarCuenta(id));
    }

    /**
     * Lista las Cuentas_Bancarias del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtro opcional por bandera de actividad (Req 43.1).
     *
     * @param activa filtro de actividad; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link CuentaBancariaDto}.
     */
    @GetMapping("/cuentas-bancarias")
    @PreAuthorize("@autorizador.moduloHabilitado('tesoreria') and @autorizador.tiene('cuenta_bancaria','listar')")
    public PaginaResponse<CuentaBancariaDto> listarCuentas(
            @RequestParam(name = "activa", required = false) Boolean activa,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioTesoreria.listarCuentas(activa, pageable));
    }

    // ------------------------------------------------------------------
    // Estado_Cuenta_Bancario y conciliacion (Req 43.2 - 43.5)
    // ------------------------------------------------------------------

    /**
     * Importa un Estado_Cuenta_Bancario para una Cuenta_Bancaria (Req 43.2).
     *
     * @param id      identificador de la Cuenta_Bancaria destino.
     * @param request datos de la importacion (periodo, referencia y movimientos).
     * @return 201 Created con el {@link EstadoCuentaBancarioDto} importado.
     */
    @PostMapping("/cuentas-bancarias/{id}/estados-cuenta")
    @PreAuthorize("@autorizador.moduloHabilitado('tesoreria') and @autorizador.tiene('conciliacion_bancaria','crear')")
    public ResponseEntity<EstadoCuentaBancarioDto> importarEstadoCuenta(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ImportarEstadoCuentaRequest request) {
        ImportarEstadoCuentaCommand comando = new ImportarEstadoCuentaCommand(
                request.referenciaArchivo(),
                request.periodoInicio(),
                request.periodoFin(),
                request.movimientos() == null ? null : request.movimientos().stream()
                        .map(linea -> new MovimientoImportado(
                                linea.fecha(), linea.monto(), linea.referencia(),
                                linea.descripcion()))
                        .toList());
        EstadoCuentaBancarioDto dto = servicioTesoreria.importarEstadoCuenta(id, comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Concilia un Estado_Cuenta_Bancario (Req 43.3, 43.4, 43.5). Empareja cada
     * movimiento con una Poliza_Contable/Pago dentro de tolerancia; los no
     * emparejados quedan en excepcion. La conciliacion queda {@code completa} solo si
     * la diferencia es cero y no hay excepciones (Property 18).
     *
     * @param id identificador del Estado_Cuenta_Bancario a conciliar.
     * @return 201 Created con la {@link ConciliacionBancariaDto} resultante.
     */
    @PostMapping("/estados-cuenta/{id}/conciliacion")
    @PreAuthorize("@autorizador.moduloHabilitado('tesoreria') and @autorizador.tiene('conciliacion_bancaria','crear')")
    public ResponseEntity<ConciliacionBancariaDto> conciliar(@PathVariable("id") UUID id) {
        ConciliacionBancariaDto dto = servicioTesoreria.conciliar(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista los Movimiento_Bancario del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por Cuenta_Bancaria, periodo y estado de
     * conciliacion (Req 43.6).
     *
     * @param cuentaBancariaId   Cuenta_Bancaria a filtrar; opcional.
     * @param desde              fecha minima (inclusiva); opcional.
     * @param hasta              fecha maxima (inclusiva); opcional.
     * @param estadoConciliacion estado de conciliacion; opcional.
     * @param page               numero de pagina 0-index; opcional.
     * @param size               tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link MovimientoBancarioDto}.
     */
    @GetMapping("/movimientos-bancarios")
    @PreAuthorize("@autorizador.moduloHabilitado('tesoreria') and @autorizador.tiene('movimiento_bancario','leer')")
    public PaginaResponse<MovimientoBancarioDto> listarMovimientos(
            @RequestParam(name = "cuentaBancariaId", required = false) UUID cuentaBancariaId,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "estadoConciliacion", required = false) String estadoConciliacion,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        EstadoConciliacionMovimiento estado = interpretarEstadoMovimiento(estadoConciliacion);
        return PaginaResponse.de(servicioTesoreria.listarMovimientos(
                cuentaBancariaId, desde, hasta, estado, pageable));
    }

    /**
     * Lista las Conciliacion_Bancaria del tenant de forma paginada (20 por defecto,
     * 100 maximo) con filtros opcionales por Cuenta_Bancaria, periodo y estado
     * (Req 43.6).
     *
     * @param cuentaBancariaId Cuenta_Bancaria a filtrar; opcional.
     * @param desde            instante minimo (inclusivo); opcional.
     * @param hasta            instante maximo (inclusivo); opcional.
     * @param estado           estado (en_proceso/completa); opcional.
     * @param page             numero de pagina 0-index; opcional.
     * @param size             tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ConciliacionBancariaDto}.
     */
    @GetMapping("/conciliaciones")
    @PreAuthorize("@autorizador.moduloHabilitado('tesoreria') and @autorizador.tiene('conciliacion_bancaria','leer')")
    public PaginaResponse<ConciliacionBancariaDto> listarConciliaciones(
            @RequestParam(name = "cuentaBancariaId", required = false) UUID cuentaBancariaId,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        EstadoConciliacionBancaria estadoInterpretado = interpretarEstadoConciliacion(estado);
        return PaginaResponse.de(servicioTesoreria.listarConciliaciones(
                cuentaBancariaId, desde, hasta, estadoInterpretado, pageable));
    }

    private static EstadoConciliacionMovimiento interpretarEstadoMovimiento(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return EstadoConciliacionMovimiento.desdeValorBd(valor);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException(
                    "Estado de conciliacion del Movimiento_Bancario desconocido: " + valor);
        }
    }

    private static EstadoConciliacionBancaria interpretarEstadoConciliacion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return EstadoConciliacionBancaria.desdeValorBd(valor);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException(
                    "Estado de la Conciliacion_Bancaria desconocido: " + valor);
        }
    }
}
