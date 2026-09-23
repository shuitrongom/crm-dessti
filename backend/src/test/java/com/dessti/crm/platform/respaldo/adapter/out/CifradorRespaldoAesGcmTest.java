package com.dessti.crm.platform.respaldo.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dessti.crm.platform.respaldo.application.CifradorRespaldoPort;
import com.dessti.crm.platform.respaldo.application.CifradorRespaldoPort.ArtefactoCifrado;
import com.dessti.crm.platform.respaldo.application.RespaldoException;
import com.dessti.crm.platform.security.crypto.ProveedorLlaves;

/**
 * Pruebas unitarias del cifrador de artefactos de respaldo (Tarea 49.2, Req 50.3,
 * 67). Verifican el cifrado/descifrado round-trip del artefacto, la deteccion de
 * manipulacion (AES-GCM) y el fallo controlado cuando la version de llave no esta
 * disponible, sin arrancar el contexto de Spring.
 */
@DisplayName("Tarea 49.2 - Cifrador de respaldo AES-256-GCM (Req 50.3, 67)")
class CifradorRespaldoAesGcmTest {

    private static SecretKey llaveAleatoria() {
        byte[] material = new byte[32];
        new SecureRandom().nextBytes(material);
        return new SecretKeySpec(material, "AES");
    }

    @Test
    @DisplayName("Round-trip: cifra un volcado y lo descifra al contenido original")
    void cifraYDescifraElVolcado(@TempDir Path dir) throws Exception {
        SecretKey v1 = llaveAleatoria();
        CifradorRespaldoPort cifrador =
                new CifradorRespaldoAesGcm(new ProveedorLlavesFalso("v1", Map.of("v1", v1)));

        byte[] contenido = "VOLCADO-SQL-DE-PRUEBA-datos-fiscales".getBytes();
        Path volcado = dir.resolve("volcado.sql");
        Files.write(volcado, contenido);
        Path artefacto = dir.resolve("respaldo.enc");

        ArtefactoCifrado meta = cifrador.cifrar(volcado, artefacto);

        // El artefacto cifrado NO contiene el contenido en claro. Se comprueba
        // sobre los BYTES cifrados: el artefacto es ciphertext AES-GCM (bytes
        // arbitrarios), no texto UTF-8 valido, por lo que no puede decodificarse.
        byte[] cifrado = Files.readAllBytes(artefacto);
        byte[] marcador = "VOLCADO-SQL".getBytes(StandardCharsets.UTF_8);
        assertThat(contiene(cifrado, marcador))
                .as("El artefacto cifrado no debe contener el contenido en claro (Req 67)")
                .isFalse();
        assertThat(meta.aliasLlave()).isEqualTo("v1");
        assertThat(meta.checksum()).isNotBlank();
        assertThat(meta.tamanoBytes()).isEqualTo(Files.size(artefacto));

        // Se descifra correctamente al contenido original.
        Path restaurado = dir.resolve("restaurado.sql");
        cifrador.descifrar(artefacto, restaurado);
        assertThat(Files.readAllBytes(restaurado)).isEqualTo(contenido);
    }

    @Test
    @DisplayName("Un artefacto manipulado falla al descifrar (autenticacion GCM)")
    void artefactoManipuladoFallaAlDescifrar(@TempDir Path dir) throws Exception {
        SecretKey v1 = llaveAleatoria();
        CifradorRespaldoPort cifrador =
                new CifradorRespaldoAesGcm(new ProveedorLlavesFalso("v1", Map.of("v1", v1)));

        Path volcado = dir.resolve("volcado.sql");
        Files.write(volcado, "contenido-integro".getBytes());
        Path artefacto = dir.resolve("respaldo.enc");
        cifrador.cifrar(volcado, artefacto);

        // Se manipula el ultimo byte del artefacto (dentro del ciphertext/tag).
        byte[] datos = Files.readAllBytes(artefacto);
        datos[datos.length - 1] ^= 0x01;
        Files.write(artefacto, datos);

        assertThatThrownBy(() -> cifrador.descifrar(artefacto, dir.resolve("salida.sql")))
                .isInstanceOf(RespaldoException.class);
    }

    @Test
    @DisplayName("Rotacion: un artefacto cifrado con v1 se descifra tras activar v2 conservando v1")
    void descifraTrasRotacionSiLaVersionSigueDisponible(@TempDir Path dir) throws Exception {
        SecretKey v1 = llaveAleatoria();
        SecretKey v2 = llaveAleatoria();

        // Se cifra con v1 activa.
        CifradorRespaldoPort conV1 =
                new CifradorRespaldoAesGcm(new ProveedorLlavesFalso("v1", Map.of("v1", v1)));
        Path volcado = dir.resolve("volcado.sql");
        Files.write(volcado, "dato-previo-a-rotacion".getBytes());
        Path artefacto = dir.resolve("respaldo.enc");
        conV1.cifrar(volcado, artefacto);

        // Rotacion: v2 activa, v1 aun disponible para descifrar.
        CifradorRespaldoPort trasRotacion = new CifradorRespaldoAesGcm(
                new ProveedorLlavesFalso("v2", new LinkedHashMap<>(Map.of("v1", v1, "v2", v2))));

        Path restaurado = dir.resolve("restaurado.sql");
        trasRotacion.descifrar(artefacto, restaurado);
        assertThat(Files.readString(restaurado)).isEqualTo("dato-previo-a-rotacion");
    }

    @Test
    @DisplayName("Fallo controlado si la version de llave del artefacto no esta disponible (Req 67.6)")
    void fallaControladoSiFaltaLaVersionDeLlave(@TempDir Path dir) throws Exception {
        SecretKey v1 = llaveAleatoria();
        SecretKey v2 = llaveAleatoria();

        CifradorRespaldoPort conV1 =
                new CifradorRespaldoAesGcm(new ProveedorLlavesFalso("v1", Map.of("v1", v1)));
        Path volcado = dir.resolve("volcado.sql");
        Files.write(volcado, "dato".getBytes());
        Path artefacto = dir.resolve("respaldo.enc");
        conV1.cifrar(volcado, artefacto);

        // Proveedor que YA NO tiene v1 (solo v2): version requerida no disponible.
        CifradorRespaldoPort sinV1 =
                new CifradorRespaldoAesGcm(new ProveedorLlavesFalso("v2", Map.of("v2", v2)));

        assertThatThrownBy(() -> sinV1.descifrar(artefacto, dir.resolve("salida.sql")))
                .isInstanceOf(RespaldoException.class);
    }

    /**
     * Indica si la secuencia de bytes {@code aguja} aparece de forma contigua
     * dentro de {@code heno} (barrido de ventana deslizante). Devuelve false si
     * {@code aguja} esta vacia o es mas larga que {@code heno}.
     */
    private static boolean contiene(byte[] heno, byte[] aguja) {
        if (aguja.length == 0 || aguja.length > heno.length) {
            return false;
        }
        for (int i = 0; i <= heno.length - aguja.length; i++) {
            boolean coincide = true;
            for (int j = 0; j < aguja.length; j++) {
                if (heno[i + j] != aguja[j]) {
                    coincide = false;
                    break;
                }
            }
            if (coincide) {
                return true;
            }
        }
        return false;
    }

    /**
     * Proveedor de llaves de PRUEBA en memoria (no persiste ni resuelve del
     * entorno). Material aleatorio, jamas de produccion.
     */
    private static final class ProveedorLlavesFalso implements ProveedorLlaves {

        private final String aliasActivo;
        private final Map<String, SecretKey> llaves;

        ProveedorLlavesFalso(String aliasActivo, Map<String, SecretKey> llaves) {
            this.aliasActivo = aliasActivo;
            this.llaves = new LinkedHashMap<>(llaves);
        }

        @Override
        public String aliasActivo() {
            return aliasActivo;
        }

        @Override
        public SecretKey llavePara(String alias) {
            SecretKey llave = llaves.get(alias);
            if (llave == null) {
                throw new com.dessti.crm.platform.security.crypto.LlaveCifradoNoDisponibleException(alias);
            }
            return llave;
        }

        @Override
        public Set<String> versionesDisponibles() {
            return Set.copyOf(llaves.keySet());
        }
    }
}
