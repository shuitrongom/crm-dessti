package com.dessti.crm.rhnomina.nomina.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de las <strong>tablas fiscales de la nomina</strong> (tarea 35.3,
 * Req 41.2) con ejemplos <strong>verificados a mano</strong>. Cada aserto muestra la
 * aritmetica esperada en comentarios para que los montos sean auditables y
 * mantenibles cuando el SAT actualice las tarifas (vigente 2024/2026, revisar
 * anualmente).
 *
 * <p>Se ejercitan tanto las funciones de tabla individuales
 * ({@link TablasFiscalesNomina#isrMensual(BigDecimal)},
 * {@link TablasFiscalesNomina#subsidioAlEmpleoMensual(BigDecimal)},
 * {@link TablasFiscalesNomina#cuotaImssObrera(BigDecimal)},
 * {@link TablasFiscalesNomina#descuentoInfonavit(BigDecimal, BigDecimal)}) como el
 * calculo integral {@link CalculoNomina#calcular(EntradaNomina)} sobre casos
 * concretos.</p>
 */
class TablasFiscalesNominaTest {

    // ----------------------------------------------------------------------
    // ISR mensual (Art. 96 LISR) -- montos verificados a mano
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("ISR de una base en el tramo 6,332.06..11,128.01 (10.88%)")
    void isrEnTramoDiezPuntoOchoOcho() {
        // base = 7,500.00
        // tramo: limite_inferior=6,332.06, cuota_fija=371.83, %=10.88%
        // excedente = 7,500.00 - 6,332.06 = 1,167.94
        // marginal  = 1,167.94 * 0.1088 = 127.071872
        // ISR = round(371.83 + 127.071872, 2) = 498.90
        assertThat(TablasFiscalesNomina.isrMensual(new BigDecimal("7500.00")))
                .isEqualByComparingTo(new BigDecimal("498.90"));
    }

    @Test
    @DisplayName("ISR de una base en el tramo 746.05..6,332.05 (6.40%)")
    void isrEnTramoSeisPuntoCuarenta() {
        // base = 3,000.00
        // tramo: limite_inferior=746.05, cuota_fija=14.32, %=6.40%
        // excedente = 3,000.00 - 746.05 = 2,253.95
        // marginal  = 2,253.95 * 0.0640 = 144.2528
        // ISR = round(14.32 + 144.2528, 2) = 158.57
        assertThat(TablasFiscalesNomina.isrMensual(new BigDecimal("3000.00")))
                .isEqualByComparingTo(new BigDecimal("158.57"));
    }

    @Test
    @DisplayName("ISR de una base en el primer tramo (1.92%) es la parte marginal")
    void isrEnPrimerTramo() {
        // base = 500.00 (dentro de 0.01..746.04)
        // tramo: limite_inferior=0.01, cuota_fija=0.00, %=1.92%
        // excedente = 500.00 - 0.01 = 499.99
        // marginal  = 499.99 * 0.0192 = 9.599808
        // ISR = round(0.00 + 9.599808, 2) = 9.60
        assertThat(TablasFiscalesNomina.isrMensual(new BigDecimal("500.00")))
                .isEqualByComparingTo(new BigDecimal("9.60"));
    }

    // ----------------------------------------------------------------------
    // Subsidio al empleo mensual (tabla simplificada) -- verificado a mano
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("Subsidio al empleo aplica cuando el ingreso no excede el tope")
    void subsidioAplicaBajoElTope() {
        // ingreso = 3,000.00 <= 7,382.34 -> subsidio = 475.00
        assertThat(TablasFiscalesNomina.subsidioAlEmpleoMensual(new BigDecimal("3000.00")))
                .isEqualByComparingTo(new BigDecimal("475.00"));
    }

    @Test
    @DisplayName("Subsidio al empleo en el tope exacto sigue aplicando")
    void subsidioEnElTopeExacto() {
        // ingreso = 7,382.34 == tope -> subsidio = 475.00
        assertThat(TablasFiscalesNomina.subsidioAlEmpleoMensual(new BigDecimal("7382.34")))
                .isEqualByComparingTo(new BigDecimal("475.00"));
    }

    @Test
    @DisplayName("Subsidio al empleo es cero por encima del tope")
    void subsidioCeroSobreElTope() {
        // ingreso = 7,500.00 > 7,382.34 -> subsidio = 0.00
        assertThat(TablasFiscalesNomina.subsidioAlEmpleoMensual(new BigDecimal("7500.00")))
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    // ----------------------------------------------------------------------
    // Cuota obrera del IMSS (simplificada) e Infonavit -- verificado a mano
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("Cuota obrera del IMSS = base * 2.5%")
    void cuotaImssObrera() {
        // 7,500.00 * 0.025 = 187.50
        assertThat(TablasFiscalesNomina.cuotaImssObrera(new BigDecimal("7500.00")))
                .isEqualByComparingTo(new BigDecimal("187.50"));
    }

    @Test
    @DisplayName("Infonavit sin credito (tasa 0) es cero")
    void infonavitSinCredito() {
        assertThat(TablasFiscalesNomina.descuentoInfonavit(
                new BigDecimal("7500.00"), TablasFiscalesNomina.TASA_INFONAVIT_SIN_CREDITO))
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Infonavit con credito = base * tasa")
    void infonavitConCredito() {
        // 3,000.00 * 0.05 = 150.00
        assertThat(TablasFiscalesNomina.descuentoInfonavit(
                new BigDecimal("3000.00"), new BigDecimal("0.05")))
                .isEqualByComparingTo(new BigDecimal("150.00"));
    }

    // ----------------------------------------------------------------------
    // Calculo integral -- casos concretos verificados a mano (Req 41.1, 41.2)
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("Nomina quincenal salario 500/dia: percepciones 7,500, sin subsidio")
    void calculoQuincenalSinSubsidio() {
        // salario = 500.00 * 15 = 7,500.00 ; percepciones = 7,500.00
        // ISR = 498.90 ; IMSS = 7,500 * 0.025 = 187.50 ; Infonavit = 0
        // deducciones = 498.90 + 187.50 = 686.40
        // subsidio = 0 (7,500 > 7,382.34)
        // neto = 7,500.00 - 686.40 + 0.00 = 6,813.60
        ResultadoNomina r = CalculoNomina.calcular(new EntradaNomina(
                new BigDecimal("500.00"), 15, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(r.percepciones()).isEqualByComparingTo(new BigDecimal("7500.00"));
        assertThat(r.isr()).isEqualByComparingTo(new BigDecimal("498.90"));
        assertThat(r.imss()).isEqualByComparingTo(new BigDecimal("187.50"));
        assertThat(r.infonavit()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(r.deducciones()).isEqualByComparingTo(new BigDecimal("686.40"));
        assertThat(r.subsidio()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(r.neto()).isEqualByComparingTo(new BigDecimal("6813.60"));
    }

    @Test
    @DisplayName("Nomina quincenal salario 200/dia: percepciones 3,000, con subsidio")
    void calculoQuincenalConSubsidio() {
        // salario = 200.00 * 15 = 3,000.00 ; percepciones = 3,000.00
        // ISR = 158.57 ; IMSS = 75.00 ; Infonavit = 0
        // deducciones = 158.57 + 75.00 = 233.57
        // subsidio = 475.00 (3,000 <= 7,382.34)
        // neto = 3,000.00 - 233.57 + 475.00 = 3,241.43
        ResultadoNomina r = CalculoNomina.calcular(new EntradaNomina(
                new BigDecimal("200.00"), 15, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(r.percepciones()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(r.isr()).isEqualByComparingTo(new BigDecimal("158.57"));
        assertThat(r.imss()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(r.deducciones()).isEqualByComparingTo(new BigDecimal("233.57"));
        assertThat(r.subsidio()).isEqualByComparingTo(new BigDecimal("475.00"));
        assertThat(r.neto()).isEqualByComparingTo(new BigDecimal("3241.43"));
    }

    @Test
    @DisplayName("Nomina mensual salario 100/dia con Infonavit 5%: neto 3,091.43")
    void calculoMensualConInfonavit() {
        // salario = 100.00 * 30 = 3,000.00 ; percepciones = 3,000.00
        // ISR = 158.57 ; IMSS = 75.00 ; Infonavit = 3,000 * 0.05 = 150.00
        // deducciones = 158.57 + 75.00 + 150.00 = 383.57
        // subsidio = 475.00 ; neto = 3,000.00 - 383.57 + 475.00 = 3,091.43
        ResultadoNomina r = CalculoNomina.calcular(new EntradaNomina(
                new BigDecimal("100.00"), 30, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("0.05")));

        assertThat(r.infonavit()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(r.deducciones()).isEqualByComparingTo(new BigDecimal("383.57"));
        assertThat(r.subsidio()).isEqualByComparingTo(new BigDecimal("475.00"));
        assertThat(r.neto()).isEqualByComparingTo(new BigDecimal("3091.43"));
    }

    @Test
    @DisplayName("Nomina quincenal con tiempo extra: percepciones incluyen el extra")
    void calculoConTiempoExtra() {
        // salario = 300.00 * 15 = 4,500.00 ; tiempo extra = 500.00
        // percepciones = 4,500.00 + 500.00 = 5,000.00
        // ISR: excedente = 5,000 - 746.05 = 4,253.95 ; marginal = 4,253.95*0.064 = 272.2528
        //      ISR = round(14.32 + 272.2528) = 286.57
        // IMSS = 4,500 * 0.025 = 112.50 (sobre el salario base, no las percepciones)
        // deducciones = 286.57 + 112.50 = 399.07
        // subsidio = 475.00 ; neto = 5,000.00 - 399.07 + 475.00 = 5,075.93
        ResultadoNomina r = CalculoNomina.calcular(new EntradaNomina(
                new BigDecimal("300.00"), 15, new BigDecimal("500.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(r.salario()).isEqualByComparingTo(new BigDecimal("4500.00"));
        assertThat(r.tiempoExtra()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(r.percepciones()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(r.isr()).isEqualByComparingTo(new BigDecimal("286.57"));
        assertThat(r.imss()).isEqualByComparingTo(new BigDecimal("112.50"));
        assertThat(r.deducciones()).isEqualByComparingTo(new BigDecimal("399.07"));
        assertThat(r.neto()).isEqualByComparingTo(new BigDecimal("5075.93"));
    }
}
