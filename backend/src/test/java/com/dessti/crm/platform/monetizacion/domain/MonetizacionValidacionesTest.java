package com.dessti.crm.platform.monetizacion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/** Pruebas de {@link MonetizacionValidaciones}: moneda ISO 4217, clave y rango de precio. */
class MonetizacionValidacionesTest {

    @Test
    @DisplayName("normaliza el codigo de moneda a mayusculas (ISO 4217)")
    void normalizaMoneda() {
        assertThat(MonetizacionValidaciones.normalizarCodigoMoneda(" usd ")).isEqualTo("USD");
    }

    @Test
    @DisplayName("rechaza codigo de moneda invalido (no 3 letras)")
    void rechazaMonedaInvalida() {
        assertThatThrownBy(() -> MonetizacionValidaciones.normalizarCodigoMoneda("US1"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> MonetizacionValidaciones.normalizarCodigoMoneda("USDD"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> MonetizacionValidaciones.normalizarCodigoMoneda(null))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("normaliza la clave de modulo a minusculas y recorta")
    void normalizaClave() {
        assertThat(MonetizacionValidaciones.normalizarClaveModulo("  Facturacion ")).isEqualTo("facturacion");
    }

    @Test
    @DisplayName("precio: acepta 0.00 (modulo gratuito) y el maximo; escala 2 HALF_UP")
    void precioRangoValido() {
        assertThat(MonetizacionValidaciones.validarPrecio(new BigDecimal("0.00")))
                .isEqualByComparingTo("0.00");
        assertThat(MonetizacionValidaciones.validarPrecio(new BigDecimal("999999999.99")))
                .isEqualByComparingTo("999999999.99");
        assertThat(MonetizacionValidaciones.validarPrecio(new BigDecimal("10.005")))
                .isEqualByComparingTo("10.01"); // HALF_UP
    }

    @Test
    @DisplayName("precio: rechaza negativos y por encima del maximo")
    void precioFueraDeRango() {
        assertThatThrownBy(() -> MonetizacionValidaciones.validarPrecio(new BigDecimal("-0.01")))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> MonetizacionValidaciones.validarPrecio(new BigDecimal("1000000000.00")))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> MonetizacionValidaciones.validarPrecio(null))
                .isInstanceOf(ReglaNegocioException.class);
    }
}