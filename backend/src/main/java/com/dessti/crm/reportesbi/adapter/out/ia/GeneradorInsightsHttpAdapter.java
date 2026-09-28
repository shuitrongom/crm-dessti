package com.dessti.crm.reportesbi.adapter.out.ia;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dessti.crm.reportesbi.application.ia.GeneradorInsightsPort;
import com.dessti.crm.reportesbi.application.ia.IndicadorInsight;
import com.dessti.crm.reportesbi.application.ia.ResultadoInsights;
import com.dessti.crm.reportesbi.application.ia.SolicitudInsights;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Adaptador HTTP <strong>real</strong> del {@link GeneradorInsightsPort} hacia un
 * proveedor de IA externo (LLM por HTTP, contrato estilo <em>chat completions</em>) que
 * redacta los insights ejecutivos de la suite BI en espanol de Mexico (Req 48, suite
 * BI+IA; Req 11).
 *
 * <h2>Degradacion gracil (patron del proyecto)</h2>
 * <p>Este adaptador <strong>nunca</strong> rompe el flujo del tablero: ante cualquier
 * fallo delega en el {@link GeneradorInsightsHeuristico heuristico determinista} que
 * recibe como respaldo. En concreto degrada cuando:</p>
 * <ul>
 *   <li>el proveedor no esta configurado ({@code crm.ia.url}/{@code crm.ia.api-key}
 *       vacios, {@link IaProperties#estaConfigurado()} = {@code false});</li>
 *   <li>la solicitud no aporta indicadores con datos;</li>
 *   <li>la llamada HTTP falla, agota el tiempo o responde con estado no 2xx;</li>
 *   <li>la respuesta no trae un contenido de texto utilizable.</li>
 * </ul>
 * <p>En exito devuelve el narrativo del proveedor con {@code generadoPorIa=true}. Esto
 * replica el enfoque de la poliza contable automatica: la funcionalidad avanzada suma
 * cuando esta disponible, pero su ausencia jamas degrada la operacion base.</p>
 *
 * <h2>Secretos (Req 11)</h2>
 * <p>La clave de API viaja solo en la cabecera {@code Authorization} y <strong>nunca</strong>
 * se escribe en logs: los mensajes de diagnostico omiten credenciales y cuerpos.</p>
 *
 * <h2>Testabilidad</h2>
 * <p>El {@link HttpClient} se inyecta por constructor (paquete-privado) para sustituirlo
 * por un doble sin red real, siguiendo el patron de {@code ServicioGeocoding}.</p>
 */
public class GeneradorInsightsHttpAdapter implements GeneradorInsightsPort {

    private static final Logger LOG = LoggerFactory.getLogger(GeneradorInsightsHttpAdapter.class);

    /** Numero maximo de indicadores que se envian al proveedor para acotar el prompt. */
    private static final int MAX_INDICADORES_PROMPT = 60;

    private final IaProperties propiedades;
    private final GeneradorInsightsPort respaldo;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Constructor de produccion. Construye el {@link HttpClient} con el timeout de
     * conexion configurado en {@link IaProperties}.
     *
     * @param propiedades  configuracion del proveedor ({@code crm.ia.*}).
     * @param respaldo     generador heuristico usado en la degradacion gracil.
     * @param objectMapper mapeador de JSON de la aplicacion.
     */
    public GeneradorInsightsHttpAdapter(IaProperties propiedades,
                                        GeneradorInsightsPort respaldo,
                                        ObjectMapper objectMapper) {
        this(propiedades, respaldo, objectMapper,
                HttpClient.newBuilder().connectTimeout(propiedades.timeout()).build());
    }

    /**
     * Constructor con el {@link HttpClient} inyectado, usado por las pruebas para
     * sustituirlo por un doble sin red real.
     *
     * @param propiedades  configuracion del proveedor.
     * @param respaldo     generador heuristico de respaldo.
     * @param objectMapper mapeador de JSON.
     * @param httpClient   cliente HTTP colaborador.
     */
    GeneradorInsightsHttpAdapter(IaProperties propiedades,
                                 GeneradorInsightsPort respaldo,
                                 ObjectMapper objectMapper,
                                 HttpClient httpClient) {
        this.propiedades = propiedades;
        this.respaldo = respaldo;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public ResultadoInsights generar(SolicitudInsights solicitud) {
        // Sin configuracion o sin datos: se resuelve con el heuristico, sin tocar la red.
        if (!propiedades.estaConfigurado() || solicitud == null || solicitud.sinDatos()) {
            return respaldo.generar(solicitud);
        }
        try {
            String cuerpo = construirPeticion(solicitud);
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(propiedades.url()))
                    .timeout(propiedades.timeout())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + propiedades.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> respuesta =
                    httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() / 100 != 2) {
                LOG.warn("Insights BI: el proveedor de IA respondio estado {}; se usa el "
                        + "generador heuristico.", respuesta.statusCode());
                return respaldo.generar(solicitud);
            }
            ResultadoInsights resultado = interpretar(respuesta.body());
            if (resultado == null) {
                LOG.warn("Insights BI: respuesta de IA sin contenido utilizable; se usa el "
                        + "generador heuristico.");
                return respaldo.generar(solicitud);
            }
            return resultado;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Insights BI: llamada a la IA interrumpida; se usa el generador "
                    + "heuristico.", e);
            return respaldo.generar(solicitud);
        } catch (Exception e) {
            LOG.warn("Insights BI: fallo/timeout llamando a la IA; se usa el generador "
                    + "heuristico.", e);
            return respaldo.generar(solicitud);
        }
    }

    // ------------------------------------------------------------------
    // Construccion de la peticion (contrato estilo chat completions)
    // ------------------------------------------------------------------

    /**
     * Arma el cuerpo JSON de la peticion: un mensaje de sistema que fija el rol
     * (analista de negocio, respuesta en JSON es-MX) y un mensaje de usuario con los
     * indicadores del periodo. Se pide una respuesta en JSON con {@code resumen} y
     * {@code hallazgos} para un parseo robusto.
     */
    private String construirPeticion(SolicitudInsights solicitud)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        ObjectNode raiz = objectMapper.createObjectNode();
        raiz.put("model", propiedades.modeloEfectivo());
        raiz.put("temperature", 0.2);

        ArrayNode mensajes = raiz.putArray("messages");

        ObjectNode sistema = mensajes.addObject();
        sistema.put("role", "system");
        sistema.put("content", "Eres un analista de negocio senior. Redacta en espanol de "
                + "Mexico, con tono ejecutivo, claro y accionable. Responde UNICAMENTE con un "
                + "objeto JSON valido con las claves \"resumen\" (cadena, 2 a 4 frases) y "
                + "\"hallazgos\" (arreglo de cadenas, cada una un punto concreto). No incluyas "
                + "texto fuera del JSON.");

        ObjectNode usuario = mensajes.addObject();
        usuario.put("role", "user");
        usuario.put("content", construirPrompt(solicitud));

        return objectMapper.writeValueAsString(raiz);
    }

    /** Construye el prompt de usuario con el contexto del periodo y los indicadores. */
    private String construirPrompt(SolicitudInsights solicitud) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analiza el desempeno del negocio y genera insights ejecutivos.\n");
        sb.append("Periodo: ")
                .append(solicitud.desde() == null ? "sin inicio" : solicitud.desde())
                .append(" a ")
                .append(solicitud.hasta() == null ? "sin fin" : solicitud.hasta())
                .append(".\n");
        sb.append("Alcance: ")
                .append(solicitud.area() == null || solicitud.area().isBlank()
                        ? "todas las areas" : "area " + solicitud.area())
                .append(".\n");
        sb.append("Indicadores (etiqueta [area]: valor actual; anterior; variacion; unidad):\n");
        List<IndicadorInsight> indicadores = solicitud.indicadores();
        int limite = Math.min(indicadores.size(), MAX_INDICADORES_PROMPT);
        for (int i = 0; i < limite; i++) {
            IndicadorInsight ind = indicadores.get(i);
            sb.append("- ").append(ind.etiqueta()).append(" [").append(ind.area()).append("]: ")
                    .append(ind.valor());
            if (ind.tieneComparativo()) {
                sb.append("; anterior ").append(ind.comparativo())
                        .append("; variacion ").append(ind.variacion());
            } else {
                sb.append("; sin comparativo");
            }
            sb.append("; ").append(ind.unidad() == null ? "" : ind.unidad()).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Interpretacion de la respuesta
    // ------------------------------------------------------------------

    /**
     * Extrae el contenido del primer mensaje de la respuesta (estilo chat completions),
     * intenta interpretarlo como el JSON solicitado ({@code resumen}/{@code hallazgos})
     * y, si no lo es, usa el texto plano como resumen. Devuelve {@code null} si no hay
     * contenido utilizable, para que el llamador degrade al heuristico.
     */
    private ResultadoInsights interpretar(String cuerpo) {
        try {
            JsonNode raiz = objectMapper.readTree(cuerpo);
            JsonNode contenido = raiz.path("choices").path(0).path("message").path("content");
            if (contenido.isMissingNode() || contenido.isNull()) {
                return null;
            }
            String texto = contenido.asText("").trim();
            if (texto.isEmpty()) {
                return null;
            }
            ResultadoInsights estructurado = intentarJson(texto);
            if (estructurado != null) {
                return estructurado;
            }
            // El proveedor devolvio texto libre: se usa integro como resumen ejecutivo.
            return ResultadoInsights.deIa(texto, List.of());
        } catch (Exception e) {
            LOG.warn("Insights BI: no se pudo interpretar la respuesta de la IA.", e);
            return null;
        }
    }

    /** Intenta leer el contenido como el JSON {resumen, hallazgos}; null si no aplica. */
    private ResultadoInsights intentarJson(String texto) {
        String limpio = desenvolverJson(texto);
        try {
            JsonNode nodo = objectMapper.readTree(limpio);
            if (!nodo.isObject()) {
                return null;
            }
            JsonNode resumen = nodo.path("resumen");
            if (resumen.isMissingNode() || resumen.asText("").isBlank()) {
                return null;
            }
            List<String> hallazgos = new ArrayList<>();
            JsonNode arreglo = nodo.path("hallazgos");
            if (arreglo.isArray()) {
                for (JsonNode item : arreglo) {
                    String h = item.asText("").trim();
                    if (!h.isBlank()) {
                        hallazgos.add(h);
                    }
                }
            }
            return ResultadoInsights.deIa(resumen.asText().trim(), hallazgos);
        } catch (Exception e) {
            return null;
        }
    }

    /** Quita cercas de codigo Markdown (```json ... ```) si el proveedor las incluye. */
    private String desenvolverJson(String texto) {
        String t = texto.strip();
        if (t.startsWith("```")) {
            int primerSalto = t.indexOf('\n');
            if (primerSalto >= 0) {
                t = t.substring(primerSalto + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.strip();
    }
}
