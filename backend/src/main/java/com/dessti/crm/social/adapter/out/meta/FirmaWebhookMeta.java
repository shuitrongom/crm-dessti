package com.dessti.crm.social.adapter.out.meta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper <strong>puro</strong> de validacion de la firma de los webhooks de Meta
 * (Req 64.3, 64.4, 9). Meta firma el cuerpo (payload) de cada evento con
 * <strong>HMAC-SHA256</strong> usando el <em>app secret</em> y lo envia en la
 * cabecera {@code X-Hub-Signature-256} con el formato {@code sha256=<hex>}.
 *
 * <h2>TLS y validacion (Req 9)</h2>
 * <p>La terminacion TLS ocurre en el Proxy_Inverso (IIS): las peticiones llegan al
 * backend por HTTP interno con las cabeceras {@code X-Forwarded-*} (ver
 * {@code application.yml} y {@code docs/tls-y-proxy-inverso-iis.md}). Con
 * independencia del transporte, la aplicacion <strong>valida la autenticidad del
 * evento</strong> recomputando el HMAC del cuerpo con el app secret (Req 11) y
 * comparandolo con la firma recibida en tiempo constante. Un evento con firma
 * ausente o invalida se rechaza (401/403) antes de procesarse.</p>
 *
 * <p>El app secret se resuelve exclusivamente desde la gestion de secretos
 * ({@code crm.social.app-secret} sobre {@code META_APP_SECRET}, Req 11) y nunca se
 * escribe en logs.</p>
 */
public final class FirmaWebhookMeta {

    /** Prefijo de la firma HMAC-SHA256 en la cabecera {@code X-Hub-Signature-256}. */
    public static final String PREFIJO_SHA256 = "sha256=";

    private static final String ALGORITMO_HMAC = "HmacSHA256";

    private FirmaWebhookMeta() {
        // Utilidad estatica pura: no instanciable.
    }

    /**
     * Calcula la firma HMAC-SHA256 del cuerpo con el app secret y la devuelve en el
     * formato de cabecera de Meta ({@code sha256=<hex>}). Funcion pura.
     *
     * @param appSecret app secret de Meta (Req 11); obligatorio y no vacio.
     * @param cuerpo    cuerpo del evento (payload crudo, tal cual se recibio);
     *                  obligatorio.
     * @return la firma en formato {@code sha256=<hex-minusculas>}.
     * @throws IllegalArgumentException si {@code appSecret}/{@code cuerpo} son nulos
     *         o el app secret esta vacio.
     * @throws IllegalStateException    si el entorno criptografico no soporta
     *         HMAC-SHA256 (no deberia ocurrir en una JVM estandar).
     */
    public static String calcularFirma(String appSecret, String cuerpo) {
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("El app secret de Meta es obligatorio para firmar.");
        }
        if (cuerpo == null) {
            throw new IllegalArgumentException("El cuerpo del evento es obligatorio.");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITMO_HMAC);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITMO_HMAC));
            byte[] hmac = mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8));
            return PREFIJO_SHA256 + aHex(hmac);
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC-SHA256 del webhook.", ex);
        }
    }

    /**
     * Valida la firma recibida en la cabecera {@code X-Hub-Signature-256} frente al
     * cuerpo del evento (Req 64.3, 64.4). Funcion pura. La comparacion es en tiempo
     * constante para evitar ataques de temporizacion; es insensible a mayusculas en
     * el hex y tolera la ausencia del prefijo {@code sha256=} en la firma recibida.
     *
     * @param appSecret       app secret de Meta (Req 11); obligatorio.
     * @param cuerpo          cuerpo del evento (payload crudo); obligatorio.
     * @param firmaRecibida   valor de la cabecera {@code X-Hub-Signature-256};
     *                        {@code null}/vacio se considera invalido.
     * @return {@code true} si la firma recibida coincide con la recomputada.
     */
    public static boolean esValida(String appSecret, String cuerpo, String firmaRecibida) {
        if (firmaRecibida == null || firmaRecibida.isBlank()) {
            return false;
        }
        String esperada = calcularFirma(appSecret, cuerpo);
        // Normaliza ambos a hex en minusculas sin el prefijo para comparar.
        String hexEsperado = despojarPrefijo(esperada);
        String hexRecibido = despojarPrefijo(firmaRecibida.strip().toLowerCase(Locale.ROOT));
        byte[] a = hexEsperado.getBytes(StandardCharsets.UTF_8);
        byte[] b = hexRecibido.getBytes(StandardCharsets.UTF_8);
        // Comparacion en tiempo constante (MessageDigest.isEqual) sobre la longitud
        // esperada; longitudes distintas se rechazan de forma segura.
        return a.length == b.length && MessageDigest.isEqual(a, b);
    }

    private static String despojarPrefijo(String firma) {
        String f = firma.toLowerCase(Locale.ROOT);
        return f.startsWith(PREFIJO_SHA256) ? f.substring(PREFIJO_SHA256.length()) : f;
    }

    private static String aHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
