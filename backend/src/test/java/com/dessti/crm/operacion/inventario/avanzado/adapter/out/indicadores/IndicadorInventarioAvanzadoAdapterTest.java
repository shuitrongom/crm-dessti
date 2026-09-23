package com.dessti.crm.operacion.inventario.avanzado.adapter.out.indicadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.ExistenciaAlmacenRepository;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Pruebas unitarias deterministas de {@link IndicadorInventarioAvanzadoAdapter} (Req 22.1)
 * con un doble Mockito del repositorio: verifican que la valuacion total se mapea a un
 * indicador monetario (MXN) y el numero de almacenes a un conteo, sin arrancar Spring ni
 * base de datos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IndicadorInventarioAvanzadoAdapter - valuacion y existencias (Req 22.1)")
class IndicadorInventarioAvanzadoAdapterTest {

    @Mock
    private ExistenciaAlmacenRepository existenciaAlmacenRepository;

    @Test
    @DisplayName("mapea valuacion total (MXN) y conteo de almacenes con existencias")
    void mapeaValuacionYAlmacenes() {
        when(existenciaAlmacenRepository.sumarValuacionTotal()).thenReturn(new BigDecimal("128000.5000"));
        when(existenciaAlmacenRepository.contarAlmacenesConExistencias()).thenReturn(3L);

        IndicadorInventarioAvanzadoAdapter adapter =
                new IndicadorInventarioAvanzadoAdapter(existenciaAlmacenRepository);

        IndicadoresArea resultado = adapter.agregar(
                FiltroIndicadores.deTablero(null, null, null));

        assertThat(resultado.area()).isEqualTo(AreaIndicador.INVENTARIO_AVANZADO);
        Map<String, ValorIndicador> porClave = indexar(resultado);

        ValorIndicador valuacion = porClave.get("valuacion_inventario_total");
        assertThat(valuacion.valor()).isEqualByComparingTo("128000.5000");
        assertThat(valuacion.unidad()).isEqualTo("MXN");

        ValorIndicador almacenes = porClave.get("almacenes_con_existencias");
        assertThat(almacenes.valor()).isEqualByComparingTo("3");
        assertThat(almacenes.unidad()).isEqualTo("conteo");
    }

    @Test
    @DisplayName("valuacion nula se normaliza a cero")
    void valuacionNulaEsCero() {
        when(existenciaAlmacenRepository.sumarValuacionTotal()).thenReturn(null);
        when(existenciaAlmacenRepository.contarAlmacenesConExistencias()).thenReturn(0L);

        IndicadorInventarioAvanzadoAdapter adapter =
                new IndicadorInventarioAvanzadoAdapter(existenciaAlmacenRepository);

        IndicadoresArea resultado = adapter.agregar(
                FiltroIndicadores.deTablero(null, null, null));

        assertThat(indexar(resultado).get("valuacion_inventario_total").valor())
                .isEqualByComparingTo("0");
    }

    private static Map<String, ValorIndicador> indexar(IndicadoresArea area) {
        return area.indicadores().stream()
                .collect(Collectors.toMap(ValorIndicador::clave, v -> v));
    }
}
