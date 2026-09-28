package com.dessti.crm.compras.factura.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la maquina de estados pura de {@link EstadoFacturaProveedor}
 * (Req 33.6 + mejora enterprise de reapertura de discrepancia). Verifican las
 * transiciones permitidas, el unico estado final ({@code pagada}), la reapertura de
 * {@code discrepancia -> registrada} y el mapeo a/desde la etiqueta persistida.
 */
class EstadoFacturaProveedorTest {

    @Test
    @DisplayName("registrada transita a conciliada y a discrepancia (Req 33.6)")
    void registradaTransiciones() {
        assertThat(EstadoFacturaProveedor.REGISTRADA
                .puedeTransicionarA(EstadoFacturaProveedor.CONCILIADA)).isTrue();
        assertThat(EstadoFacturaProveedor.REGISTRADA
                .puedeTransicionarA(EstadoFacturaProveedor.DISCREPANCIA)).isTrue();
    }

    @Test
    @DisplayName("conciliada transita a pagada (Req 33.7)")
    void conciliadaTransiciones() {
        assertThat(EstadoFacturaProveedor.CONCILIADA
                .puedeTransicionarA(EstadoFacturaProveedor.PAGADA)).isTrue();
    }

    @Test
    @DisplayName("discrepancia es RECUPERABLE: puede reabrirse a registrada")
    void discrepanciaRecuperable() {
        assertThat(EstadoFacturaProveedor.DISCREPANCIA
                .puedeTransicionarA(EstadoFacturaProveedor.REGISTRADA)).isTrue();
        assertThat(EstadoFacturaProveedor.DISCREPANCIA.esFinal()).isFalse();
        // Desde discrepancia NO se salta directo a conciliada/pagada (debe re-conciliar).
        assertThat(EstadoFacturaProveedor.DISCREPANCIA
                .puedeTransicionarA(EstadoFacturaProveedor.CONCILIADA)).isFalse();
        assertThat(EstadoFacturaProveedor.DISCREPANCIA
                .puedeTransicionarA(EstadoFacturaProveedor.PAGADA)).isFalse();
    }

    @Test
    @DisplayName("pagada es el unico estado final, sin transiciones salientes")
    void pagadaEsFinal() {
        assertThat(EstadoFacturaProveedor.PAGADA.esFinal()).isTrue();
        assertThat(EstadoFacturaProveedor.PAGADA
                .puedeTransicionarA(EstadoFacturaProveedor.REGISTRADA)).isFalse();
        assertThat(EstadoFacturaProveedor.PAGADA
                .puedeTransicionarA(EstadoFacturaProveedor.CONCILIADA)).isFalse();
    }

    @Test
    @DisplayName("mapeo a/desde la etiqueta de base de datos")
    void mapeoEtiqueta() {
        assertThat(EstadoFacturaProveedor.REGISTRADA.valorBd()).isEqualTo("registrada");
        assertThat(EstadoFacturaProveedor.CONCILIADA.valorBd()).isEqualTo("conciliada");
        assertThat(EstadoFacturaProveedor.DISCREPANCIA.valorBd()).isEqualTo("discrepancia");
        assertThat(EstadoFacturaProveedor.PAGADA.valorBd()).isEqualTo("pagada");
        assertThat(EstadoFacturaProveedor.desdeValorBd("  DISCREPANCIA "))
                .isEqualTo(EstadoFacturaProveedor.DISCREPANCIA);
    }
}
