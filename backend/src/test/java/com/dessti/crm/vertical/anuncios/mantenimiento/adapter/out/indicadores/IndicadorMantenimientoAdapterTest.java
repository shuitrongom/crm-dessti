package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.out.indicadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dessti.crm.vertical.anuncios.mantenimiento.adapter.out.persistence.TicketServicioRepository;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Pruebas unitarias deterministas de {@link IndicadorMantenimientoAdapter} (Req 22.1) con
 * un doble Mockito del repositorio: verifican el mapeo de los conteos de cumplimiento del
 * SLA a las claves esperadas y el calculo del porcentaje de cumplimiento (half-up), sin
 * arrancar Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IndicadorMantenimientoAdapter - cumplimiento de SLA (Req 22.1)")
class IndicadorMantenimientoAdapterTest {

    @Mock
    private TicketServicioRepository ticketServicioRepository;

    @Test
    @DisplayName("calcula porcentaje de cumplimiento del SLA de resolucion y respuesta")
    void calculaCumplimientoSla() {
        // Resolucion: 3 cumplidos, 1 incumplido => 75.00%
        when(ticketServicioRepository.contarPorCumplimientoSlaResolucion(eq(true), any(), any()))
                .thenReturn(3L);
        when(ticketServicioRepository.contarPorCumplimientoSlaResolucion(eq(false), any(), any()))
                .thenReturn(1L);
        // Respuesta: 1 cumplido, 1 incumplido => 50.00%
        when(ticketServicioRepository.contarPorCumplimientoSlaRespuesta(eq(true), any(), any()))
                .thenReturn(1L);
        when(ticketServicioRepository.contarPorCumplimientoSlaRespuesta(eq(false), any(), any()))
                .thenReturn(1L);

        IndicadorMantenimientoAdapter adapter =
                new IndicadorMantenimientoAdapter(ticketServicioRepository);

        IndicadoresArea resultado = adapter.agregar(
                FiltroIndicadores.deTablero(null, null, null));

        assertThat(resultado.area()).isEqualTo(AreaIndicador.MANTENIMIENTO);
        Map<String, ValorIndicador> porClave = indexar(resultado);

        assertThat(porClave.get("tickets_sla_resolucion_cumplido").valor()).isEqualByComparingTo("3");
        assertThat(porClave.get("tickets_sla_resolucion_incumplido").valor()).isEqualByComparingTo("1");
        ValorIndicador cumplimientoResolucion = porClave.get("cumplimiento_sla_resolucion");
        assertThat(cumplimientoResolucion.valor()).isEqualByComparingTo("75.00");
        assertThat(cumplimientoResolucion.unidad()).isEqualTo("porcentaje");

        assertThat(porClave.get("cumplimiento_sla_respuesta").valor()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("sin tickets resueltos el cumplimiento es 0 (evita division por cero)")
    void sinTicketsCumplimientoCero() {
        when(ticketServicioRepository.contarPorCumplimientoSlaResolucion(any(Boolean.class), any(), any()))
                .thenReturn(0L);
        when(ticketServicioRepository.contarPorCumplimientoSlaRespuesta(any(Boolean.class), any(), any()))
                .thenReturn(0L);

        IndicadorMantenimientoAdapter adapter =
                new IndicadorMantenimientoAdapter(ticketServicioRepository);

        IndicadoresArea resultado = adapter.agregar(
                FiltroIndicadores.deTablero(null, null, null));

        assertThat(indexar(resultado).get("cumplimiento_sla_resolucion").valor())
                .isEqualByComparingTo("0");
    }

    private static Map<String, ValorIndicador> indexar(IndicadoresArea area) {
        return area.indicadores().stream()
                .collect(Collectors.toMap(ValorIndicador::clave, v -> v));
    }
}
