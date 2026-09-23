package com.dessti.crm.platform.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias acotadas del cifrado de datos sensibles en reposo (Req 67):
 * round-trip, IV aleatorio por invocación, rotación de llave y bloqueo
 * controlado ante llave ausente. Se usan llaves de prueba en memoria (nunca las
 * reales) para ejecutar sin Docker ni contexto de Spring.
 */
class ServicioCifradoAesGcmTest {

    /** Llave AES-256 de prueba (32 bytes) codificada en Base64. Solo para pruebas. */
    private static final String LLAVE_V1_B64 =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    /** Segunda llave AES-256 de prueba (32 bytes), distinta de la v1. */
    private static final String LLAVE_V2_B64 =
            Base64.getEncoder().encodeToString("ABCDEFGHIJKLMNOPabcdefghijklmnop".getBytes());

    private static ServicioCifrado servicioCon(String activa, Map<String, String> llaves) {
        CifradoProperties props = new CifradoProperties(activa, llaves);
        return new ServicioCifradoAesGcm(new ProveedorLlavesEnEntorno(props));
    }

    @Test
    @DisplayName("cifrar/descifrar es un round-trip exacto")
    void roundTrip() {
        ServicioCifrado servicio = servicioCon("v1", Map.of("v1", LLAVE_V1_B64));
        String original = "RFC: XAXX010101000 - dato fiscal sensible";

        String cifrado = servicio.cifrar(original);

        assertThat(cifrado).startsWith("v1:");
        assertThat(cifrado).doesNotContain(original);
        assertThat(servicio.descifrar(cifrado)).isEqualTo(original);
    }

    @Test
    @DisplayName("null se preserva en ambos sentidos")
    void nullSePreserva() {
        ServicioCifrado servicio = servicioCon("v1", Map.of("v1", LLAVE_V1_B64));
        assertThat(servicio.cifrar(null)).isNull();
        assertThat(servicio.descifrar(null)).isNull();
    }

    @Test
    @DisplayName("dos cifrados del mismo texto difieren (IV aleatorio por invocación)")
    void ivDistintoPorInvocacion() {
        ServicioCifrado servicio = servicioCon("v1", Map.of("v1", LLAVE_V1_B64));
        String texto = "mismo texto";

        String c1 = servicio.cifrar(texto);
        String c2 = servicio.cifrar(texto);

        assertThat(c1).isNotEqualTo(c2);
        // Ambos descifran al mismo valor original.
        assertThat(servicio.descifrar(c1)).isEqualTo(texto);
        assertThat(servicio.descifrar(c2)).isEqualTo(texto);
    }

    @Test
    @DisplayName("rotación: v2 activa cifra nuevo; aún se descifra lo cifrado con v1")
    void rotacionConservaDescifradoDeDatosPrevios() {
        // Estado inicial: v1 activa.
        ServicioCifrado antesRotacion = servicioCon("v1", Map.of("v1", LLAVE_V1_B64));
        String cifradoConV1 = antesRotacion.cifrar("dato previo a la rotación");
        assertThat(cifradoConV1).startsWith("v1:");

        // Rotación: v2 pasa a activa; v1 permanece disponible para descifrar.
        ServicioCifrado despuesRotacion =
                servicioCon("v2", Map.of("v1", LLAVE_V1_B64, "v2", LLAVE_V2_B64));

        // Lo nuevo se cifra con v2.
        String cifradoConV2 = despuesRotacion.cifrar("dato nuevo tras rotación");
        assertThat(cifradoConV2).startsWith("v2:");

        // Se sigue descifrando lo cifrado con v1 (dato previo) y lo cifrado con v2 (nuevo).
        assertThat(despuesRotacion.descifrar(cifradoConV1)).isEqualTo("dato previo a la rotación");
        assertThat(despuesRotacion.descifrar(cifradoConV2)).isEqualTo("dato nuevo tras rotación");
    }

    @Test
    @DisplayName("descifrar con versión de llave ausente falla sin exponer el valor")
    void versionAusenteAlDescifrar() {
        // Dato cifrado con v1.
        ServicioCifrado conV1 = servicioCon("v1", Map.of("v1", LLAVE_V1_B64));
        String cifradoConV1 = conV1.cifrar("dato");

        // Proveedor que solo conoce v2 (v1 no disponible): bloqueo controlado (Req 67.6).
        ServicioCifrado soloV2 = servicioCon("v2", Map.of("v2", LLAVE_V2_B64));

        assertThatThrownBy(() -> soloV2.descifrar(cifradoConV1))
                .isInstanceOf(LlaveCifradoNoDisponibleException.class)
                .hasMessageContaining("v1")
                .hasMessageNotContainingAny(LLAVE_V1_B64, "dato");
    }

    @Test
    @DisplayName("ausencia de llave activa bloquea la construcción (arranque)")
    void ausenciaDeLlaveActivaBloquea() {
        // Activa "v1" pero sin material disponible.
        assertThatThrownBy(() -> servicioCon("v1", Map.of()))
                .isInstanceOf(LlaveCifradoNoDisponibleException.class)
                .hasMessageContaining("v1");

        // Alias activo no configurado en absoluto.
        assertThatThrownBy(() -> servicioCon("  ", Map.of("v1", LLAVE_V1_B64)))
                .isInstanceOf(LlaveCifradoNoDisponibleException.class);
    }

    @Test
    @DisplayName("dato manipulado (GCM) falla la autenticación al descifrar")
    void datoManipuladoFallaAutenticacion() {
        ServicioCifrado servicio = servicioCon("v1", Map.of("v1", LLAVE_V1_B64));
        String cifrado = servicio.cifrar("dato íntegro");

        // Alterar el último carácter del contenido Base64 (rompe el tag GCM).
        String manipulado = cifrado.substring(0, cifrado.length() - 1)
                + (cifrado.charAt(cifrado.length() - 1) == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> servicio.descifrar(manipulado))
                .isInstanceOf(CifradoException.class);
    }

    @Test
    @DisplayName("material de llave con longitud inválida se rechaza (no AES-256)")
    void longitudDeLlaveInvalida() {
        String corta = Base64.getEncoder().encodeToString("clave-corta".getBytes());
        assertThatThrownBy(() -> servicioCon("v1", Map.of("v1", corta)))
                .isInstanceOf(LlaveCifradoNoDisponibleException.class);
    }

    @Test
    @DisplayName("el proveedor expone las versiones disponibles como metadatos")
    void versionesDisponibles() {
        ProveedorLlaves proveedor = new ProveedorLlavesEnEntorno(
                new CifradoProperties("v1", Map.of("v1", LLAVE_V1_B64, "v2", LLAVE_V2_B64)));
        assertThat(proveedor.aliasActivo()).isEqualTo("v1");
        assertThat(proveedor.versionesDisponibles()).isEqualTo(Set.of("v1", "v2"));

        SecretKey llave = proveedor.llavePara("v1");
        assertThat(llave.getAlgorithm()).isEqualTo("AES");
        assertThat(llave).isInstanceOf(SecretKeySpec.class);
    }

    @Test
    @DisplayName("CifradoProperties.toString enmascara el material de las llaves")
    void toStringNoExponeMaterial() {
        CifradoProperties props = new CifradoProperties("v1", Map.of("v1", LLAVE_V1_B64));
        String texto = props.toString();
        assertThat(texto).contains("v1").doesNotContain(LLAVE_V1_B64);
    }
}
