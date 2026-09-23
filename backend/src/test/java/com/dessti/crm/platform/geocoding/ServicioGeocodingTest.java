package com.dessti.crm.platform.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pruebas unitarias de {@link ServicioGeocoding} (revision R2): con un
 * {@link HttpClient} simulado (Mockito) verifican, sin red real, que
 * <ul>
 *   <li>una respuesta GeoJSON de Photon se mapea correctamente a
 *       {@link DireccionSugeridaDto} (via + numero, respaldo de ciudad, etiqueta);</li>
 *   <li>ante error/timeout del proveedor devuelve lista vacia sin lanzar;</li>
 *   <li>con {@code q} de menos de 3 caracteres no llama al cliente HTTP;</li>
 *   <li>si Photon no arroja resultados, cae al respaldo Nominatim y lo mapea.</li>
 * </ul>
 */
class ServicioGeocodingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> respuesta(int estado, String cuerpo) {
        HttpResponse<String> r = mock(HttpResponse.class);
        // Se usa lenient para permitir armar el doble ANTES de encadenar el
        // when(cliente.send(...)); asi se evita el "unfinished stubbing" que
        // ocurre al construir un mock dentro de otro when(...).thenReturn(...).
        org.mockito.Mockito.lenient().when(r.statusCode()).thenReturn(estado);
        org.mockito.Mockito.lenient().when(r.body()).thenReturn(cuerpo);
        return r;
    }

    @Test
    void mapeaRespuestaPhotonADto() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        String geojson = """
                {"features":[
                  {"properties":{
                    "name":"Catedral","street":"Avenida Juarez","housenumber":"100",
                    "postcode":"50000","city":"Toluca","state":"Estado de Mexico",
                    "country":"Mexico"}}
                ]}""";
        HttpResponse<String> ok = respuesta(200, geojson);
        when(cliente.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(ok);

        ServicioGeocoding servicio = new ServicioGeocoding(objectMapper, cliente);
        List<DireccionSugeridaDto> sugerencias = servicio.buscar("Toluca");

        assertThat(sugerencias).containsExactly(new DireccionSugeridaDto(
                "Avenida Juarez 100, Toluca, Estado de Mexico, Mexico",
                "Avenida Juarez 100", "Toluca", "Estado de Mexico", "50000", "Mexico"));
    }

    @Test
    void respaldaLaCiudadConDistritoCuandoFaltaCity() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        String geojson = """
                {"features":[
                  {"properties":{
                    "name":"Plaza Principal","district":"Centro","state":"Jalisco",
                    "country":"Mexico"}}
                ]}""";
        HttpResponse<String> ok = respuesta(200, geojson);
        when(cliente.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(ok);

        ServicioGeocoding servicio = new ServicioGeocoding(objectMapper, cliente);
        List<DireccionSugeridaDto> sugerencias = servicio.buscar("Centro");

        assertThat(sugerencias).containsExactly(new DireccionSugeridaDto(
                "Plaza Principal, Centro, Jalisco, Mexico",
                "Plaza Principal", "Centro", "Jalisco", "", "Mexico"));
    }

    @Test
    void anteErrorDelProveedorDevuelveListaVaciaSinLanzar() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        when(cliente.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("timeout simulado"));

        ServicioGeocoding servicio = new ServicioGeocoding(objectMapper, cliente);

        assertThat(servicio.buscar("Toluca")).isEmpty();
    }

    @Test
    void terminoCortoNoConsultaElCliente() throws Exception {
        HttpClient cliente = mock(HttpClient.class);

        ServicioGeocoding servicio = new ServicioGeocoding(objectMapper, cliente);
        List<DireccionSugeridaDto> sugerencias = servicio.buscar("to");

        assertThat(sugerencias).isEmpty();
        verify(cliente, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void cuandoPhotonVieneVacioCaeAlRespaldoNominatim() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        String nominatim = """
                [
                  {"display_name":"Avenida Juarez 100, Toluca, Estado de Mexico, Mexico",
                   "address":{
                     "road":"Avenida Juarez","house_number":"100","city":"Toluca",
                     "state":"Estado de Mexico","postcode":"50000","country":"Mexico"}}
                ]""";
        // Primera llamada (Photon) -> features vacias; segunda (Nominatim) -> resultado.
        HttpResponse<String> photonVacio = respuesta(200, "{\"features\":[]}");
        HttpResponse<String> nominatimOk = respuesta(200, nominatim);
        when(cliente.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(photonVacio)
                .thenReturn(nominatimOk);

        ServicioGeocoding servicio = new ServicioGeocoding(objectMapper, cliente);
        List<DireccionSugeridaDto> sugerencias = servicio.buscar("Avenida Juarez 100 Toluca");

        assertThat(sugerencias).containsExactly(new DireccionSugeridaDto(
                "Avenida Juarez 100, Toluca, Estado de Mexico, Mexico",
                "Avenida Juarez 100", "Toluca", "Estado de Mexico", "50000", "Mexico"));
        verify(cliente, org.mockito.Mockito.times(2))
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
