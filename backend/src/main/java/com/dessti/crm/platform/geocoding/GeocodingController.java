package com.dessti.crm.platform.geocoding;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada REST para el <strong>autocompletado de direcciones via
 * backend propio</strong> (revision R2, Req 4). Expone una utilidad transversal
 * de captura de direcciones que el frontend consume a traves del proxy
 * {@code /api}, en lugar de que el navegador llame directamente al proveedor OSM
 * (que fallaba de forma intermitente por CORS/red del navegador).
 *
 * <p>Ruta (relativa al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /geocoding/direcciones?q=<texto>} — 200 OK con la lista de
 *       {@link DireccionSugeridaDto}. Cuando {@code q} es corto (menos del minimo
 *       tras normalizar) o el proveedor falla, la lista viene vacia.</li>
 * </ul>
 *
 * <h2>Autorizacion</h2>
 * <p>Solo exige estar <em>autenticado</em>
 * ({@code @PreAuthorize("isAuthenticated()")}), como el perfil propio o el gating
 * de modulos: es una utilidad transversal de captura de direcciones, sin datos
 * sensibles, por lo que NO se restringe con permiso de modulo ni con un permiso
 * atomico. Un peticion sin token valido responde 401 (Req 1.6 de seguridad).</p>
 */
@RestController
@RequestMapping("/geocoding")
public class GeocodingController {

    private final ServicioGeocoding servicioGeocoding;

    public GeocodingController(ServicioGeocoding servicioGeocoding) {
        this.servicioGeocoding = servicioGeocoding;
    }

    /**
     * Busca sugerencias de direccion para el texto {@code q}. Delega la
     * normalizacion, el minimo de caracteres y la consulta al proveedor en
     * {@link ServicioGeocoding}, que degrada a lista vacia ante error/timeout.
     *
     * @param q texto libre de la direccion; opcional (vacio -> lista vacia).
     * @return 200 OK con la lista de sugerencias (posiblemente vacia).
     */
    @GetMapping("/direcciones")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DireccionSugeridaDto>> buscarDirecciones(
            @RequestParam(name = "q", required = false, defaultValue = "") String q) {
        return ResponseEntity.ok(servicioGeocoding.buscar(q));
    }
}
