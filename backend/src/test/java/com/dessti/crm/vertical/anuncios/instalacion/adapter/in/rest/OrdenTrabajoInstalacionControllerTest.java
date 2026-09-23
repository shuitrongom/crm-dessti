package com.dessti.crm.vertical.anuncios.instalacion.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;
import com.dessti.crm.vertical.anuncios.instalacion.application.EvidenciaInstalacionDto;
import com.dessti.crm.vertical.anuncios.instalacion.application.OrdenTrabajoInstalacionDetalleDto;
import com.dessti.crm.vertical.anuncios.instalacion.application.OrdenTrabajoInstalacionDto;
import com.dessti.crm.vertical.anuncios.instalacion.application.PendienteInstalacionDto;
import com.dessti.crm.vertical.anuncios.instalacion.application.ServicioOrdenesTrabajoInstalacion;

/**
 * Pruebas de rebanada (slice) del {@link OrdenTrabajoInstalacionController} (tarea
 * 6.5; Req 8.1, 8.2, 8.3, 8.4, 8.6, 14.1). Reproduce el contrato REST real sin BD:
 * {@link ServicioOrdenesTrabajoInstalacion} se simula y el evaluador RBAC
 * {@code @autorizador} se sustituye por un doble para evaluar realmente las
 * expresiones {@code @PreAuthorize}. Sigue exactamente el patron de
 * {@code OrdenFabricacionControllerTest} (slice {@code @WebMvcTest} con
 * {@link MethodSecurityConfig}, {@link ManejadorGlobalErrores} y el doble del
 * {@code @autorizador}).
 *
 * <p>El gating de estos endpoints es de <strong>anuncios</strong>
 * ({@code moduloHabilitado('operacion') and giroCorresponde('operacion')}), por lo
 * que el camino feliz stubbea ambos por defecto (ver {@link #permitirGatingPorDefecto()}).</p>
 */
@WebMvcTest(controllers = OrdenTrabajoInstalacionController.class,
        properties = {
                "DB_USER=test",
                "DB_PASSWORD=test",
                "JWT_SIGNING_KEY=clave-de-firma-jwt-solo-para-pruebas-0123456789",
                "CRM_ENC_KEY_ACTIVE=v1",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "crm.secretos.jwt-signing-key=clave-de-firma-jwt-solo-para-pruebas-0123456789"
        })
@Import({MethodSecurityConfig.class, ManejadorGlobalErrores.class,
        OrdenTrabajoInstalacionControllerTest.ConfiguracionPrueba.class})
class OrdenTrabajoInstalacionControllerTest {

    private static final UUID ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID ORDEN_FABRICACION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SITIO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CUADRILLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CLIENTE = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PENDIENTE = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID EVIDENCIA = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioOrdenesTrabajoInstalacion servicioOrdenesTrabajoInstalacion;

    @Autowired
    private Autorizador autorizador;

    private static OrdenTrabajoInstalacionDto otiDto(String estado) {
        Instant ahora = Instant.parse("2025-01-15T12:00:00Z");
        return new OrdenTrabajoInstalacionDto(
                ID, ORDEN_FABRICACION, SITIO, CUADRILLA, CLIENTE,
                LocalDate.of(2025, 1, 20), estado, 0L, ahora, ahora);
    }

    private static PendienteInstalacionDto pendienteDto(boolean resuelto, String descripcion) {
        Instant ahora = Instant.parse("2025-01-15T12:00:00Z");
        return new PendienteInstalacionDto(PENDIENTE, ID, descripcion, resuelto, 0L, ahora, ahora);
    }

    private static EvidenciaInstalacionDto evidenciaDto() {
        Instant ahora = Instant.parse("2025-01-15T12:00:00Z");
        return new EvidenciaInstalacionDto(EVIDENCIA, ID, "fotos/instalacion-1.jpg", 0L, ahora, ahora);
    }

    private static OrdenTrabajoInstalacionDetalleDto detalleDto(
            String estado, List<PendienteInstalacionDto> pendientes,
            List<EvidenciaInstalacionDto> evidencias) {
        Instant ahora = Instant.parse("2025-01-15T12:00:00Z");
        return new OrdenTrabajoInstalacionDetalleDto(
                ID, ORDEN_FABRICACION, SITIO, CUADRILLA, CLIENTE,
                LocalDate.of(2025, 1, 20), estado, 0L, ahora, ahora, pendientes, evidencias);
    }

    @BeforeEach
    void permitirGatingPorDefecto() {
        // Gating de anuncios: modulo + giro se satisfacen por defecto para no romper
        // el camino feliz; cada test controla la autorizacion fina via tiene(...).
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
        org.mockito.Mockito.lenient().when(autorizador.giroCorresponde(anyString())).thenReturn(true);
    }

    // ------------------------------------------------------------------
    // Consulta de detalle con pendientes/evidencias (Req 8.1, 8.2, 8.3)
    // ------------------------------------------------------------------

    @Test
    void consultar_devuelve200_conPendientesYEvidencias() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "leer")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.consultarDetalle(eq(ID)))
                .thenReturn(detalleDto("en_curso",
                        List.of(pendienteDto(false, "fijar anclas")),
                        List.of(evidenciaDto())));

        mockMvc.perform(get("/ordenes-trabajo-instalacion/{id}", ID).with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.estado").value("en_curso"))
                .andExpect(jsonPath("$.pendientes").isArray())
                .andExpect(jsonPath("$.pendientes[0].descripcion").value("fijar anclas"))
                .andExpect(jsonPath("$.pendientes[0].resuelto").value(false))
                .andExpect(jsonPath("$.evidencias").isArray())
                .andExpect(jsonPath("$.evidencias[0].url").value("fotos/instalacion-1.jpg"));
    }

    @Test
    void consultar_devuelve200_conListasVacias_cuandoNoHayElementos() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "leer")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.consultarDetalle(eq(ID)))
                .thenReturn(detalleDto("programada", List.of(), List.of()));

        mockMvc.perform(get("/ordenes-trabajo-instalacion/{id}", ID).with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendientes").isArray())
                .andExpect(jsonPath("$.pendientes").isEmpty())
                .andExpect(jsonPath("$.evidencias").isArray())
                .andExpect(jsonPath("$.evidencias").isEmpty());
    }

    @Test
    void consultar_devuelve404_cuandoOtiNoAccesible() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "leer")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.consultarDetalle(eq(ID)))
                .thenThrow(new RecursoNoEncontradoException(
                        "No se encontro la Orden_Trabajo_Instalacion solicitada."));

        mockMvc.perform(get("/ordenes-trabajo-instalacion/{id}", ID).with(user("instalacion")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Endpoints dedicados de pendientes y evidencias (Req 8.2, 8.3, 8.6)
    // ------------------------------------------------------------------

    @Test
    void pendientes_devuelve200_conLista() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "leer")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.pendientesDe(eq(ID)))
                .thenReturn(List.of(pendienteDto(false, "fijar anclas"),
                        pendienteDto(true, "conectar acometida")));

        mockMvc.perform(get("/ordenes-trabajo-instalacion/{id}/pendientes", ID)
                        .with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].descripcion").value("fijar anclas"))
                .andExpect(jsonPath("$[0].resuelto").value(false))
                .andExpect(jsonPath("$[1].resuelto").value(true));
    }

    @Test
    void pendientes_devuelve200_conListaVacia() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "leer")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.pendientesDe(eq(ID))).thenReturn(List.of());

        mockMvc.perform(get("/ordenes-trabajo-instalacion/{id}/pendientes", ID)
                        .with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void pendientes_devuelve404_cuandoOtiNoAccesible() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "leer")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.pendientesDe(eq(ID)))
                .thenThrow(new RecursoNoEncontradoException(
                        "No se encontro la Orden_Trabajo_Instalacion solicitada."));

        mockMvc.perform(get("/ordenes-trabajo-instalacion/{id}/pendientes", ID)
                        .with(user("instalacion")))
                .andExpect(status().isNotFound());
    }

    @Test
    void evidencias_devuelve200_conLista() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "leer")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.evidenciasDe(eq(ID)))
                .thenReturn(List.of(evidenciaDto()));

        mockMvc.perform(get("/ordenes-trabajo-instalacion/{id}/evidencias", ID)
                        .with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].url").value("fotos/instalacion-1.jpg"));
    }

    @Test
    void evidencias_devuelve200_conListaVacia() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "leer")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.evidenciasDe(eq(ID))).thenReturn(List.of());

        mockMvc.perform(get("/ordenes-trabajo-instalacion/{id}/evidencias", ID)
                        .with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ------------------------------------------------------------------
    // Guarda de cierre (Req 8.4): 422 informativo y 409 transicion invalida
    // ------------------------------------------------------------------

    @Test
    void cambiarEstado_devuelve422_conMensajeQueEnumeraLosPendientes() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "cambiar_estado")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.cambiarEstado(eq(ID), eq("completada")))
                .thenThrow(new ReglaNegocioException(
                        "no se puede completar: pendientes por resolver: [«fijar anclas», «conectar acometida»]"));

        mockMvc.perform(put("/ordenes-trabajo-instalacion/{id}/estado", ID)
                        .contentType("application/json")
                        .content("{\"estado\":\"completada\"}")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cambiarEstado_devuelve409_cuandoTransicionInvalida() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "cambiar_estado")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.cambiarEstado(eq(ID), anyString()))
                .thenThrow(new TransicionInvalidaException("Transicion de estado invalida."));

        mockMvc.perform(put("/ordenes-trabajo-instalacion/{id}/estado", ID)
                        .contentType("application/json")
                        .content("{\"estado\":\"completada\"}")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void cambiarEstado_devuelve200_conNuevoEstado() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "cambiar_estado")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.cambiarEstado(eq(ID), eq("completada")))
                .thenReturn(otiDto("completada"));

        mockMvc.perform(put("/ordenes-trabajo-instalacion/{id}/estado", ID)
                        .contentType("application/json")
                        .content("{\"estado\":\"completada\"}")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("completada"));
    }

    // ------------------------------------------------------------------
    // Resolucion de un pendiente via registro de avance (Req 8.2)
    // ------------------------------------------------------------------

    @Test
    void registrarAvance_devuelve200_alResolverPendiente() throws Exception {
        when(autorizador.tiene("orden_trabajo_instalacion", "cambiar_estado")).thenReturn(true);
        when(servicioOrdenesTrabajoInstalacion.registrarAvance(eq(ID), any()))
                .thenReturn(otiDto("en_curso"));

        mockMvc.perform(post("/ordenes-trabajo-instalacion/{id}/avance", ID)
                        .contentType("application/json")
                        .content("{\"pendientesResueltos\":[\"" + PENDIENTE + "\"]}")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    // ------------------------------------------------------------------
    // Autorizacion: falta de permiso => 403
    // ------------------------------------------------------------------

    @Test
    void consultar_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/ordenes-trabajo-instalacion/{id}", ID).with(user("sin_permiso")))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
