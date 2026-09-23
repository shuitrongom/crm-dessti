package com.dessti.crm.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Pruebas unitarias acotadas del verificador de secretos requeridos (Req 11.2, 11.3).
 *
 * <p>Usan un {@link MockEnvironment} en memoria: no arrancan el contexto de
 * Spring ni requieren base de datos.</p>
 */
class SecretosValidadorTest {

    private MockEnvironment entornoConTodosLosSecretos() {
        return new MockEnvironment()
                .withProperty("spring.datasource.username", "usuario_bd")
                .withProperty("spring.datasource.password", "clave_bd_secreta")
                .withProperty("crm.secretos.jwt-signing-key", "clave_firma_jwt");
    }

    @Test
    @DisplayName("No lanza excepción cuando todos los secretos requeridos están presentes")
    void validar_conTodosLosSecretos_noLanza() {
        MockEnvironment env = entornoConTodosLosSecretos();

        assertThatCode(() -> SecretosValidador.validar(env)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Aborta cuando falta la clave de firma JWT y reporta su nombre sin el valor")
    void validar_faltaJwt_lanzaConNombre() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.username", "usuario_bd")
                .withProperty("spring.datasource.password", "clave_bd_secreta");
        // JWT_SIGNING_KEY ausente a propósito.

        assertThatThrownBy(() -> SecretosValidador.validar(env))
                .isInstanceOf(SecretoFaltanteException.class)
                .hasMessageContaining("JWT_SIGNING_KEY");
    }

    @Test
    @DisplayName("Aborta cuando faltan credenciales de BD y las reporta por su nombre")
    void validar_faltanCredencialesBd_lanzaConNombres() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("crm.secretos.jwt-signing-key", "clave_firma_jwt");
        // DB_USER y DB_PASSWORD ausentes.

        assertThatThrownBy(() -> SecretosValidador.validar(env))
                .isInstanceOf(SecretoFaltanteException.class)
                .hasMessageContaining("DB_USER")
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("Un secreto vacío o en blanco se trata como ausente")
    void validar_secretoEnBlanco_seTrataComoAusente() {
        MockEnvironment env = entornoConTodosLosSecretos()
                .withProperty("spring.datasource.password", "   ");

        assertThatThrownBy(() -> SecretosValidador.validar(env))
                .isInstanceOf(SecretoFaltanteException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("El mensaje de la excepción NUNCA expone el valor de un secreto (Req 11.2, 11.3)")
    void mensaje_noExponeValoresDeSecretos() {
        String valorSensible = "valor-super-secreto-1234";
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.username", "usuario_bd");
        // Se dejan otros ausentes; el valor presente no debe filtrarse jamás.

        Throwable ex = org.assertj.core.api.Assertions.catchThrowable(() -> SecretosValidador.validar(env));

        assertThat(ex).isInstanceOf(SecretoFaltanteException.class);
        assertThat(ex.getMessage()).doesNotContain(valorSensible);
        assertThat(ex.getMessage()).doesNotContain("usuario_bd");
    }

    @Test
    @DisplayName("Reporta todos los secretos faltantes de una sola vez")
    void validar_reportaTodosLosFaltantes() {
        MockEnvironment env = new MockEnvironment(); // nada configurado

        assertThatThrownBy(() -> SecretosValidador.validar(env))
                .isInstanceOf(SecretoFaltanteException.class)
                .hasMessageContaining("DB_USER")
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("JWT_SIGNING_KEY");
    }

    @Test
    @DisplayName("La lista por defecto declara exactamente los tres secretos requeridos")
    void listaPorDefecto_contieneLosTresSecretos() {
        List<SecretosValidador.SecretoRequerido> requeridos = SecretosValidador.SECRETOS_REQUERIDOS;

        assertThat(requeridos).hasSize(3);
        assertThat(requeridos).extracting(SecretosValidador.SecretoRequerido::variableEntorno)
                .containsExactlyInAnyOrder("DB_USER", "DB_PASSWORD", "JWT_SIGNING_KEY");
    }

    @Test
    @DisplayName("SecretosProperties.toString() enmascara los valores sensibles (Req 11.3)")
    void secretosProperties_toStringEnmascara() {
        SecretosProperties props =
                new SecretosProperties("usuario_bd", "clave_bd_secreta", "clave_firma_jwt");

        String texto = props.toString();

        assertThat(texto).doesNotContain("usuario_bd");
        assertThat(texto).doesNotContain("clave_bd_secreta");
        assertThat(texto).doesNotContain("clave_firma_jwt");
        assertThat(texto).contains("****");
    }

    @Test
    @DisplayName("SecretosProperties.toString() marca los secretos ausentes sin exponer nada")
    void secretosProperties_toStringMarcaAusentes() {
        SecretosProperties props = new SecretosProperties(null, "", "clave");

        String texto = props.toString();

        assertThat(texto).contains("<ausente>");
        assertThat(texto).contains("****");
        assertThat(texto).doesNotContain("clave");
    }
}
