package com.dessti.crm.social.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas unitarias del dominio {@link CanalSocial} tras ampliar los canales
 * soportados de tres a cinco (spec redes-sociales-conexiones, Req 1): WhatsApp,
 * Facebook, Instagram, Messenger y TikTok.
 *
 * <p>Verifican que {@link CanalSocial#valorBd()} y
 * {@link CanalSocial#desdeValorBd(String)} son inversas para los cinco canales
 * (Property 1) y que los tres canales previos siguen resolviendo igual
 * (no regresion, Req 1.5).</p>
 */
class CanalSocialTest {

    @Test
    void soportaExactamenteLosCincoCanales() {
        assertThat(CanalSocial.values())
                .as("los cinco canales soportados (Req 1.1)")
                .extracting(CanalSocial::valorBd)
                .containsExactlyInAnyOrder(
                        "whatsapp", "facebook", "instagram", "messenger", "tiktok");
    }

    @ParameterizedTest
    @CsvSource({
        "WHATSAPP,  whatsapp",
        "FACEBOOK,  facebook",
        "INSTAGRAM, instagram",
        "MESSENGER, messenger",
        "TIKTOK,    tiktok"
    })
    void valorBdDevuelveLaEtiquetaEsperada(CanalSocial canal, String etiqueta) {
        assertThat(canal.valorBd()).isEqualTo(etiqueta);
    }

    @ParameterizedTest
    @EnumSource(CanalSocial.class)
    void valorBdYDesdeValorBdSonInversasParaLosCinco(CanalSocial canal) {
        assertThat(CanalSocial.desdeValorBd(canal.valorBd()))
                .as("round-trip valorBd -> desdeValorBd para %s", canal)
                .isEqualTo(canal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"whatsapp", "messenger", "instagram"})
    void losTresCanalesPreviosSiguenResolviendo(String etiquetaPrevia) {
        assertThat(CanalSocial.desdeValorBd(etiquetaPrevia).valorBd())
                .as("los tres canales previos se preservan (Req 1.5)")
                .isEqualTo(etiquetaPrevia);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FACEBOOK", "  Tiktok  ", "WhatsApp"})
    void desdeValorBdIgnoraMayusculasYEspacios(String valor) {
        assertThat(CanalSocial.desdeValorBd(valor))
                .isEqualTo(CanalSocial.desdeValorBd(valor.strip().toLowerCase()));
    }

    @Test
    void desdeValorBdRechazaValorNulo() {
        assertThatThrownBy(() -> CanalSocial.desdeValorBd(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"twitter", "linkedin", "telegram", "x", ""})
    void desdeValorBdRechazaCanalDesconocido(String desconocido) {
        assertThatThrownBy(() -> CanalSocial.desdeValorBd(desconocido))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
