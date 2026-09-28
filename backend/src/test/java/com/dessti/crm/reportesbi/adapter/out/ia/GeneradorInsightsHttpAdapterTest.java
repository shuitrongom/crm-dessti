package com.dessti.crm.reportesbi.adapter.out.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.reportesbi.application.ia.IndicadorInsight;
import com.dessti.crm.reportesbi.application.ia.ResultadoInsights;
import com.dessti.crm.reportesbi.application.ia.SolicitudInsights;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pruebas unitarias del {@link GeneradorInsightsHttpAdapter} (suite BI+IA) con un
 * {@link HttpClient} simulado (Mockito), sin red real. Verifican la
 * <strong>degradacion gracil</strong> al heuristico y la interpretacion de la respuesta:
 * <ul>
 *   <li>sin configuracion no toca la red y usa el heuristico;</li>
 *   <li>una respuesta JSON valida produce insights con {@code generadoPorIa=true};</li>
 *   <li>un estado no 2xx, un fallo de red o un contenido vacio degradan al heuristico.</li>
 * </ul>
 */
class GeneradorInsightsHttpAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeneradorInsightsHeuristico heuristico = new GeneradorInsightsHeuristico();

    private static IaProperties configurado() {
        return new IaProperties("clave-secreta", "https://ia.example/v1/chat/completions",
                "modelo-x", Duration.ofSeconds(5));
    }

    private static IaProperties sinConfigurar() {
        return new IaProperties("", "", "", Duration.ofSeconds(5));
    }

    private static SolicitudInsights solicitudConDatos() {
        return new SolicitudInsights(null, null, null, List.of(
                new IndicadorInsight("COMERCIAL", "cotiz", "Cotizaciones",
                        new BigDecimal("12"), "conteo", new BigDecimal("8"), new BigDecimal("4"))));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> respuesta(int estado, String cuerpo) {
        HttpResponse<String> r = mock(HttpResponse.class);
        org.mockito.Mockito.lenient().when(r.statusCode()).thenReturn(estado);
        org.mockito.Mockito.lenient().when(r.body()).thenReturn(cuerpo);
        return r;
    }

    private static String choice(String contenido) {
        // Respuesta estilo chat completions con el contenido escapado como texto JSON.
        return "{\"choices\":[{\"message\":{\"content\":" + contenido + "}}]}";
    }

    @Test
    @DisplayName("Sin configuracion: no llama a la red y devuelve el heuristico")
    void sinConfiguracionUsaHeuristico() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        GeneradorInsightsHttpAdapter adaptador =
                new GeneradorInsightsHttpAdapter(sinConfigurar(), heuristico, objectMapper, cliente);

        ResultadoInsights r = adaptador.generar(solicitudConDatos());

        assertThat(r.generadoPorIa()).isFalse();
        verify(cliente, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("Respuesta JSON valida: insights generados por IA")
    void respuestaJsonValidaGeneraPorIa() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        String contenidoJson = "\"{\\\"resumen\\\":\\\"Buen periodo.\\\","
                + "\\\"hallazgos\\\":[\\\"Ventas al alza\\\"]}\"";
        // Se construye el doble ANTES del when(...) para no anidar stubbings (evita
        // el UnfinishedStubbingException de Mockito).
        HttpResponse<String> ok = respuesta(200, choice(contenidoJson));
        when(cliente.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(ok);

        GeneradorInsightsHttpAdapter adaptador =
                new GeneradorInsightsHttpAdapter(configurado(), heuristico, objectMapper, cliente);
        ResultadoInsights r = adaptador.generar(solicitudConDatos());

        assertThat(r.generadoPorIa()).isTrue();
        assertThat(r.resumenEjecutivo()).isEqualTo("Buen periodo.");
        assertThat(r.hallazgos()).containsExactly("Ventas al alza");
    }

    @Test
    @DisplayName("Contenido de texto libre: se usa como resumen, generado por IA")
    void textoLibreComoResumen() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        HttpResponse<String> ok =
                respuesta(200, choice("\"El periodo muestra crecimiento sostenido.\""));
        when(cliente.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(ok);

        GeneradorInsightsHttpAdapter adaptador =
                new GeneradorInsightsHttpAdapter(configurado(), heuristico, objectMapper, cliente);
        ResultadoInsights r = adaptador.generar(solicitudConDatos());

        assertThat(r.generadoPorIa()).isTrue();
        assertThat(r.resumenEjecutivo()).isEqualTo("El periodo muestra crecimiento sostenido.");
        assertThat(r.hallazgos()).isEmpty();
    }

    @Test
    @DisplayName("Estado no 2xx: degrada al heuristico")
    void estadoNo2xxDegrada() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        HttpResponse<String> error = respuesta(500, "error interno");
        when(cliente.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(error);

        GeneradorInsightsHttpAdapter adaptador =
                new GeneradorInsightsHttpAdapter(configurado(), heuristico, objectMapper, cliente);
        ResultadoInsights r = adaptador.generar(solicitudConDatos());

        assertThat(r.generadoPorIa()).isFalse();
        assertThat(r.resumenEjecutivo()).isNotBlank();
    }

    @Test
    @DisplayName("Fallo de red: degrada al heuristico sin lanzar")
    void falloDeRedDegrada() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        when(cliente.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("timeout simulado"));

        GeneradorInsightsHttpAdapter adaptador =
                new GeneradorInsightsHttpAdapter(configurado(), heuristico, objectMapper, cliente);
        ResultadoInsights r = adaptador.generar(solicitudConDatos());

        assertThat(r.generadoPorIa()).isFalse();
        assertThat(r.resumenEjecutivo()).isNotBlank();
    }

    @Test
    @DisplayName("Contenido vacio: degrada al heuristico")
    void contenidoVacioDegrada() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        HttpResponse<String> vacio = respuesta(200, choice("\"\""));
        when(cliente.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(vacio);

        GeneradorInsightsHttpAdapter adaptador =
                new GeneradorInsightsHttpAdapter(configurado(), heuristico, objectMapper, cliente);
        ResultadoInsights r = adaptador.generar(solicitudConDatos());

        assertThat(r.generadoPorIa()).isFalse();
    }

    @Test
    @DisplayName("Sin datos: usa el heuristico sin llamar a la red aunque este configurado")
    void sinDatosNoLlamaRed() throws Exception {
        HttpClient cliente = mock(HttpClient.class);
        GeneradorInsightsHttpAdapter adaptador =
                new GeneradorInsightsHttpAdapter(configurado(), heuristico, objectMapper, cliente);

        ResultadoInsights r = adaptador.generar(new SolicitudInsights(null, null, null, List.of()));

        assertThat(r.generadoPorIa()).isFalse();
        verify(cliente, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
