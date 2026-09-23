package com.dessti.crm.platform.arranque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.security.crypto.CifradoProperties;
import com.dessti.crm.platform.security.crypto.LlaveCifradoNoDisponibleException;
import com.dessti.crm.platform.security.crypto.ProveedorLlavesEnEntorno;

/**
 * Prueba de arranque/smoke (Tarea 49.3) del <strong>bloqueo controlado por
 * llave de cifrado esencial ausente</strong> (Req 67.6, en linea con el
 * fail-fast del Req 11.2).
 *
 * <p>Verifica que la construccion del proveedor de llaves —que ocurre al crear
 * su bean durante el arranque— aborta cuando la {@code Llave_Cifrado} activa no
 * esta disponible, <strong>sin exponer</strong> el material de ninguna llave en
 * el mensaje (Req 67.2). Es una prueba unitaria acotada (no arranca el contexto
 * de Spring ni requiere base de datos), coherente con el estilo de
 * {@code SecretosValidadorTest}.</p>
 */
@DisplayName("Tarea 49.3 - Smoke: el arranque se bloquea si falta la Llave_Cifrado esencial (Req 67.6)")
class ArranqueLlaveCifradoSmokeTest {

    /** Llave AES-256 de PRUEBA (32 bytes 0x00) en Base64. NO es una llave real. */
    private static final String LLAVE_PRUEBA = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @DisplayName("Aborta cuando no hay version de llave activa configurada (CRM_ENC_KEY_ACTIVE ausente)")
    void arranque_sinLlaveActiva_seBloquea() {
        CifradoProperties sinActiva = new CifradoProperties(null, Map.of("v1", LLAVE_PRUEBA));

        Throwable lanzada = catchThrowable(() -> new ProveedorLlavesEnEntorno(sinActiva));

        assertThat(lanzada)
                .as("Sin llave activa, el arranque debe bloquearse (Req 67.6)")
                .isInstanceOf(LlaveCifradoNoDisponibleException.class);
        assertThat(lanzada.getMessage())
                .as("El mensaje NUNCA debe exponer material de llave (Req 67.2)")
                .doesNotContain(LLAVE_PRUEBA);
    }

    @Test
    @DisplayName("Aborta cuando el material de la llave activa no esta disponible")
    void arranque_materialDeLlaveActivaAusente_seBloquea() {
        // Se declara 'v1' como activa pero su material no se provee.
        CifradoProperties sinMaterial = new CifradoProperties("v1", Map.of());

        Throwable lanzada = catchThrowable(() -> new ProveedorLlavesEnEntorno(sinMaterial));

        assertThat(lanzada)
                .as("Sin material de la llave activa, el arranque debe bloquearse (Req 67.6)")
                .isInstanceOf(LlaveCifradoNoDisponibleException.class);
        assertThat(lanzada.getMessage()).doesNotContain(LLAVE_PRUEBA);
    }

    @Test
    @DisplayName("Arranca cuando la llave activa y su material estan presentes")
    void arranque_conLlaveActivaYMaterial_noSeBloquea() {
        CifradoProperties completa = new CifradoProperties("v1", Map.of("v1", LLAVE_PRUEBA));

        assertThatCode(() -> new ProveedorLlavesEnEntorno(completa)).doesNotThrowAnyException();
    }
}
