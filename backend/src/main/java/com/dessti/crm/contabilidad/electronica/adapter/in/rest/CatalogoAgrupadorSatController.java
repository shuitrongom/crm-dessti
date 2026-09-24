package com.dessti.crm.contabilidad.electronica.adapter.in.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.contabilidad.electronica.application.CatalogoAgrupadorSatPort;
import com.dessti.crm.contabilidad.electronica.application.CodigoAgrupadorSatDto;

/**
 * Adaptador de entrada REST que expone el catalogo oficial de codigos agrupadores
 * del SAT (Apartado B del Anexo 24) como consulta de solo lectura, para el
 * autocompletar del amarre de Cuentas_Contables (Req 1.2).
 *
 * <p>Ruta (relativa al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /contabilidad/codigos-agrupadores-sat?q=} — busca/lista los
 *       codigos agrupadores ({@code contabilidad_electronica:leer}).</li>
 * </ul>
 *
 * <p>El catalogo es dato de plataforma (comun a todos los tenants); el gating por
 * modulo {@code contabilidad} y el permiso protegen la consulta.</p>
 */
@RestController
@RequestMapping("/contabilidad/codigos-agrupadores-sat")
public class CatalogoAgrupadorSatController {

    private static final int LIMITE_RESULTADOS = 50;

    private final CatalogoAgrupadorSatPort catalogo;

    public CatalogoAgrupadorSatController(CatalogoAgrupadorSatPort catalogo) {
        this.catalogo = catalogo;
    }

    /**
     * Busca codigos agrupadores del SAT por coincidencia en codigo o nombre.
     *
     * @param q texto de busqueda; opcional (vacio devuelve el inicio del catalogo).
     * @return 200 OK con la lista de codigos agrupadores.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('contabilidad_electronica','leer')")
    public ResponseEntity<List<CodigoAgrupadorSatDto>> buscar(
            @RequestParam(name = "q", required = false) String q) {
        List<CodigoAgrupadorSatDto> resultado = catalogo.buscar(q, LIMITE_RESULTADOS).stream()
                .map(CodigoAgrupadorSatDto::de)
                .toList();
        return ResponseEntity.ok(resultado);
    }
}
