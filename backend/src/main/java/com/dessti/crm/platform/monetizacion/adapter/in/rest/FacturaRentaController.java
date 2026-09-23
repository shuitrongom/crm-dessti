package com.dessti.crm.platform.monetizacion.adapter.in.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.monetizacion.application.FacturaRentaDto;
import com.dessti.crm.platform.monetizacion.application.ServicioFacturacionRenta;

/**
 * Adaptador REST de plataforma para la factura de renta de modulos por Empresa
 * (V23, Req 24.3). Rutas guardadas por permisos {@code factura_renta:*} del
 * {@code super_admin} (V23).
 *
 * <p>Rutas (context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET  /empresas/{tenantId}/renta?periodo=YYYY-MM-01} — calcula
 *       (sin emitir) la renta del periodo para previsualizar.</li>
 *   <li>{@code POST /empresas/{tenantId}/renta?periodo=YYYY-MM-01} — emite y
 *       persiste la factura de renta del periodo (409 si ya existe).</li>
 *   <li>{@code GET  /empresas/{tenantId}/facturas-renta} — lista las facturas de
 *       renta de la Empresa.</li>
 *   <li>{@code GET  /facturas-renta/{id}} — consulta una factura de renta.</li>
 *   <li>{@code GET  /facturas-renta/{id}/pdf} — descarga el comprobante PREMIUM
 *       en PDF ({@code application/pdf}, {@code Content-Disposition: inline}).</li>
 * </ul>
 */
@RestController
@RequestMapping
public class FacturaRentaController {

    private final ServicioFacturacionRenta servicioFacturacion;

    public FacturaRentaController(ServicioFacturacionRenta servicioFacturacion) {
        this.servicioFacturacion = servicioFacturacion;
    }

    @GetMapping("/empresas/{tenantId}/renta")
    @PreAuthorize("@autorizador.tiene('factura_renta','leer')")
    public FacturaRentaDto calcular(@PathVariable("tenantId") UUID tenantId,
            @RequestParam(name = "periodo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo) {
        return servicioFacturacion.calcular(tenantId, periodo);
    }

    @PostMapping("/empresas/{tenantId}/renta")
    @PreAuthorize("@autorizador.tiene('factura_renta','crear')")
    public ResponseEntity<FacturaRentaDto> emitir(@PathVariable("tenantId") UUID tenantId,
            @RequestParam(name = "periodo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(servicioFacturacion.emitir(tenantId, periodo));
    }

    @GetMapping("/empresas/{tenantId}/facturas-renta")
    @PreAuthorize("@autorizador.tiene('factura_renta','listar')")
    public List<FacturaRentaDto> listar(@PathVariable("tenantId") UUID tenantId) {
        return servicioFacturacion.listarPorEmpresa(tenantId);
    }

    @GetMapping("/facturas-renta/{id}")
    @PreAuthorize("@autorizador.tiene('factura_renta','leer')")
    public FacturaRentaDto consultar(@PathVariable("id") UUID id) {
        return servicioFacturacion.consultar(id);
    }

    /**
     * Descarga el comprobante PREMIUM de renta en PDF (Fase A). Carga la factura
     * (404 si no existe), la enriquece con los datos del emisor (Dess-TI) y del
     * receptor (Empresa) y devuelve los bytes del PDF para visualizarlo en linea.
     *
     * @param id identificador de la factura de renta.
     * @return respuesta {@code 200} con {@code Content-Type: application/pdf} y
     *         {@code Content-Disposition: inline; filename="factura-renta-{folio}-{periodo}.pdf"}.
     */
    @GetMapping("/facturas-renta/{id}/pdf")
    @PreAuthorize("@autorizador.tiene('factura_renta','leer')")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable("id") UUID id) {
        FacturaRentaDto factura = servicioFacturacion.consultar(id);
        byte[] pdf = servicioFacturacion.generarPdf(id);

        String nombreArchivo = "factura-renta-" + folioCorto(factura.id())
                + "-" + factura.periodo() + ".pdf";
        ContentDisposition disposicion = ContentDisposition.inline()
                .filename(nombreArchivo).build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(pdf);
    }

    /** Folio: primeros 8 caracteres del id (sin guiones) en mayusculas. */
    private static String folioCorto(UUID id) {
        String plano = id.toString().replace("-", "");
        return plano.substring(0, Math.min(8, plano.length())).toUpperCase(java.util.Locale.ROOT);
    }
}