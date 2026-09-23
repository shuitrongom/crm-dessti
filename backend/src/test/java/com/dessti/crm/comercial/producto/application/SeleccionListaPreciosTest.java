package com.dessti.crm.comercial.producto.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository;
import com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository.PrecioVigente;
import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort.ConsultaSugerenciaPrecio;
import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias basadas en ejemplos de la seleccion de {@code Lista_Precios}
 * por prioridad y segmento (tarea 16.3, Req 59.4, 59.9).
 *
 * <p>Complementan a {@link ServicioSeleccionPrecioTest} (tarea 16.1) centrandose
 * en los <em>escenarios de seleccion entre varias listas vigentes aplicables a un
 * Cliente</em>: mayor prioridad entre listas generales, preferencia del segmento
 * especifico sobre la general aun con menor prioridad, desempate por prioridad
 * dentro del segmento, ausencia de segmento del Cliente, <em>fallback</em> cuando
 * ningun segmento coincide, coincidencia de segmento sin distinguir mayusculas,
 * conjunto vacio de candidatas y el manejo de entradas nulas.</p>
 *
 * <p>Usan dobles de Mockito sobre {@link PrecioProductoRepository}; no cargan el
 * contexto de Spring ni base de datos. Las instancias de {@link PrecioVigente} se
 * construyen directamente por ser un {@code record}.</p>
 */
class SeleccionListaPreciosTest {

    private static final UUID PRODUCTO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDate FECHA = LocalDate.of(2024, 8, 1);

    private PrecioProductoRepository precioProductoRepository;
    private ServicioSeleccionPrecio servicio;

    @BeforeEach
    void setUp() {
        precioProductoRepository = mock(PrecioProductoRepository.class);
        servicio = new ServicioSeleccionPrecio(precioProductoRepository);
    }

    @Nested
    @DisplayName("Seleccion por prioridad entre listas generales")
    class Prioridad {

        @Test
        @DisplayName("con varias listas generales vigentes gana el precio de mayor prioridad (Req 59.9)")
        void variasGeneralesGanaMayorPrioridad() {
            when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                    .thenReturn(List.of(
                            new PrecioVigente(new BigDecimal("100.00"), 1, null),
                            new PrecioVigente(new BigDecimal("95.00"), 10, null),
                            new PrecioVigente(new BigDecimal("98.00"), 4, null)));

            Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                    new ConsultaSugerenciaPrecio(PRODUCTO, null, FECHA));

            assertThat(precio).contains(new BigDecimal("95.00"));
        }
    }

    @Nested
    @DisplayName("Preferencia del segmento especifico sobre la general")
    class Segmento {

        @Test
        @DisplayName("la lista de segmento del Cliente gana pese a tener MENOR prioridad que la general (Req 59.9)")
        void segmentoVenceAGeneralConMenorPrioridad() {
            when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                    .thenReturn(List.of(
                            new PrecioVigente(new BigDecimal("90.00"), 99, null),         // general, prioridad muy alta
                            new PrecioVigente(new BigDecimal("72.50"), 1, "mayoreo")));    // segmento, prioridad minima

            Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                    new ConsultaSugerenciaPrecio(PRODUCTO, "mayoreo", FECHA));

            assertThat(precio).contains(new BigDecimal("72.50"));
        }

        @Test
        @DisplayName("dentro del grupo de segmento gana la de mayor prioridad e ignora las generales de mayor prioridad (Req 59.9)")
        void dentroDelSegmentoGanaMayorPrioridad() {
            when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                    .thenReturn(List.of(
                            new PrecioVigente(new BigDecimal("70.00"), 2, "mayoreo"),
                            new PrecioVigente(new BigDecimal("65.00"), 7, "mayoreo"),
                            new PrecioVigente(new BigDecimal("40.00"), 50, null)));       // general de mayor prioridad, descartada

            Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                    new ConsultaSugerenciaPrecio(PRODUCTO, "mayoreo", FECHA));

            assertThat(precio).contains(new BigDecimal("65.00"));
        }

        @Test
        @DisplayName("la coincidencia de segmento no distingue mayusculas: Cliente 'Mayoreo' casa con lista 'mayoreo' (Req 59.9)")
        void coincidenciaDeSegmentoSinDistinguirMayusculas() {
            when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                    .thenReturn(List.of(
                            new PrecioVigente(new BigDecimal("100.00"), 20, null),
                            new PrecioVigente(new BigDecimal("60.00"), 3, "mayoreo")));

            Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                    new ConsultaSugerenciaPrecio(PRODUCTO, "Mayoreo", FECHA));

            assertThat(precio).contains(new BigDecimal("60.00"));
        }
    }

    @Nested
    @DisplayName("Fallback y ausencia de segmento")
    class Fallback {

        @Test
        @DisplayName("sin segmento del Cliente solo cuenta la prioridad entre todas las candidatas (Req 59.9)")
        void sinSegmentoSoloPrioridad() {
            when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                    .thenReturn(List.of(
                            new PrecioVigente(new BigDecimal("130.00"), 5, "mayoreo"),
                            new PrecioVigente(new BigDecimal("125.00"), 8, null),
                            new PrecioVigente(new BigDecimal("140.00"), 2, "gobierno")));

            Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                    new ConsultaSugerenciaPrecio(PRODUCTO, "  ", FECHA)); // blanco = general

            assertThat(precio).contains(new BigDecimal("125.00"));
        }

        @Test
        @DisplayName("con segmento del Cliente pero SIN lista que lo cubra, se usan todas las candidatas por prioridad (Req 59.9)")
        void segmentoSinCoincidenciaCaeATodas() {
            when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                    .thenReturn(List.of(
                            new PrecioVigente(new BigDecimal("200.00"), 4, null),
                            new PrecioVigente(new BigDecimal("180.00"), 9, "gobierno"),   // otro segmento, no coincide
                            new PrecioVigente(new BigDecimal("190.00"), 6, null)));

            Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                    new ConsultaSugerenciaPrecio(PRODUCTO, "mayoreo", FECHA)); // no hay lista 'mayoreo'

            assertThat(precio).contains(new BigDecimal("180.00"));
        }

        @Test
        @DisplayName("sin listas vigentes candidatas no se sugiere precio (Req 59.4)")
        void sinCandidatasVacio() {
            when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                    .thenReturn(List.of());

            Optional<BigDecimal> precio = servicio.sugerirPrecioUnitario(
                    new ConsultaSugerenciaPrecio(PRODUCTO, "mayoreo", FECHA));

            assertThat(precio).isEmpty();
        }
    }

    @Nested
    @DisplayName("Manejo de entradas y fecha por defecto")
    class Entradas {

        @Test
        @DisplayName("consulta nula lanza ReglaNegocioException (Req 59.4)")
        void consultaNulaFalla() {
            assertThatThrownBy(() -> servicio.sugerirPrecioUnitario(null))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("productoId nulo lanza ReglaNegocioException (Req 59.4)")
        void productoIdNuloFalla() {
            assertThatThrownBy(() -> servicio.sugerirPrecioUnitario(
                    new ConsultaSugerenciaPrecio(null, "mayoreo", FECHA)))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("fecha nula consulta el repositorio con la fecha de hoy (Req 59.4)")
        void fechaNulaUsaHoy() {
            when(precioProductoRepository.buscarPreciosVigentes(eq(PRODUCTO), any(LocalDate.class)))
                    .thenReturn(List.of(new PrecioVigente(new BigDecimal("50.00"), 1, null)));

            servicio.sugerirPrecioUnitario(new ConsultaSugerenciaPrecio(PRODUCTO, null, null));

            ArgumentCaptor<LocalDate> fechaCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(precioProductoRepository).buscarPreciosVigentes(eq(PRODUCTO), fechaCaptor.capture());
            assertThat(fechaCaptor.getValue()).isEqualTo(LocalDate.now());
        }
    }
}
