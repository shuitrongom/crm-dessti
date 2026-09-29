package com.dessti.crm.operacion.proyecto.adapter.in.rest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dessti.crm.operacion.proyecto.application.evidencia.ArchivoEvidencia;
import com.dessti.crm.operacion.proyecto.application.evidencia.EvidenciaAvanceDto;
import com.dessti.crm.operacion.proyecto.application.evidencia.ServicioEvidenciasAvance;
import com.dessti.crm.platform.error.ReglaNegocioException;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST de las EVIDENCIAS de avance de sitio (Req 3.2,
 * deber-ser enterprise). Expone la subida de archivos reales (multipart), el
 * listado por Sitio, la descarga/visualizacion del binario y la decision de
 * aprobacion (aprobar/rechazar).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /proyectos/{id}/sitios/{sitioId}/evidencias} — sube un
 *       archivo de evidencia (multipart); crea la evidencia en {@code pendiente}.
 *       Autoriza {@code operacion} + {@code proyecto:actualizar} (mismo actor
 *       operativo que avanza la fase).</li>
 *   <li>{@code GET /proyectos/{id}/sitios/{sitioId}/evidencias} — lista las
 *       evidencias del Sitio ({@code proyecto:leer}).</li>
 *   <li>{@code GET /evidencias-avance/{evidenciaId}/archivo} — sirve el binario
 *       para visualizar/descargar ({@code proyecto:leer}).</li>
 *   <li>{@code PUT /evidencias-avance/{evidenciaId}/decision?accion=aprobar|rechazar}
 *       — aprueba/rechaza la evidencia ({@code evidencia_avance:aprobar}).</li>
 * </ul>
 *
 * <p>El manejo de errores lo centraliza {@code ManejadorGlobalErrores} (404 no
 * accesible, 409 transicion invalida, 422 regla de negocio). El {@code tenant_id}
 * y el actor se derivan del contexto, nunca del cuerpo.</p>
 */
@RestController
@RequestMapping
public class EvidenciaAvanceController {

    private final ServicioEvidenciasAvance servicioEvidencias;

    public EvidenciaAvanceController(ServicioEvidenciasAvance servicioEvidencias) {
        this.servicioEvidencias = servicioEvidencias;
    }

    /**
     * Sube un archivo de evidencia que respalda la fase actual del Sitio (Req 3.2).
     * El archivo viaja como parte multipart {@code archivo}. La validacion de tipo y
     * tamano la aplica el servicio contra la configuracion; 422 si no procede.
     *
     * @param id      identificador del Proyecto.
     * @param sitioId identificador del Sitio.
     * @param archivo parte multipart con el binario de la evidencia.
     * @return 201 Created con el {@link EvidenciaAvanceDto} creado (pendiente).
     */
    @PostMapping(path = "/proyectos/{id}/sitios/{sitioId}/evidencias",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','actualizar')")
    public ResponseEntity<EvidenciaAvanceDto> subir(
            @PathVariable("id") UUID id,
            @PathVariable("sitioId") UUID sitioId,
            @RequestPart("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioException("Debes adjuntar un archivo de evidencia.");
        }
        byte[] contenido = leerBytes(archivo);
        EvidenciaAvanceDto dto = servicioEvidencias.subir(
                id, sitioId, contenido, archivo.getOriginalFilename(), archivo.getContentType());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista las evidencias del Sitio, mas reciente primero (Req 3.2).
     *
     * @param id      identificador del Proyecto.
     * @param sitioId identificador del Sitio.
     * @return 200 OK con la lista de {@link EvidenciaAvanceDto}.
     */
    @GetMapping("/proyectos/{id}/sitios/{sitioId}/evidencias")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','leer')")
    public List<EvidenciaAvanceDto> listar(
            @PathVariable("id") UUID id,
            @PathVariable("sitioId") UUID sitioId) {
        return servicioEvidencias.listar(id, sitioId);
    }

    /**
     * Sirve el binario de una evidencia para visualizarla o descargarla (Req 3.2).
     * Se entrega {@code inline} (para verla en el navegador); el {@code Content-Type}
     * refleja el MIME almacenado. 404 si no es accesible.
     *
     * @param evidenciaId identificador de la evidencia.
     * @return 200 OK con los bytes del archivo.
     */
    @GetMapping("/evidencias-avance/{evidenciaId}/archivo")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('proyecto','leer')")
    public ResponseEntity<byte[]> descargarArchivo(@PathVariable("evidenciaId") UUID evidenciaId) {
        ArchivoEvidencia archivo = servicioEvidencias.leerArchivo(evidenciaId);
        MediaType tipo;
        try {
            tipo = MediaType.parseMediaType(archivo.tipoMime());
        } catch (RuntimeException ex) {
            tipo = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposicion = ContentDisposition.inline()
                .filename(archivo.nombreOriginal()).build();
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(archivo.contenido());
    }

    /**
     * Aprueba o rechaza una evidencia (Req 3.2). El rechazo exige un motivo en el
     * cuerpo. Operacion de control de calidad: exige {@code evidencia_avance:aprobar}.
     *
     * @param evidenciaId identificador de la evidencia.
     * @param accion      {@code aprobar} o {@code rechazar}.
     * @param request     cuerpo opcional con el motivo (obligatorio al rechazar).
     * @return 200 OK con el {@link EvidenciaAvanceDto} tras la decision.
     */
    @PutMapping("/evidencias-avance/{evidenciaId}/decision")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('evidencia_avance','aprobar')")
    public ResponseEntity<EvidenciaAvanceDto> decidir(
            @PathVariable("evidenciaId") UUID evidenciaId,
            @RequestParam("accion") String accion,
            @RequestBody(required = false) @Valid DecisionEvidenciaRequest request) {
        boolean aprobar = switch (accion == null ? "" : accion.trim().toLowerCase()) {
            case "aprobar" -> true;
            case "rechazar" -> false;
            default -> throw new ReglaNegocioException(
                    "La accion debe ser 'aprobar' o 'rechazar'.");
        };
        String motivo = (request == null) ? null : request.motivo();
        return ResponseEntity.ok(servicioEvidencias.decidir(evidenciaId, aprobar, motivo));
    }

    /** Lee los bytes del archivo multipart, traduciendo un fallo de IO a 422. */
    private static byte[] leerBytes(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer el archivo de evidencia adjuntado.");
        }
    }
}
