package com.dessti.crm.comercial.cliente.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias de las CONDICIONES DE CREDITO del {@link Cliente} (Req 5,
 * V90): dias de credito (0..365, 0 = contado) y limite de credito (opcional,
 * >= 0, escala 2 HALF_UP). Verifican la persistencia del valor valido, el
 * default contado de las sobrecargas sin condiciones (AC-3), el rechazo con 422
 * sin mutar la entidad (AC-4, incluida atomicidad), el redondeo monetario y la
 * revalidacion al actualizar. No arrancan Spring ni base de datos.
 */
class ClienteCondicionesCreditoTest {

    private static final String ACTOR = "ventas@acme.com";
    private static final String RFC = "ABC120101AB1";

    @Test
    @DisplayName("crear con dias_credito=45 persiste 45 (AC-3)")
    void crearConDiasCreditoPersiste() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, new CondicionesCredito(45, new BigDecimal("1000.00")), ACTOR);

        assertThat(cliente.getDiasCredito()).isEqualTo(45);
        assertThat(cliente.getLimiteCredito()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("las sobrecargas sin condiciones dejan contado: dias=0, limite=null (AC-3)")
    void sobrecargasSinCondicionesContado() {
        Cliente conBasicos = Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, ACTOR);
        Cliente minimo = Cliente.crear("Acme S.A.", RFC, "a@b.com", null, ACTOR);

        assertThat(conBasicos.getDiasCredito()).isZero();
        assertThat(conBasicos.getLimiteCredito()).isNull();
        assertThat(minimo.getDiasCredito()).isZero();
        assertThat(minimo.getLimiteCredito()).isNull();
    }

    @Test
    @DisplayName("diasCredito null se normaliza a 0 (contado)")
    void diasCreditoNuloEsCero() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, new CondicionesCredito(null, null), ACTOR);

        assertThat(cliente.getDiasCredito()).isZero();
    }

    @Test
    @DisplayName("dias_credito negativo se rechaza con 422 (AC-4)")
    void diasCreditoNegativoRechazado() {
        assertThatThrownBy(() -> Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, new CondicionesCredito(-1, null), ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("dias de credito");
    }

    @Test
    @DisplayName("dias_credito mayor que 365 se rechaza con 422 (AC-4)")
    void diasCreditoFueraDeRangoRechazado() {
        assertThatThrownBy(() -> Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, new CondicionesCredito(366, null), ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("365");
    }

    @Test
    @DisplayName("limite_credito negativo se rechaza con 422 (AC-4)")
    void limiteCreditoNegativoRechazado() {
        assertThatThrownBy(() -> Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, new CondicionesCredito(30, new BigDecimal("-0.01")), ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("limite de credito");
    }

    @Test
    @DisplayName("un dias_credito invalido no deja escrito el limite (atomicidad, AC-4)")
    void diasInvalidoNoMutaLimiteAlActualizar() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, new CondicionesCredito(30, new BigDecimal("500.00")), ACTOR);

        // dias invalido + limite nuevo valido: la excepcion debe dejar ambos campos
        // en su valor previo (dias=30, limite=500.00), nunca escribir el limite nuevo.
        assertThatThrownBy(() -> cliente.actualizar("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, new CondicionesCredito(400, new BigDecimal("9999.00")), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(cliente.getDiasCredito()).isEqualTo(30);
        assertThat(cliente.getLimiteCredito()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("limite con 4 decimales se redondea a escala 2 HALF_UP")
    void limiteRedondeaEscala2() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, new CondicionesCredito(0, new BigDecimal("1000.1255")), ACTOR);

        assertThat(cliente.getLimiteCredito()).isEqualByComparingTo("1000.13");
        assertThat(cliente.getLimiteCredito().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("actualizar revalida y aplica las nuevas condiciones de credito validas")
    void actualizarAplicaCondiciones() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null, ACTOR);

        cliente.actualizar("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, new CondicionesCredito(60, new BigDecimal("2500.00")), ACTOR);

        assertThat(cliente.getDiasCredito()).isEqualTo(60);
        assertThat(cliente.getLimiteCredito()).isEqualByComparingTo("2500.00");
    }
}
