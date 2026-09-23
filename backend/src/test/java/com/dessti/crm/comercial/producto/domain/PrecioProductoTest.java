package com.dessti.crm.comercial.producto.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias del rango de precios de {@link PrecioProducto} (Req 59.3,
 * 59.10; Property 30). Cubre los limites del rango cerrado
 * {@code [0.01, 999,999,999.99]}. La prueba de propiedad exhaustiva es la tarea
 * 16.2; aqui se prueban ejemplos de frontera para dejar el modulo probado.
 */
class PrecioProductoTest {

    private static final UUID LISTA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCTO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("precio 0.01 (limite inferior) se acepta (Req 59.3)")
    void precioLimiteInferiorValido() {
        PrecioProducto pp = PrecioProducto.crear(LISTA, PRODUCTO, new BigDecimal("0.01"), "ventas");
        assertThat(pp.getPrecio()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("precio 999,999,999.99 (limite superior) se acepta (Req 59.3)")
    void precioLimiteSuperiorValido() {
        PrecioProducto pp = PrecioProducto.crear(LISTA, PRODUCTO, new BigDecimal("999999999.99"), "ventas");
        assertThat(pp.getPrecio()).isEqualByComparingTo("999999999.99");
    }

    @Test
    @DisplayName("precio 0.00 (por debajo del minimo) se rechaza con 422 (Req 59.10)")
    void precioCeroSeRechaza() {
        assertThatThrownBy(() -> PrecioProducto.crear(LISTA, PRODUCTO, new BigDecimal("0.00"), "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("precio 1,000,000,000.00 (por encima del maximo) se rechaza con 422 (Req 59.10)")
    void precioSobreMaximoSeRechaza() {
        assertThatThrownBy(() -> PrecioProducto.crear(LISTA, PRODUCTO, new BigDecimal("1000000000.00"), "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("el precio se normaliza a 2 decimales con redondeo al valor mas cercano")
    void precioSeNormalizaAEscala2() {
        PrecioProducto pp = PrecioProducto.crear(LISTA, PRODUCTO, new BigDecimal("10.005"), "ventas");
        assertThat(pp.getPrecio()).isEqualByComparingTo("10.01");
    }

    @Test
    @DisplayName("precio nulo se rechaza con 422 (Req 59.10)")
    void precioNuloSeRechaza() {
        assertThatThrownBy(() -> PrecioProducto.crear(LISTA, PRODUCTO, null, "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
