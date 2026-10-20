package com.dessti.crm.compras.ordencompra.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias del dominio {@link OrdenCompra} y {@link OrdenCompraValidaciones}
 * (Req 31): cálculo del subtotal por partida y del total como Σ subtotales half-up,
 * rangos de cantidad/precio/partidas, y estado inicial. No arrancan Spring ni base de
 * datos: aritmética monetaria y reglas puras del agregado.
 */
class OrdenCompraDominioTest {

    private static final String ACTOR = "sistema";
    private static final UUID PROVEEDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MATERIAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static PartidaOrdenCompra partida(int cantidad, String precio) {
        return PartidaOrdenCompra.crear(MATERIAL_ID, cantidad, new BigDecimal(precio), ACTOR);
    }

    @Test
    @DisplayName("el subtotal de una partida es cantidad × precio, escala 2 half-up")
    void subtotalPartida() {
        PartidaOrdenCompra p = partida(3, "100.005"); // 100.005 → 100.01 (half-up) × 3 = 300.03
        assertThat(p.getPrecioUnitario()).isEqualByComparingTo("100.01");
        assertThat(p.getSubtotal()).isEqualByComparingTo("300.03");
    }

    @Test
    @DisplayName("el total de la orden es la suma de los subtotales, escala 2 half-up")
    void totalOrden() {
        OrdenCompra orden = OrdenCompra.crear(PROVEEDOR_ID, null,
                List.of(partida(2, "10.50"), partida(3, "5.25")), ACTOR);
        // 2×10.50 = 21.00 ; 3×5.25 = 15.75 ; total = 36.75
        assertThat(orden.getTotal()).isEqualByComparingTo("36.75");
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenCompra.ABIERTA);
        assertThat(orden.getProveedorId()).isEqualTo(PROVEEDOR_ID);
    }

    @Test
    @DisplayName("crear sin partidas se rechaza (422)")
    void sinPartidas() {
        assertThatThrownBy(() -> OrdenCompra.crear(PROVEEDOR_ID, null, List.of(), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear sin proveedor se rechaza (422)")
    void sinProveedor() {
        assertThatThrownBy(() -> OrdenCompra.crear(null, null, List.of(partida(1, "1.00")), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear con más de 500 partidas se rechaza (422)")
    void demasiadasPartidas() {
        List<PartidaOrdenCompra> muchas = IntStream.range(0, 501)
                .mapToObj(i -> partida(1, "1.00"))
                .toList();
        assertThatThrownBy(() -> OrdenCompra.crear(PROVEEDOR_ID, null, muchas, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("una cantidad fuera de rango [1, 999999] se rechaza (422)")
    void cantidadFueraDeRango() {
        assertThatThrownBy(() -> partida(0, "1.00")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> partida(1_000_000, "1.00")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("un precio que redondea a 0.00 se rechaza (422) por debajo del mínimo")
    void precioRedondeaACero() {
        // 0.004 → 0.00 (half-up), por debajo de PRECIO_MINIMO 0.01.
        assertThatThrownBy(() -> partida(1, "0.004")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("un precio por encima del máximo se rechaza (422)")
    void precioSobreMaximo() {
        assertThatThrownBy(() -> partida(1, "1000000000.00"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("cambiar estado a una transición inválida conserva el estado y lanza 409")
    void transicionInvalidaConservaEstado() {
        OrdenCompra orden = OrdenCompra.crear(PROVEEDOR_ID, null, List.of(partida(1, "1.00")), ACTOR);
        assertThatThrownBy(() -> orden.cambiarEstado(EstadoOrdenCompra.CERRADA, ACTOR))
                .isInstanceOf(com.dessti.crm.platform.error.TransicionInvalidaException.class);
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenCompra.ABIERTA);
    }
}
