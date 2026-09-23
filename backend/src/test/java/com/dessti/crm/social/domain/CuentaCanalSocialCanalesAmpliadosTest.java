package com.dessti.crm.social.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pruebas unitarias de que la fabrica de dominio {@link CuentaCanalSocial#crear}
 * admite los canales ampliados FACEBOOK y TIKTOK (spec redes-sociales-conexiones,
 * Req 1.2), ademas de los tres canales previos, mapeando correctamente el
 * {@link CanalSocial} al agregado. No requiere Spring ni base de datos.
 */
class CuentaCanalSocialCanalesAmpliadosTest {

    @Test
    void crearAceptaCanalFacebook() {
        CuentaCanalSocial cuenta = CuentaCanalSocial.crear(
                CanalSocial.FACEBOOK, "pagina-fb-123", "Pagina de Facebook",
                "secreto/facebook", "admin");

        assertThat(cuenta.getCanal()).isEqualTo(CanalSocial.FACEBOOK);
        assertThat(cuenta.getCanal().valorBd()).isEqualTo("facebook");
        assertThat(cuenta.isActiva()).isTrue();
    }

    @Test
    void crearAceptaCanalTiktok() {
        CuentaCanalSocial cuenta = CuentaCanalSocial.crear(
                CanalSocial.TIKTOK, "perfil-tt-456", "Perfil de TikTok",
                "secreto/tiktok", "admin");

        assertThat(cuenta.getCanal()).isEqualTo(CanalSocial.TIKTOK);
        assertThat(cuenta.getCanal().valorBd()).isEqualTo("tiktok");
        assertThat(cuenta.isActiva()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(CanalSocial.class)
    void crearAceptaLosCincoCanalesYPreservaLaEtiqueta(CanalSocial canal) {
        CuentaCanalSocial cuenta = CuentaCanalSocial.crear(
                canal, "id-externo", "Cuenta de " + canal.valorBd(),
                "secreto/" + canal.valorBd(), "admin");

        assertThat(cuenta.getCanal()).isEqualTo(canal);
    }
}
