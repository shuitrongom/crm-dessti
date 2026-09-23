package com.dessti.crm.platform.geocoding;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Servicio de <strong>geocoding via backend propio</strong> (revision R2 de la
 * especificacion "Emisor por Empresa y autocompletado"). Sustituye la llamada
 * directa del navegador al proveedor OSM —que fallaba de forma intermitente por
 * CORS/red del navegador y quedaba oculta tras un {@code catchError -> []}— por
 * una consulta desde el servidor, donde el proveedor SI responde de forma fiable.
 *
 * <p>Estrategia de proveedores (Req 4.1):</p>
 * <ol>
 *   <li><strong>Photon</strong> (komoot, GeoJSON) como proveedor primario, con un
 *       {@code bbox} amplio de Mexico para favorecer —sin excluir— resultados
 *       locales.</li>
 *   <li><strong>Nominatim</strong> (OpenStreetMap, {@code jsonv2}) como respaldo
 *       cuando Photon falla, agota el tiempo o devuelve vacio. Nominatim exige un
 *       {@code User-Agent} identificable, que se envia siempre.</li>
 * </ol>
 *
 * <p>Robustez (Req 4.5): toda peticion tiene TIMEOUT (~4 s de conexion y de
 * lectura). Ante CUALQUIER error o timeout se registra un {@code WARN} con la
 * causa —para que el fallo deje de ser invisible— y se devuelve una lista VACIA;
 * el metodo nunca lanza. Con menos del minimo de caracteres significativos no se
 * realiza ninguna llamada.</p>
 *
 * <p>Cortesia con el servicio publico: una cache en memoria de vida corta
 * (TTL ~60 s por termino normalizado) evita repetir llamadas identicas. El
 * {@link HttpClient} es un colaborador inyectado por constructor para poder
 * probar el mapeo y el manejo de errores sin red real.</p>
 */
@Service
public class ServicioGeocoding {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioGeocoding.class);

    /** Minimo de caracteres significativos antes de consultar al proveedor. */
    static final int MIN_CARACTERES = 3;
    /** Numero maximo de sugerencias solicitadas por consulta. */
    private static final int LIMITE = 6;
    /** Timeout de la peticion (conexion y lectura) al proveedor OSM. */
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    /** Vida de una entrada de cache por termino normalizado. */
    private static final Duration TTL_CACHE = Duration.ofSeconds(60);
    /** Caja delimitadora AMPLIA de Mexico para Photon: minLon,minLat,maxLon,maxLat. */
    private static final String BBOX_MEXICO = "-118.6,14.3,-86.5,32.8";
    /** Endpoint publico de Photon (GeoJSON), sin clave de API. */
    private static final String PHOTON_URL = "https://photon.komoot.io/api/";
    /** Endpoint publico de Nominatim (OpenStreetMap), sin clave de API. */
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    /** User-Agent identificable requerido por la politica de uso de Nominatim. */
    private static final String USER_AGENT = "plataforma-multigiro/1.0 (soporte@dessti.com)";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, EntradaCache> cache = new ConcurrentHashMap<>();

    /**
     * @param objectMapper el {@link ObjectMapper} de Jackson de la aplicacion
     *                     (autoconfigurado por Spring Boot), reutilizado para
     *                     parsear la respuesta del proveedor.
     */
    @Autowired
    public ServicioGeocoding(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    /**
     * Constructor con el {@link HttpClient} inyectado, usado por las pruebas para
     * sustituirlo por un doble sin red real.
     *
     * @param objectMapper mapeador de JSON reutilizado de la aplicacion.
     * @param httpClient   cliente HTTP colaborador (con timeout de conexion).
     */
    ServicioGeocoding(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * Busca direcciones que coincidan con {@code q} y devuelve las sugerencias ya
     * mapeadas. Normaliza el termino (colapsa espacios y recorta); con menos de
     * {@link #MIN_CARACTERES} caracteres devuelve lista vacia sin llamar al
     * proveedor. Consulta Photon y, si falla o no arroja resultados, cae a
     * Nominatim. Ante cualquier error o timeout registra {@code WARN} y devuelve
     * lista vacia (nunca lanza).
     *
     * @param q texto libre de la direccion a buscar.
     * @return lista de sugerencias (posiblemente vacia); nunca {@code null}.
     */
    public List<DireccionSugeridaDto> buscar(String q) {
        String termino = normalizar(q);
        if (termino.length() < MIN_CARACTERES) {
            return List.of();
        }

        EntradaCache enCache = cache.get(termino);
        if (enCache != null && !enCache.expirada()) {
            return enCache.sugerencias();
        }

        List<DireccionSugeridaDto> resultado = consultarPhoton(termino);
        if (resultado.isEmpty()) {
            resultado = consultarNominatim(termino);
        }

        cache.put(termino, new EntradaCache(resultado, Instant.now().plus(TTL_CACHE)));
        return resultado;
    }

    /** Consulta Photon (GeoJSON) y mapea sus features; vacio ante error/timeout. */
    private List<DireccionSugeridaDto> consultarPhoton(String termino) {
        String url = PHOTON_URL + "?q=" + codificar(termino)
                + "&lang=es&limit=" + LIMITE + "&bbox=" + BBOX_MEXICO;
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> respuesta =
                    httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() / 100 != 2) {
                LOG.warn("Geocoding: Photon respondio estado {} para el termino '{}'",
                        respuesta.statusCode(), termino);
                return List.of();
            }
            return mapearPhoton(respuesta.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Geocoding: consulta a Photon interrumpida para el termino '{}'", termino, e);
            return List.of();
        } catch (Exception e) {
            LOG.warn("Geocoding: fallo/timeout consultando Photon para el termino '{}'",
                    termino, e);
            return List.of();
        }
    }

    /** Consulta Nominatim (jsonv2) con User-Agent; vacio ante error/timeout. */
    private List<DireccionSugeridaDto> consultarNominatim(String termino) {
        String url = NOMINATIM_URL + "?q=" + codificar(termino)
                + "&format=jsonv2&addressdetails=1&limit=" + LIMITE + "&countrycodes=mx";
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> respuesta =
                    httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() / 100 != 2) {
                LOG.warn("Geocoding: Nominatim respondio estado {} para el termino '{}'",
                        respuesta.statusCode(), termino);
                return List.of();
            }
            return mapearNominatim(respuesta.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Geocoding: consulta a Nominatim interrumpida para el termino '{}'",
                    termino, e);
            return List.of();
        } catch (Exception e) {
            LOG.warn("Geocoding: fallo/timeout consultando Nominatim para el termino '{}'",
                    termino, e);
            return List.of();
        }
    }

    /** Mapea la respuesta GeoJSON de Photon a la lista de sugerencias. */
    private List<DireccionSugeridaDto> mapearPhoton(String cuerpo) throws Exception {
        JsonNode raiz = objectMapper.readTree(cuerpo);
        JsonNode features = raiz.path("features");
        List<DireccionSugeridaDto> sugerencias = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode p = feature.path("properties");
            String via = primerNoVacio(texto(p, "street"), texto(p, "name"));
            String numero = texto(p, "housenumber");
            String calle = numero.isBlank() ? via : (via + " " + numero).trim();
            String estado = texto(p, "state");
            String ciudad = primerNoVacio(texto(p, "city"), texto(p, "district"),
                    texto(p, "county"), estado, texto(p, "name"));
            String cp = texto(p, "postcode");
            String pais = texto(p, "country");
            sugerencias.add(construir(calle, ciudad, estado, cp, pais));
        }
        return sugerencias;
    }

    /** Mapea la respuesta JSON de Nominatim (jsonv2) a la lista de sugerencias. */
    private List<DireccionSugeridaDto> mapearNominatim(String cuerpo) throws Exception {
        JsonNode raiz = objectMapper.readTree(cuerpo);
        List<DireccionSugeridaDto> sugerencias = new ArrayList<>();
        for (JsonNode item : raiz) {
            JsonNode a = item.path("address");
            String via = primerNoVacio(texto(a, "road"), texto(a, "pedestrian"),
                    texto(a, "neighbourhood"));
            String numero = texto(a, "house_number");
            String calle = numero.isBlank() ? via : (via + " " + numero).trim();
            String estado = texto(a, "state");
            String ciudad = primerNoVacio(texto(a, "city"), texto(a, "town"),
                    texto(a, "village"), texto(a, "county"), estado);
            String cp = texto(a, "postcode");
            String pais = texto(a, "country");
            String etiqueta = texto(item, "display_name");
            if (etiqueta.isBlank()) {
                etiqueta = etiquetaDe(calle, ciudad, estado, pais);
            }
            sugerencias.add(new DireccionSugeridaDto(etiqueta, calle, ciudad, estado, cp, pais));
        }
        return sugerencias;
    }

    /** Construye la sugerencia armando su etiqueta a partir de las partes no vacias. */
    private DireccionSugeridaDto construir(String calle, String ciudad, String estado,
                                           String cp, String pais) {
        return new DireccionSugeridaDto(
                etiquetaDe(calle, ciudad, estado, pais), calle, ciudad, estado, cp, pais);
    }

    /** Une las partes no vacias con comas para formar la etiqueta legible. */
    private String etiquetaDe(String calle, String ciudad, String estado, String pais) {
        List<String> partes = new ArrayList<>();
        for (String parte : List.of(calle, ciudad, estado, pais)) {
            if (!parte.isBlank()) {
                partes.add(parte);
            }
        }
        return String.join(", ", partes);
    }

    /** Lee un campo de texto del nodo, devolviendo cadena vacia si falta. */
    private String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        return valor.isMissingNode() || valor.isNull() ? "" : valor.asText("").trim();
    }

    /** Devuelve el primer valor no vacio (recortado) o cadena vacia. */
    private String primerNoVacio(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return "";
    }

    /** Normaliza el termino: colapsa espacios multiples a uno y recorta extremos. */
    private String normalizar(String texto) {
        return texto == null ? "" : texto.replaceAll("\\s+", " ").trim();
    }

    /** Codifica el termino para incrustarlo en la query de la URL del proveedor. */
    private String codificar(String termino) {
        return URLEncoder.encode(termino, StandardCharsets.UTF_8);
    }

    /** Entrada de la cache en memoria: sugerencias con su instante de expiracion. */
    private record EntradaCache(List<DireccionSugeridaDto> sugerencias, Instant expiraEn) {
        boolean expirada() {
            return Instant.now().isAfter(expiraEn);
        }
    }
}
