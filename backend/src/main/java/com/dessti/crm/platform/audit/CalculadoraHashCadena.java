package com.dessti.crm.platform.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * Calculo puro del hash encadenado de la bitacora de auditoria (Req 10.4, 10.7).
 *
 * <p>Cada registro se encadena con el anterior mediante:</p>
 *
 * <pre>{@code hash_actual = SHA-256( contenido_canonico || hash_previo )}</pre>
 *
 * <p>donde {@code contenido_canonico} es una serializacion determinista de los
 * campos del registro y {@code hash_previo} es el hash hexadecimal del registro
 * anterior (o la {@link #HASH_SEMILLA semilla} para el primer registro). El
 * resultado es una cadena hexadecimal de 64 caracteres.</p>
 *
 * <p><strong>Determinismo:</strong> el orden y la codificacion de los campos son
 * fijos, y los valores nulos se representan con un marcador constante. Asi, dos
 * secuencias identicas de eventos producen exactamente la misma cadena de
 * hashes, y alterar cualquier campo de cualquier registro cambia su
 * {@code hash_actual} (y, por encadenamiento, el de todos los posteriores),
 * haciendo detectable la manipulacion.</p>
 *
 * <p>Clase de utilidad sin estado y thread-safe (crea un {@link MessageDigest}
 * por invocacion).</p>
 */
public final class CalculadoraHashCadena {

    /**
     * Hash semilla del primer registro de la cadena (64 ceros hexadecimales):
     * representa el "hash_previo" inexistente del genesis.
     */
    public static final String HASH_SEMILLA = "0".repeat(64);

    /** Separador de campos improbable en los datos, para evitar ambiguedades. */
    private static final String SEP = "\u001F"; // Unit Separator (US)

    /** Marcador para campos nulos, distinto de la cadena vacia. */
    private static final String NULO = "\u0000NULL\u0000";

    private CalculadoraHashCadena() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Calcula el {@code hash_actual} de un registro a partir de su contenido y
     * del hash del registro anterior.
     *
     * @param tenantId       tenant del evento; {@code null} para plataforma.
     * @param actor          actor de la accion.
     * @param accion         accion ejecutada.
     * @param recurso        recurso afectado.
     * @param detalle        detalle legible (sin secretos), puede ser {@code null}.
     * @param valorAnterior  JSON del estado previo (sin secretos), puede ser {@code null}.
     * @param valorNuevo     JSON del estado nuevo (sin secretos), puede ser {@code null}.
     * @param traceId        identificador de correlacion, puede ser {@code null}.
     * @param timestampUtc   marca temporal UTC del evento.
     * @param hashPrevio     hash del registro anterior (o {@link #HASH_SEMILLA}).
     * @return el hash hexadecimal SHA-256 (64 caracteres) del registro.
     */
    public static String calcular(
            UUID tenantId, String actor, String accion, String recurso,
            String detalle, String valorAnterior, String valorNuevo,
            String traceId, Instant timestampUtc, String hashPrevio) {

        String contenido = String.join(SEP,
                valor(tenantId == null ? null : tenantId.toString()),
                valor(actor),
                valor(accion),
                valor(recurso),
                valor(detalle),
                valor(valorAnterior),
                valor(valorNuevo),
                valor(traceId),
                valor(timestampUtc == null ? null : timestampUtc.toString()),
                valor(hashPrevio));

        return sha256Hex(contenido);
    }

    private static String valor(String v) {
        return v == null ? NULO : v;
    }

    /**
     * Calcula el SHA-256 de una cadena y lo devuelve en hexadecimal minusculas.
     *
     * @param texto texto a hashear (se codifica en UTF-8).
     * @return hash hexadecimal de 64 caracteres.
     */
    static String sha256Hex(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM estandar; no deberia ocurrir.
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }
}
