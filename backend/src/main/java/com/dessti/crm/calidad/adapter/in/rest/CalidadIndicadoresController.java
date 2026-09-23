package com.dessti.crm.calidad.adapter.in.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.calidad.application.IndicadoresCalidadDto;
import com.dessti.crm.calidad.application.ServicioIndicadoresCalidad;
import com.dessti.crm.calidad.application.ServicioTrazabilidadIso;
import com.dessti.crm.calidad.application.TrazabilidadClausulaDto;

/**
 * Adaptador de entrada REST de solo lectura del modulo {@code calidad} (Req 70.6, 70.7,
 * 70.8, 70.10; tareas 54.6 y 54.7). Expone:
 * <ul>
 *   <li>{@code GET /calidad/indicadores} — agregaciones de cultura de calidad y percepcion
 *       del cliente ({@code @autorizador.tiene('calidad','leer')}); surfaceables en el
 *       Tablero (Req 22, 48).</li>
 *   <li>{@code GET /calidad/trazabilidad-iso} — vista de trazabilidad de clausulas
 *       ISO 9001:2026 -> capacidades del Sistema ({@code @autorizador.tiene('calidad','leer')}).</li>
 * </ul>
 * <p>El permiso {@code calidad:leer} se sembro en V47 (rol {@code calidad}, {@code gerente} y
 * {@code admin_empresa}). Este controlador es <strong>propio del modulo calidad</strong>: no
 * toca reportes-bi ni sus puertos de area, evitando colisiones con desarrollos en paralelo.</p>
 */
@RestController
@RequestMapping("/calidad")
public class CalidadIndicadoresController {

    private final ServicioIndicadoresCalidad servicioIndicadores;
    private final ServicioTrazabilidadIso servicioTrazabilidad;

    public CalidadIndicadoresController(ServicioIndicadoresCalidad servicioIndicadores,
                                        ServicioTrazabilidadIso servicioTrazabilidad) {
        this.servicioIndicadores = servicioIndicadores;
        this.servicioTrazabilidad = servicioTrazabilidad;
    }

    /**
     * Devuelve los indicadores de cultura de calidad del tenant (Req 70.6, 70.7, 70.8). Es
     * una consulta de solo lectura que no modifica los datos de origen (Req 22).
     *
     * @return 200 OK con el {@link IndicadoresCalidadDto}.
     */
    @GetMapping("/indicadores")
    @PreAuthorize("@autorizador.tiene('calidad','leer')")
    public ResponseEntity<IndicadoresCalidadDto> indicadores() {
        return ResponseEntity.ok(servicioIndicadores.consultar());
    }

    /**
     * Devuelve la vista de trazabilidad de clausulas ISO 9001:2026 -> capacidades del
     * Sistema (Req 70.10). Catalogo fijo, estable e independiente del tenant.
     *
     * @return 200 OK con la lista de {@link TrazabilidadClausulaDto}.
     */
    @GetMapping("/trazabilidad-iso")
    @PreAuthorize("@autorizador.tiene('calidad','leer')")
    public ResponseEntity<List<TrazabilidadClausulaDto>> trazabilidadIso() {
        return ResponseEntity.ok(servicioTrazabilidad.trazabilidad());
    }
}
