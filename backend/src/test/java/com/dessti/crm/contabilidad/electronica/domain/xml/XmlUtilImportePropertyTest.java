package com.dessti.crm.contabilidad.electronica.domain.xml;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) del formato de importe del SAT
 * ({@link XmlUtil#importe}), Req 7.6. Para cualquier importe, la salida debe tener
 * exactamente dos decimales, punto decimal, sin notacion cientifica, y ser igual al
 * valor redondeado HALF_UP a dos decimales.
 */
class XmlUtilImportePropertyTest {

    /** Importes en centavos entre -10^11 y 10^11, escalados a 2 decimales. */
    @Provide
    Arbitrary<BigDecimal> importes() {
        return Arbitraries.longs().between(-100_000_000_000L, 100_000_000_000L)
                .map(c -> new BigDecimal(c).movePointLeft(2));
    }

    @Property(tries = 1000)
    void formatoSiempreDosDecimales(@ForAll("importes") BigDecimal valor) {
        String s = XmlUtil.importe(valor);

        // Exactamente dos decimales tras el punto.
        int punto = s.indexOf('.');
        assertThat(punto).as("debe contener punto decimal").isGreaterThanOrEqualTo(0);
        assertThat(s.substring(punto + 1))
                .as("exactamente dos decimales").hasSize(2);
        // Sin notacion cientifica.
        assertThat(s).doesNotContain("E").doesNotContain("e");
        // Igual al valor redondeado HALF_UP a 2 decimales.
        assertThat(new BigDecimal(s))
                .isEqualByComparingTo(valor.setScale(2, RoundingMode.HALF_UP));
    }

    @Property(tries = 100)
    void nuloSeTrataComoCero(@ForAll boolean ignorado) {
        assertThat(XmlUtil.importe(null)).isEqualTo("0.00");
    }
}
