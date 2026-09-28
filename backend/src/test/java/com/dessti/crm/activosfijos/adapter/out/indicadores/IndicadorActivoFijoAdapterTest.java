package com.dessti.crm.activosfijos.adapter.out.indicadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.activosfijos.adapter.out.persistence.ActivoFijoRepository;
import com.dessti.crm.activosfijos.domain.EstadoActivoFijo;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Pruebas unitarias de {@link IndicadorActivoFijoAdapter} (Req 22.1, 44). Con un doble del
 * repositorio (sin base de datos) verifican el area, el valor neto en libros
 * (costo - depreciacion) y que las sumas nulas se tratan como cero.
 */
class IndicadorActivoFijoAdapterTest {

    private ActivoFijoRepository repo;
    private IndicadorActivoFijoAdapter adaptador;

    @BeforeEach
    void setUp() {
        repo = mock(ActivoFijoRepository.class);
        adaptador = new IndicadorActivoFijoAdapter(repo);
    }

    private ValorIndicador clave(IndicadoresArea area, String clave) {
        return area.indicadores().stream()
                .filter(v -> v.clave().equals(clave))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("Emite el area ACTIVO_FIJO con valor neto = costo - depreciacion")
    void valorNetoEnLibros() {
        when(repo.sumarCostoActivos()).thenReturn(new BigDecimal("1000000.00"));
        when(repo.sumarDepreciacionAcumulada()).thenReturn(new BigDecimal("250000.00"));
        when(repo.countByEstado(EstadoActivoFijo.ACTIVO)).thenReturn(12L);
        when(repo.countByEstado(EstadoActivoFijo.BAJA)).thenReturn(3L);

        IndicadoresArea area = adaptador.agregar(FiltroIndicadores.deTablero(null, null, null));

        assertThat(area.area()).isEqualTo(AreaIndicador.ACTIVO_FIJO);
        assertThat(clave(area, "activos_valor_neto_libros").valor()).isEqualByComparingTo("750000.00");
        assertThat(clave(area, "activos_costo_total").valor()).isEqualByComparingTo("1000000.00");
        assertThat(clave(area, "activos_depreciacion_acumulada").valor()).isEqualByComparingTo("250000.00");
        assertThat(clave(area, "activos_vigentes").valor()).isEqualByComparingTo("12");
        assertThat(clave(area, "activos_baja").valor()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("Sumas nulas del repositorio se tratan como cero")
    void sumasNulasComoCero() {
        when(repo.sumarCostoActivos()).thenReturn(null);
        when(repo.sumarDepreciacionAcumulada()).thenReturn(null);
        when(repo.countByEstado(EstadoActivoFijo.ACTIVO)).thenReturn(0L);
        when(repo.countByEstado(EstadoActivoFijo.BAJA)).thenReturn(0L);

        IndicadoresArea area = adaptador.agregar(FiltroIndicadores.deTablero(null, null, null));

        assertThat(clave(area, "activos_valor_neto_libros").valor()).isEqualByComparingTo("0");
    }
}
