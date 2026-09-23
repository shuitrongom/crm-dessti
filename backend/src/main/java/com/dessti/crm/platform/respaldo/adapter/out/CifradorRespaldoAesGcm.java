package com.dessti.crm.platform.respaldo.adapter.out;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import com.dessti.crm.platform.respaldo.application.CifradorRespaldoPort;
import com.dessti.crm.platform.respaldo.application.RespaldoException;
import com.dessti.crm.platform.security.crypto.LlaveCifradoNoDisponibleException;
import com.dessti.crm.platform.security.crypto.ProveedorLlaves;

/**
 * Adaptador de {@link CifradorRespaldoPort} que cifra el artefacto de respaldo
 * con <strong>AES-256-GCM</strong> usando la {@link ProveedorLlaves} existente
 * (Req 50.3, 67), reutilizando su gestion de versiones para poder descifrar
 * artefactos previos tras una rotacion de llave (Req 67).
 *
 * <p><strong>Formato del artefacto cifrado en disco:</strong> un encabezado con
 * el alias de la version de llave terminado en salto de linea, seguido del
 * IV/nonce de 12 bytes y del texto cifrado con la etiqueta de autenticacion GCM
 * (128 bits):</p>
 * <pre>
 *   &lt;aliasLlave&gt;\n | IV(12) | ciphertext || tag
 * </pre>
 *
 * <p>El IV se genera con {@link SecureRandom} por cada artefacto y nunca se
 * reutiliza. La autenticacion GCM garantiza que un artefacto manipulado se
 * detecte al descifrar. El material de la llave NUNCA se registra (Req 67.2).</p>
 */
public class CifradorRespaldoAesGcm implements CifradorRespaldoPort {

    static final String TRANSFORMACION = "AES/GCM/NoPadding";
    static final int LONGITUD_IV_BYTES = 12;
    static final int LONGITUD_TAG_BITS = 128;
    private static final char SALTO = '\n';

    private final ProveedorLlaves proveedorLlaves;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorRespaldoAesGcm(ProveedorLlaves proveedorLlaves) {
        this.proveedorLlaves = proveedorLlaves;
    }

    @Override
    public ArtefactoCifrado cifrar(Path volcadoEnClaro, Path destinoCifrado) {
        String alias = proveedorLlaves.aliasActivo();
        SecretKey llave = proveedorLlaves.llavePara(alias);

        byte[] iv = new byte[LONGITUD_IV_BYTES];
        aleatorio.nextBytes(iv);

        try {
            byte[] claro = Files.readAllBytes(volcadoEnClaro);

            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(LONGITUD_TAG_BITS, iv));
            byte[] cifradoConTag = cipher.doFinal(claro);

            byte[] cabecera = (alias + SALTO).getBytes(StandardCharsets.UTF_8);
            byte[] artefacto = new byte[cabecera.length + iv.length + cifradoConTag.length];
            System.arraycopy(cabecera, 0, artefacto, 0, cabecera.length);
            System.arraycopy(iv, 0, artefacto, cabecera.length, iv.length);
            System.arraycopy(cifradoConTag, 0, artefacto, cabecera.length + iv.length, cifradoConTag.length);

            Files.write(destinoCifrado, artefacto);

            String checksum = sha256Hex(artefacto);
            return new ArtefactoCifrado(alias, checksum, artefacto.length);
        } catch (IOException e) {
            throw new RespaldoException("Fallo al leer/escribir el artefacto de respaldo (Req 50).", e);
        } catch (GeneralSecurityException e) {
            // No se incluye contenido ni material de llave en el mensaje (Req 67.2).
            throw new RespaldoException("Fallo al cifrar el artefacto de respaldo (Req 67).", e);
        }
    }

    @Override
    public void descifrar(Path artefactoCifrado, Path destinoEnClaro) {
        try {
            byte[] artefacto = Files.readAllBytes(artefactoCifrado);
            int corte = indiceSalto(artefacto);
            if (corte <= 0 || artefacto.length <= corte + 1 + LONGITUD_IV_BYTES) {
                throw new RespaldoException(
                        "Artefacto de respaldo invalido: cabecera o longitud insuficiente (Req 67).");
            }

            String alias = new String(artefacto, 0, corte, StandardCharsets.UTF_8);
            SecretKey llave = proveedorLlaves.llavePara(alias);

            int inicioIv = corte + 1;
            byte[] iv = new byte[LONGITUD_IV_BYTES];
            System.arraycopy(artefacto, inicioIv, iv, 0, LONGITUD_IV_BYTES);

            int inicioCifrado = inicioIv + LONGITUD_IV_BYTES;
            byte[] cifradoConTag = new byte[artefacto.length - inicioCifrado];
            System.arraycopy(artefacto, inicioCifrado, cifradoConTag, 0, cifradoConTag.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(LONGITUD_TAG_BITS, iv));
            byte[] claro = cipher.doFinal(cifradoConTag);

            Files.write(destinoEnClaro, claro);
        } catch (LlaveCifradoNoDisponibleException e) {
            // Version de llave no disponible: fallo controlado sin exponer la llave (Req 67.6).
            throw new RespaldoException(
                    "No se puede descifrar el respaldo: version de llave no disponible (Req 67.6).", e);
        } catch (IOException e) {
            throw new RespaldoException("Fallo al leer/escribir durante la restauracion (Req 50).", e);
        } catch (GeneralSecurityException e) {
            throw new RespaldoException(
                    "Fallo al descifrar el respaldo: contenido invalido o manipulado (Req 67).", e);
        }
    }

    private static int indiceSalto(byte[] datos) {
        for (int i = 0; i < datos.length; i++) {
            if (datos[i] == (byte) SALTO) {
                return i;
            }
        }
        return -1;
    }

    private static String sha256Hex(byte[] datos) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(datos);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new RespaldoException("No se pudo calcular el checksum del artefacto de respaldo.", e);
        }
    }
}
