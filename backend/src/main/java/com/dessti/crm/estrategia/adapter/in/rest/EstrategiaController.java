package com.dessti.crm.estrategia.adapter.in.rest;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.estrategia.application.EsenciaEmpresaDto;
import com.dessti.crm.estrategia.application.ObjetivoEstrategicoDto;
import com.dessti.crm.estrategia.application.ServicioEstrategia;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo estrategia para la planeacion estrategica:
 * la {@link EsenciaEmpresaDto Esencia_Empresa} (mision/vision/valores) y los
 * {@link ObjetivoEstrategicoDto Objetivos_Estrategicos} con sus resultados clave
 * ponderados (Req 58; tarea 37.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code PUT /estrategia/esencia} â€” registrar/actualizar mision/vision/valores
 *       ({@code @autorizador.tiene('planeacion_estrategica','actualizar')}); 200 OK
 *       (Req 58.1).</li>
 *   <li>{@code GET /estrategia/esencia} â€” consultar la esencia
 *       ({@code @autorizador.tiene('planeacion_estrategica','leer')}); 200 OK; 404
 *       si aun no existe (Req 58.1).</li>
 *   <li>{@code POST /estrategia/objetivos} â€” crear objetivo
 *       ({@code @autorizador.tiene('objetivo_estrategico','crear')}); 201 Created;
 *       422 si falta un campo obligatorio (Req 58.2, 58.3).</li>
 *   <li>{@code GET /estrategia/objetivos/{id}} â€” consultar objetivo con avance y
 *       estado derivado ({@code @autorizador.tiene('objetivo_estrategico','leer')});
 *       200 OK; 404 si no es accesible (Req 58.10, 23.3).</li>
 *   <li>{@code GET /estrategia/objetivos?enPeriodo=&responsable=&page=&size=} â€”
 *       listado paginado (20/100) con filtros por periodo y responsable
 *       ({@code @autorizador.tiene('objetivo_estrategico','listar')}); 200 OK
 *       (Req 58.5, 58.6).</li>
 *   <li>{@code PUT /estrategia/objetivos/{id}/avance} â€” actualizar avance manual
 *       ({@code @autorizador.tiene('objetivo_estrategico','actualizar')}); 200 OK
 *       (Req 58.4, 58.9).</li>
 *   <li>{@code POST /estrategia/objetivos/{id}/resultados-clave} â€” agregar resultado
 *       clave ({@code @autorizador.tiene('objetivo_estrategico','actualizar')}); 201
 *       Created (Req 58.8).</li>
 *   <li>{@code PUT /estrategia/resultados-clave/{id}/valor} â€” actualizar el valor
 *       actual de un resultado clave, recalculando el avance
 *       ({@code @autorizador.tiene('objetivo_estrategico','actualizar')}); 200 OK
 *       (Req 58.8).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.14)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code planeacion_estrategica:{leer,actualizar}} y
 * {@code objetivo_estrategico:{crear,leer,listar,actualizar}} ya se sembraron en V5
 * y se asignaron a los roles {@code admin_empresa} y {@code gerente}, por lo que la
 * migracion V38 no requiere sembrar permisos adicionales.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el {@code tenant_id}
 * y el actor se derivan del contexto. El manejo de errores lo centraliza
 * {@code ManejadorGlobalErrores} (422 regla de negocio, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/estrategia")
public class EstrategiaController {

    private final ServicioEstrategia servicioEstrategia;

    public EstrategiaController(ServicioEstrategia servicioEstrategia) {
        this.servicioEstrategia = servicioEstrategia;
    }

    /**
     * Registra o actualiza (upsert) la mision, vision y valores de la Empresa
     * (Req 58.1).
     *
     * @param request cuerpo con mision/vision/valores (todos opcionales).
     * @return 200 OK con la {@link EsenciaEmpresaDto} resultante.
     */
    @PutMapping("/esencia")
    @PreAuthorize("@autorizador.moduloHabilitado('estrategia') and @autorizador.tiene('planeacion_estrategica','actualizar')")
    public ResponseEntity<EsenciaEmpresaDto> guardarEsencia(
            @Valid @RequestBody GuardarEsenciaRequest request) {
        return ResponseEntity.ok(servicioEstrategia.guardarEsencia(
                request.mision(), request.vision(), request.valores()));
    }

    /**
     * Consulta la mision, vision y valores de la Empresa (Req 58.1). 404 si aun no
     * se han registrado.
     *
     * @return 200 OK con la {@link EsenciaEmpresaDto}.
     */
    @GetMapping("/esencia")
    @PreAuthorize("@autorizador.moduloHabilitado('estrategia') and @autorizador.tiene('planeacion_estrategica','leer')")
    public ResponseEntity<EsenciaEmpresaDto> consultarEsencia() {
        return ResponseEntity.ok(servicioEstrategia.consultarEsencia());
    }

    /**
     * Crea un Objetivo_Estrategico con avance inicial 0 (Req 58.2). 422 si falta un
     * campo obligatorio (Req 58.3).
     *
     * @param request cuerpo con los datos del objetivo.
     * @return 201 Created con el {@link ObjetivoEstrategicoDto} creado.
     */
    @PostMapping("/objetivos")
    @PreAuthorize("@autorizador.moduloHabilitado('estrategia') and @autorizador.tiene('objetivo_estrategico','crear')")
    public ResponseEntity<ObjetivoEstrategicoDto> crearObjetivo(
            @Valid @RequestBody CrearObjetivoRequest request) {
        ObjetivoEstrategicoDto dto = servicioEstrategia.crearObjetivo(request.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un objetivo con su avance y estado derivado a la fecha actual
     * (Req 58.10). 404 si no es accesible (Req 23.3).
     *
     * @param id identificador del objetivo.
     * @return 200 OK con el {@link ObjetivoEstrategicoDto}.
     */
    @GetMapping("/objetivos/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('estrategia') and @autorizador.tiene('objetivo_estrategico','leer')")
    public ResponseEntity<ObjetivoEstrategicoDto> consultarObjetivo(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioEstrategia.consultarObjetivo(id));
    }

    /**
     * Lista los Objetivos_Estrategicos del tenant de forma paginada (20 por defecto,
     * 100 maximo) con filtros opcionales por periodo (vigencia en una fecha) y por
     * responsable (Req 58.5, 58.6).
     *
     * @param enPeriodo   fecha (ISO) para filtrar objetivos vigentes; opcional.
     * @param responsable responsable a filtrar; opcional.
     * @param page        numero de pagina 0-index; opcional.
     * @param size        tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ObjetivoEstrategicoDto}.
     */
    @GetMapping("/objetivos")
    @PreAuthorize("@autorizador.moduloHabilitado('estrategia') and @autorizador.tiene('objetivo_estrategico','listar')")
    public PaginaResponse<ObjetivoEstrategicoDto> listarObjetivos(
            @RequestParam(name = "enPeriodo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate enPeriodo,
            @RequestParam(name = "responsable", required = false) String responsable,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioEstrategia.listarObjetivos(enPeriodo, responsable, pageable));
    }

    /**
     * Actualiza el avance de un objetivo sin resultados clave (Req 58.4, 58.9). Con
     * resultados clave, el avance es derivado (422). 404 si no es accesible.
     *
     * @param id      identificador del objetivo.
     * @param request cuerpo con el nuevo avance.
     * @return 200 OK con el {@link ObjetivoEstrategicoDto} actualizado.
     */
    @PutMapping("/objetivos/{id}/avance")
    @PreAuthorize("@autorizador.moduloHabilitado('estrategia') and @autorizador.tiene('objetivo_estrategico','actualizar')")
    public ResponseEntity<ObjetivoEstrategicoDto> actualizarAvance(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarAvanceRequest request) {
        return ResponseEntity.ok(servicioEstrategia.actualizarAvanceManual(id, request.avance()));
    }

    /**
     * Agrega un resultado clave ponderado a un objetivo, recalculando su avance
     * (Req 58.8). 404 si el objetivo no es accesible.
     *
     * @param id      identificador del objetivo.
     * @param request cuerpo con los datos del resultado clave.
     * @return 201 Created con el {@link ObjetivoEstrategicoDto} y su avance recalculado.
     */
    @PostMapping("/objetivos/{id}/resultados-clave")
    @PreAuthorize("@autorizador.moduloHabilitado('estrategia') and @autorizador.tiene('objetivo_estrategico','actualizar')")
    public ResponseEntity<ObjetivoEstrategicoDto> agregarResultadoClave(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AgregarResultadoClaveRequest request) {
        ObjetivoEstrategicoDto dto =
                servicioEstrategia.agregarResultadoClave(id, request.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Actualiza el valor actual de un resultado clave, recalculando el avance
     * ponderado del objetivo contenedor (Req 58.8). 404 si el resultado clave no es
     * accesible.
     *
     * @param id      identificador del resultado clave.
     * @param request cuerpo con el nuevo valor actual.
     * @return 200 OK con el {@link ObjetivoEstrategicoDto} y su avance recalculado.
     */
    @PutMapping("/resultados-clave/{id}/valor")
    @PreAuthorize("@autorizador.moduloHabilitado('estrategia') and @autorizador.tiene('objetivo_estrategico','actualizar')")
    public ResponseEntity<ObjetivoEstrategicoDto> actualizarValorResultadoClave(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarValorResultadoClaveRequest request) {
        return ResponseEntity.ok(
                servicioEstrategia.actualizarValorResultadoClave(id, request.valorActual()));
    }
}
