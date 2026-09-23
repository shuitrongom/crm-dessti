package com.dessti.crm.comercial.oportunidad.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias del dominio {@link Oportunidad} (Req 14.1-14.5). Cubren el
 * alta con datos obligatorios y etapa inicial 'nuevo', la validacion de titulo y
 * rango del valor estimado, la asignacion de responsable, el cambio de etapa que
 * aplica la maquina de estados (exito y rechazo con 409) y la guarda de
 * conversion.
 */
class OportunidadTest {

    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USUARIO = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static Oportunidad nueva() {
        return Oportunidad.crear(CLIENTE, "Anuncio corporativo", new BigDecimal("15000.00"), "ventas");
    }

    @Test
    @DisplayName("crear fija la etapa inicial 'nuevo' y normaliza los datos (Req 14.1)")
    void crearEtapaInicialNuevo() {
        Oportunidad o = Oportunidad.crear(CLIENTE, "  Anuncio  ", new BigDecimal("15000.005"), "ventas");

        assertThat(o.getEtapa()).isEqualTo(EtapaOportunidad.NUEVO);
        assertThat(o.getClienteId()).isEqualTo(CLIENTE);
        assertThat(o.getTitulo()).isEqualTo("Anuncio");
        assertThat(o.getValorEstimado()).isEqualByComparingTo("15000.01"); // half-up a escala 2
        assertThat(o.getResponsableUsuarioId()).isNull();
        assertThat(o.getCotizacionId()).isNull();
    }

    @Test
    @DisplayName("crear rechaza Cliente nulo, titulo vacio y valor fuera de rango (Req 14.1 -> 422)")
    void crearRechazaDatosInvalidos() {
        assertThatThrownBy(() -> Oportunidad.crear(null, "t", new BigDecimal("1.00"), "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Oportunidad.crear(CLIENTE, "  ", new BigDecimal("1.00"), "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Oportunidad.crear(CLIENTE, "t", new BigDecimal("0.00"), "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Oportunidad.crear(CLIENTE, "t", new BigDecimal("1000000000.00"), "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("asignarResponsable registra al Usuario responsable (Req 14.2)")
    void asignarResponsable() {
        Oportunidad o = nueva();
        o.asignarResponsable(USUARIO, "gerente");
        assertThat(o.getResponsableUsuarioId()).isEqualTo(USUARIO);

        assertThatThrownBy(() -> o.asignarResponsable(null, "gerente"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("cambiarEtapa aplica una transicion valida (Req 14.3)")
    void cambiarEtapaValida() {
        Oportunidad o = nueva();
        o.cambiarEtapa(EtapaOportunidad.CALIFICADO, "ventas");
        assertThat(o.getEtapa()).isEqualTo(EtapaOportunidad.CALIFICADO);
        o.cambiarEtapa(EtapaOportunidad.PROPUESTA, "ventas");
        o.cambiarEtapa(EtapaOportunidad.NEGOCIACION, "ventas");
        o.cambiarEtapa(EtapaOportunidad.GANADO, "ventas");
        assertThat(o.getEtapa()).isEqualTo(EtapaOportunidad.GANADO);
    }

    @Test
    @DisplayName("cambiarEtapa invalida lanza 409 y conserva la etapa actual (Req 14.4)")
    void cambiarEtapaInvalidaConservaEtapa() {
        Oportunidad o = nueva();
        assertThatThrownBy(() -> o.cambiarEtapa(EtapaOportunidad.GANADO, "ventas"))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(o.getEtapa()).isEqualTo(EtapaOportunidad.NUEVO);
    }

    @Test
    @DisplayName("cambiarEtapa desde una etapa final lanza 409 (Req 14.4)")
    void cambiarEtapaDesdeFinal() {
        Oportunidad o = nueva();
        o.cambiarEtapa(EtapaOportunidad.PERDIDO, "ventas");
        assertThat(o.getEtapa()).isEqualTo(EtapaOportunidad.PERDIDO);
        assertThatThrownBy(() -> o.cambiarEtapa(EtapaOportunidad.CALIFICADO, "ventas"))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("esConvertible solo es cierto en etapa 'ganado' y marcarConvertida enlaza la Cotizacion (Req 14.5, 14.6)")
    void convertibleYMarcarConvertida() {
        Oportunidad o = nueva();
        assertThat(o.esConvertible()).isFalse();
        o.cambiarEtapa(EtapaOportunidad.CALIFICADO, "ventas");
        o.cambiarEtapa(EtapaOportunidad.PROPUESTA, "ventas");
        o.cambiarEtapa(EtapaOportunidad.NEGOCIACION, "ventas");
        o.cambiarEtapa(EtapaOportunidad.GANADO, "ventas");
        assertThat(o.esConvertible()).isTrue();

        UUID cotizacion = UUID.randomUUID();
        o.marcarConvertida(cotizacion, "ventas");
        assertThat(o.getCotizacionId()).isEqualTo(cotizacion);
    }
}
