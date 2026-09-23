package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias de {@link RfcValidador} (Req 24): validacion estructural del
 * RFC mexicano de persona moral (12) y fisica (13), normalizacion a mayusculas y
 * rechazo de formatos invalidos con {@link ReglaNegocioException} (HTTP 422).
 */
class RfcValidadorTest {

    @Test
    @DisplayName("Acepta un RFC de persona moral valido (3 letras + 6 digitos + 3 homoclave)")
    void aceptaRfcMoralValido() {
        // 12 caracteres: 3 letras de razon social.
        assertThat(RfcValidador.esValido("ANO120101AB1")).isTrue();
        assertThat(RfcValidador.normalizarYValidar("ANO120101AB1")).isEqualTo("ANO120101AB1");
    }

    @Test
    @DisplayName("Acepta un RFC de persona fisica valido (4 letras + 6 digitos + 3 homoclave)")
    void aceptaRfcFisicaValido() {
        // 13 caracteres: 4 letras (apellidos + nombre).
        assertThat(RfcValidador.esValido("VECJ880326XXX")).isTrue();
        assertThat(RfcValidador.normalizarYValidar("VECJ880326XXX")).isEqualTo("VECJ880326XXX");
    }

    @Test
    @DisplayName("Normaliza a mayusculas y recorta espacios antes de validar")
    void normalizaMinusculasYEspacios() {
        assertThat(RfcValidador.normalizarYValidar("  ano120101ab1  ")).isEqualTo("ANO120101AB1");
    }

    @Test
    @DisplayName("Acepta la letra N-tilde y el ampersand en la parte alfabetica de razones sociales")
    void aceptaCaracteresEspecialesRazonSocial() {
        assertThat(RfcValidador.esValido("O&M120101AB1")).isTrue();
        assertThat(RfcValidador.esValido("ÑOL120101AB1")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "RFC-INVALIDO",       // caracteres no permitidos
            "AN120101AB1",        // solo 2 letras (11 caracteres)
            "ANOX120101AB12",     // 14 caracteres (excede fisica)
            "ANO12010AB1",        // solo 5 digitos de fecha
            "ANO1201XXABC",       // letras donde van los digitos de fecha
            "ANO120101AB"         // homoclave incompleta
    })
    @DisplayName("Rechaza estructuras que no corresponden a un RFC mexicano")
    void rechazaFormatosInvalidos(String invalido) {
        assertThat(RfcValidador.esValido(invalido.toUpperCase(java.util.Locale.ROOT))).isFalse();
        assertThatThrownBy(() -> RfcValidador.normalizarYValidar(invalido))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Un RFC nulo o en blanco se rechaza como obligatorio (422)")
    void rechazaNuloOBlanco() {
        assertThatThrownBy(() -> RfcValidador.normalizarYValidar(null))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> RfcValidador.normalizarYValidar("   "))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
