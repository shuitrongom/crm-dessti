package com.dessti.crm.comercial.producto.adapter.in.rest;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.comercial.producto.application.PrecioSugeridoDto;
import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort;
import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort.ConsultaSugerenciaPrecio;

/**
 * Adaptador de entrada REST que expone la <strong>sugerencia de precio</strong>
 * de un Producto como una consulta independiente (Req 59.4, 59.9), sin crear una
 * Cotizacion.
 *
 * <p><strong>Motivacion:</strong> la regla de seleccion de precio
 * ({@link SugerenciaPrecioPort} / {@code ServicioSeleccionPrecio}) ya existe y se
 * consume internamente al crear una Cotizacion (bloque 17). Hasta ahora ese
 * precio de lista solo se resolvia en el servidor al guardar, de modo que la UI
 * no podia previsualizarlo y las partidas sin precio aparentaban importar cero.
 * Este endpoint <em>reutiliza el mismo puerto de dominio</em> (no duplica la
 * regla) para que la UI consulte el precio vigente al elegir el Producto y lo
 * muestre como valor sugerido; el Usuario puede ajustarlo antes de crear.</p>
 *
 * <p>Ruta (relativa al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /productos/{id}/precio-sugerido?fecha=&segmento=} — resuelve el
 *       precio de lista vigente ({@code @autorizador.tiene('lista_precios','leer')});
 *       200 OK con {@link PrecioSugeridoDto}. Si ninguna lista vigente aplica, el
 *       DTO indica {@code disponible=false} y {@code precioSugerido=null} (no se
 *       degrada a cero ni se responde 404: la ausencia de sugerencia es un
 *       resultado valido de la consulta).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 59)</h2>
 * <p>Se gobierna con {@code lista_precios:leer} porque es una lectura de la regla
 * de precios (la misma familia de permisos que consultar los precios de una
 * lista), ademas del modulo {@code comercial} habilitado.</p>
 *
 * <h2>Fecha de referencia</h2>
 * <p>Si no se indica {@code fecha}, se usa la fecha actual segun el {@link Clock}
 * inyectado (determinista y testeable), coherente con el resto del ambito.</p>
 */
@RestController
@RequestMapping("/productos")
public class PrecioSugeridoController {

    private final SugerenciaPrecioPort sugerenciaPrecio;
    private final Clock clock;

    public PrecioSugeridoController(SugerenciaPrecioPort sugerenciaPrecio, Clock clock) {
        this.sugerenciaPrecio = sugerenciaPrecio;
        this.clock = clock;
    }

    /**
     * Sugiere el precio unitario vigente de un Producto (Req 59.4, 59.9).
     *
     * @param id       identificador del Producto.
     * @param fecha    fecha de referencia para la vigencia (ISO {@code yyyy-MM-dd});
     *                 opcional, por defecto hoy segun el {@link Clock}.
     * @param segmento segmento del Cliente para preferir su lista; opcional.
     * @return 200 OK con el {@link PrecioSugeridoDto}; {@code disponible=false} si
     *         ninguna Lista_Precios vigente asigna precio al Producto.
     */
    @GetMapping("/{id}/precio-sugerido")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('lista_precios','leer')")
    public ResponseEntity<PrecioSugeridoDto> sugerir(
            @PathVariable("id") UUID id,
            @RequestParam(name = "fecha", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(name = "segmento", required = false) String segmento) {
        LocalDate fechaRef = (fecha != null) ? fecha : LocalDate.now(clock);
        String segmentoNormalizado = (segmento == null || segmento.isBlank()) ? null : segmento.strip();
        var precio = sugerenciaPrecio
                .sugerirPrecioUnitario(new ConsultaSugerenciaPrecio(id, segmentoNormalizado, fechaRef))
                .orElse(null);
        return ResponseEntity.ok(PrecioSugeridoDto.de(id, precio, fechaRef, segmentoNormalizado));
    }
}
