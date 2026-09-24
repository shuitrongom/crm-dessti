package com.dessti.crm.contabilidad.electronica.adapter.in.rest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.contabilidad.electronica.application.ArchivoXmlDto;
import com.dessti.crm.contabilidad.electronica.application.ServicioContabilidadElectronica;
import com.dessti.crm.contabilidad.electronica.application.VistaPreviaBalanzaDto;
import com.dessti.crm.contabilidad.electronica.application.VistaPreviaCatalogoDto;
import com.dessti.crm.contabilidad.electronica.application.VistaPreviaPolizasDto;

/**
 * Adaptador de entrada REST de la <strong>Contabilidad Electronica SAT</strong>
 * (Anexo 24). Ofrece, por periodo (anio/mes), la vista previa (JSON) y la descarga
 * (XML) del Catalogo de Cuentas (Req 2), la Balanza de Comprobacion (Req 3) y las
 * Polizas del Periodo (Req 4).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}, bajo
 * {@code /contabilidad/contabilidad-electronica}):</p>
 * <ul>
 *   <li>{@code GET /catalogo/preview} — vista previa del catalogo (perm leer).</li>
 *   <li>{@code GET /catalogo/xml?anio=&mes=} — descarga del catalogo (perm exportar).</li>
 *   <li>{@code GET /balanza/preview?anio=&mes=} — vista previa de la balanza (perm leer).</li>
 *   <li>{@code GET /balanza/xml?anio=&mes=} — descarga de la balanza (perm exportar).</li>
 *   <li>{@code GET /polizas/preview?anio=&mes=} — vista previa de polizas (perm leer).</li>
 *   <li>{@code GET /polizas/xml?anio=&mes=} — descarga de polizas (perm exportar).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 2.6, 3.6, 4.5, 5.4)</h2>
 * <p>Todas las rutas exigen el modulo {@code contabilidad} habilitado (gating por
 * Plan) y el permiso {@code contabilidad_electronica:{leer|exportar}} (sembrados en
 * V71 y enlazados al rol {@code contabilidad}). Sin ellos se responde 403.</p>
 */
@RestController
@RequestMapping("/contabilidad/contabilidad-electronica")
public class ContabilidadElectronicaController {

    private final ServicioContabilidadElectronica servicio;

    public ContabilidadElectronicaController(ServicioContabilidadElectronica servicio) {
        this.servicio = servicio;
    }

    // ---------------------- Catalogo de cuentas ----------------------

    @GetMapping("/catalogo/preview")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('contabilidad_electronica','leer')")
    public ResponseEntity<VistaPreviaCatalogoDto> previewCatalogo() {
        return ResponseEntity.ok(servicio.vistaPreviaCatalogo());
    }

    @GetMapping("/catalogo/xml")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('contabilidad_electronica','exportar')")
    public ResponseEntity<byte[]> xmlCatalogo(
            @RequestParam("anio") int anio,
            @RequestParam("mes") int mes) {
        return descargaXml(servicio.exportarCatalogo(anio, mes));
    }

    // ---------------------- Balanza de comprobacion ----------------------

    @GetMapping("/balanza/preview")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('contabilidad_electronica','leer')")
    public ResponseEntity<VistaPreviaBalanzaDto> previewBalanza(
            @RequestParam("anio") int anio,
            @RequestParam("mes") int mes) {
        return ResponseEntity.ok(servicio.vistaPreviaBalanza(anio, mes));
    }

    @GetMapping("/balanza/xml")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('contabilidad_electronica','exportar')")
    public ResponseEntity<byte[]> xmlBalanza(
            @RequestParam("anio") int anio,
            @RequestParam("mes") int mes) {
        return descargaXml(servicio.exportarBalanza(anio, mes));
    }

    // ---------------------- Polizas del periodo ----------------------

    @GetMapping("/polizas/preview")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('contabilidad_electronica','leer')")
    public ResponseEntity<VistaPreviaPolizasDto> previewPolizas(
            @RequestParam("anio") int anio,
            @RequestParam("mes") int mes) {
        return ResponseEntity.ok(servicio.vistaPreviaPolizas(anio, mes));
    }

    @GetMapping("/polizas/xml")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('contabilidad_electronica','exportar')")
    public ResponseEntity<byte[]> xmlPolizas(
            @RequestParam("anio") int anio,
            @RequestParam("mes") int mes) {
        return descargaXml(servicio.exportarPolizas(anio, mes));
    }

    // ---------------------- Utilidad de descarga ----------------------

    private ResponseEntity<byte[]> descargaXml(ArchivoXmlDto archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                .body(archivo.bytes());
    }
}
