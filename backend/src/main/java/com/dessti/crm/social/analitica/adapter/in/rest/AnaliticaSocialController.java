package com.dessti.crm.social.analitica.adapter.in.rest;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.social.analitica.application.ResumenAnaliticaSocialDto;
import com.dessti.crm.social.analitica.application.ServicioAnaliticaSocial;
import com.dessti.crm.social.domain.CanalSocial;

/**
 * Adaptador de entrada REST de la <strong>analitica social</strong> de solo lectura
 * (Req 66): metricas por Canal_Social y periodo del tenant vigente.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /social/analitica/metricas} — metricas por canal y periodo
 *       ({@code analitica_social:leer}).</li>
 *   <li>{@code GET /social/analitica/metricas/exportar} — exportacion del mismo
 *       resumen ({@code analitica_social:leer}).</li>
 * </ul>
 *
 * <h2>Filtros (Req 66.4)</h2>
 * <p>Parametros opcionales: {@code desde}/{@code hasta} (rango de fechas ISO),
 * {@code canal} (etiqueta {@code whatsapp}/{@code messenger}/{@code instagram}) y
 * {@code canalVentaId} (segmentacion por Canal_Venta, Req 66.2). Una etiqueta de canal
 * desconocida se traduce a 422.</p>
 *
 * <h2>Autorizacion (Req 3, 66.5)</h2>
 * <p>Cada ruta exige el permiso {@code analitica_social:leer} via
 * {@code @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene(...)")}: una peticion sin el permiso
 * analitico/comercial recibe 403 (Req 66.5). El permiso {@code analitica_social:leer}
 * ya se sembro en V5 y se asigno al rol {@code marketing}; se reutiliza aqui sin
 * re-sembrarlo. La exportacion se protege con el mismo permiso de lectura al no existir
 * una accion {@code exportar} sembrada para este recurso.</p>
 *
 * <h2>Aislamiento (Req 23, 66.6)</h2>
 * <p>El tenant se deriva del contexto autenticado (nunca de la peticion): las metricas
 * solo incluyen datos de la Empresa del Usuario.</p>
 */
@RestController
@RequestMapping("/social/analitica")
public class AnaliticaSocialController {

    private final ServicioAnaliticaSocial servicioAnaliticaSocial;

    public AnaliticaSocialController(ServicioAnaliticaSocial servicioAnaliticaSocial) {
        this.servicioAnaliticaSocial = servicioAnaliticaSocial;
    }

    /**
     * Metricas sociales por Canal_Social y periodo (Req 66.1, 66.4).
     *
     * @param desde        inicio del periodo (inclusivo); opcional.
     * @param hasta        fin del periodo (inclusivo); opcional.
     * @param canal        etiqueta del Canal_Social a filtrar; opcional.
     * @param canalVentaId Canal_Venta al que segmentar (Req 66.2); opcional.
     * @return 200 OK con el {@link ResumenAnaliticaSocialDto}.
     */
    @GetMapping("/metricas")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('analitica_social','leer')")
    public ResponseEntity<ResumenAnaliticaSocialDto> metricas(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "canal", required = false) String canal,
            @RequestParam(name = "canalVentaId", required = false) UUID canalVentaId) {
        return ResponseEntity.ok(servicioAnaliticaSocial
                .consultarMetricas(desde, hasta, canalOpcional(canal), canalVentaId));
    }

    /**
     * Exportacion de las metricas sociales por Canal_Social y periodo (Req 66.4).
     *
     * @param desde        inicio del periodo (inclusivo); opcional.
     * @param hasta        fin del periodo (inclusivo); opcional.
     * @param canal        etiqueta del Canal_Social a filtrar; opcional.
     * @param canalVentaId Canal_Venta al que segmentar (Req 66.2); opcional.
     * @return 200 OK con el {@link ResumenAnaliticaSocialDto} para exportar.
     */
    @GetMapping("/metricas/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('analitica_social','leer')")
    public ResponseEntity<ResumenAnaliticaSocialDto> exportarMetricas(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "canal", required = false) String canal,
            @RequestParam(name = "canalVentaId", required = false) UUID canalVentaId) {
        return ResponseEntity.ok(servicioAnaliticaSocial
                .exportarMetricas(desde, hasta, canalOpcional(canal), canalVentaId));
    }

    /**
     * Interpreta la etiqueta del Canal_Social; nula o en blanco no filtra.
     *
     * @param etiqueta etiqueta del canal; {@code null}/blanco devuelve {@code null}.
     * @return el canal, o {@code null} si no se indico.
     * @throws ReglaNegocioException si la etiqueta es desconocida (422).
     */
    private CanalSocial canalOpcional(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        try {
            return CanalSocial.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Canal_Social desconocido: " + etiqueta);
        }
    }
}
