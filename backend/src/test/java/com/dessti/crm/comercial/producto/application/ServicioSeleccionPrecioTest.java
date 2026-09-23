package com.dessti.crm.comercial.producto.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository;
import com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository.PrecioVigente;
import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort.ConsultaSugerenciaPrecio;

/**
 * Pruebas unitarias de {@link ServicioSeleccionPrecio} (Req 59.4, 59.9). Usan
 * dobles de Mockito. Cubren la regla de seleccion: mayor prioridad gana, la
 * lista de segmento especifico se prefiere sobre la general, las listas no
 * vigentes se ignoran (el repositorio ya las excluye) y sin candidatas no se
 * sugiere precio. La tarea 16.3 amplia estos ejemplos.
 */
class ServicioSeleccionPrecioTest {

    private static final UUID PRODUCTO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate HOY = LocalDate.of(2024, 6, 15);

    private PrecioProductoRepository precioProductoRepository;
    private ServicioSeleccionPrecio servicio;

    @BeforeEach
    void setUp() {
        precioProductoRepository = mock(PrecioProductoRepository.class);
        servicio = new ServicioSeleccionPrecio(precioProductoRepository);
    }

    @Test
    @DisplayName("entre varias listas vigentes gana la de mayor prioridad (Req 59.9)")
    void mayorPrioridadGana() {
        when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                .thenReturn(List.of(
                        new PrecioVigente(new BigDecimal("100.00"), 1, null),
                        new PrecioVigente(new BigDecimal("90.00"), 5, null)));

        Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                new ConsultaSugerenciaPrecio(PRODUCTO, null, HOY));

        assertThat(precio).contains(new BigDecimal("90.00"));
    }

    @Test
    @DisplayName("la lista especifica del segmento se prefiere sobre la general aunque tenga menor prioridad (Req 59.9)")
    void segmentoEspecificoVenceAGeneral() {
        when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                .thenReturn(List.of(
                        new PrecioVigente(new BigDecimal("80.00"), 9, null),        // general, prioridad alta
                        new PrecioVigente(new BigDecimal("70.00"), 2, "mayoristas"))); // segmento, prioridad baja

        Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                new ConsultaSugerenciaPrecio(PRODUCTO, "mayoristas", HOY));

        assertThat(precio).contains(new BigDecimal("70.00"));
    }

    @Test
    @DisplayName("con varias listas del mismo segmento gana la de mayor prioridad dentro del segmento (Req 59.9)")
    void dentroDelSegmentoGanaMayorPrioridad() {
        when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                .thenReturn(List.of(
                        new PrecioVigente(new BigDecimal("75.00"), 3, "mayoristas"),
                        new PrecioVigente(new BigDecimal("60.00"), 8, "mayoristas"),
                        new PrecioVigente(new BigDecimal("50.00"), 10, null)));

        Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                new ConsultaSugerenciaPrecio(PRODUCTO, "MAYORISTAS", HOY));

        assertThat(precio).contains(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("sin segmento del Cliente se aplica la lista general de mayor prioridad (Req 59.9)")
    void sinSegmentoUsaGeneralDeMayorPrioridad() {
        when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                .thenReturn(List.of(
                        new PrecioVigente(new BigDecimal("120.00"), 7, null),
                        new PrecioVigente(new BigDecimal("110.00"), 3, "mayoristas")));

        Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                new ConsultaSugerenciaPrecio(PRODUCTO, null, HOY));

        assertThat(precio).contains(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("sin listas vigentes aplicables no se sugiere precio (Req 59.4)")
    void sinCandidatasVacio() {
        when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                .thenReturn(List.of());

        Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                new ConsultaSugerenciaPrecio(PRODUCTO, "mayoristas", HOY));

        assertThat(precio).isEmpty();
    }
}
