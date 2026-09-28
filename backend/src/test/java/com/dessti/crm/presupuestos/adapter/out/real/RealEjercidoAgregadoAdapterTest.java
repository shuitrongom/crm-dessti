package com.dessti.crm.presupuestos.adapter.out.real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.compras.ordencompra.adapter.out.persistence.OrdenCompraRepository;
import com.dessti.crm.facturacion.factura.adapter.out.persistence.FacturaRepository;
import com.dessti.crm.rhnomina.nomina.adapter.out.persistence.ReciboNominaRepository;

/**
 * Pruebas unitarias de {@link RealEjercidoAgregadoAdapter} (Req 62.8). Con dobles de los
 * repositorios de facturacion, compras y nomina (sin base de datos) verifican que:
 * ingresos reales = facturacion timbrada; egresos reales = compras + costo de nomina;
 * y que un periodo con formato invalido degrada a cero de forma segura.
 */
class RealEjercidoAgregadoAdapterTest {

    private FacturaRepository facturas;
    private OrdenCompraRepository ordenes;
    private ReciboNominaRepository recibos;
    private RealEjercidoAgregadoAdapter adaptador;

    @BeforeEach
    void setUp() {
        facturas = mock(FacturaRepository.class);
        ordenes = mock(OrdenCompraRepository.class);
        recibos = mock(ReciboNominaRepository.class);
        adaptador = new RealEjercidoAgregadoAdapter(facturas, ordenes, recibos);
    }

    @Test
    @DisplayName("Ingresos reales = facturacion timbrada del periodo")
    void ingresosEsFacturacionTimbrada() {
        when(facturas.sumarFacturacionTimbrada(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("125000.00"));

        BigDecimal ingresos = adaptador.realIngresos("COMERCIAL", "2026-03");

        assertThat(ingresos).isEqualByComparingTo("125000.00");
    }

    @Test
    @DisplayName("Egresos reales = compras no canceladas + costo de nomina del periodo")
    void egresosEsComprasMasNomina() {
        when(ordenes.sumarComprasPeriodo(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("40000.00"));
        when(recibos.sumarCostoNomina(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("60000.00"));

        BigDecimal egresos = adaptador.realEgresos("OPERACION", "2026-03");

        assertThat(egresos).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("Sumas nulas de los repos se tratan como cero")
    void sumasNulasComoCero() {
        when(facturas.sumarFacturacionTimbrada(any(), any())).thenReturn(null);
        when(ordenes.sumarComprasPeriodo(any(), any())).thenReturn(null);
        when(recibos.sumarCostoNomina(any(), any())).thenReturn(null);

        assertThat(adaptador.realIngresos("A", "2026-03")).isEqualByComparingTo("0.00");
        assertThat(adaptador.realEgresos("A", "2026-03")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Periodo con formato invalido devuelve cero sin consultar los repos")
    void periodoInvalidoDevuelveCero() {
        assertThat(adaptador.realIngresos("A", "no-es-periodo")).isEqualByComparingTo("0.00");
        assertThat(adaptador.realEgresos("A", null)).isEqualByComparingTo("0.00");
    }
}
