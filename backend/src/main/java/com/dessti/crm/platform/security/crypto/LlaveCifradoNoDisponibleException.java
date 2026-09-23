package com.dessti.crm.platform.security.crypto;

/**
 * Excepción lanzada cuando una {@code Llave_Cifrado} requerida no está
 * disponible: falta la llave activa para cifrar o falta la versión necesaria
 * para descifrar un dato previo (Requisito 67.6).
 *
 * <p>El mensaje identifica la llave por su <b>alias/versión</b>, <b>nunca</b>
 * por su valor, para no exponer material criptográfico en logs (Req 67.2,
 * 67.6). Provoca un <b>bloqueo controlado</b> del acceso a los datos cifrados
 * afectados; cuando la llave activa es esencial, el arranque también se detiene
 * (coherente con el fail-fast de secretos del Req 11.2).</p>
 */
public class LlaveCifradoNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Construye la excepción indicando el alias de la llave ausente.
     *
     * @param alias alias/versión de la {@code Llave_Cifrado} no disponible.
     */
    public LlaveCifradoNoDisponibleException(String alias) {
        super("Llave de cifrado no disponible para la versión '" + alias
                + "'. Configúrela mediante variables de entorno o un almacén externo "
                + "(Req 11, 67.6). No se expone su valor.");
    }

    /**
     * Construye la excepción con un mensaje descriptivo y una causa, sin exponer
     * el valor de la llave.
     *
     * @param mensaje descripción del problema (sin material de llave).
     * @param causa   causa subyacente.
     */
    public LlaveCifradoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
