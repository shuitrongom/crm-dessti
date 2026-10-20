package com.dessti.crm.comercial.cliente.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del value object {@link CondicionesCredito} (Req 5, V90).
 * Verifican la constante {@code CONTADO} y la normalizacion {@code orContado}
 * (ausencia -> contado). No arrancan Spring ni base de datos.
 */
class CondicionesCreditoTest {

    @Test
    @DisplayName("CONTADO representa 0 dias y sin limite (null)")
    void contadoEsCeroSinLimite() {
        assertThat(CondicionesCredito.CONTADO.diasCredito()).isZero();
        assertThat(CondicionesCredito.CONTADO.limiteCredito()).isNull();
    }

    @Test
    @DisplayName("orContado(null) devuelve CONTADO")
    void orContadoDeNuloEsContado() {
        assertThat(CondicionesCredito.orContado(null)).isSameAs(CondicionesCredito.CONTADO);
    }

    @Test
    @DisplayName("orContado devuelve la instancia recibida cuando no es nula")
    void orContadoDevuelveLaInstancia() {
        CondicionesCredito c = new CondicionesCredito(30, new java.math.BigDecimal("1000.00"));
        assertThat(CondicionesCredito.orContado(c)).isSameAs(c);
    }
}
