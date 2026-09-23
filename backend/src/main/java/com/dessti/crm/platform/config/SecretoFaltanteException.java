package com.dessti.crm.platform.config;

import java.util.List;

/**
 * Excepción de arranque lanzada cuando uno o más secretos requeridos no están
 * disponibles al iniciar el Sistema (Requisito 11.2).
 *
 * <p>El mensaje incluye únicamente el <b>nombre</b> de los secretos faltantes,
 * <b>nunca</b> su valor, para no exponer credenciales en logs de arranque
 * (Req 11.2, 11.3).</p>
 */
public class SecretoFaltanteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Construye la excepción a partir de la lista de nombres de secretos faltantes.
     *
     * @param secretosFaltantes nombres de los secretos requeridos ausentes.
     */
    public SecretoFaltanteException(List<String> secretosFaltantes) {
        super(construirMensaje(secretosFaltantes));
    }

    private static String construirMensaje(List<String> secretosFaltantes) {
        return "No se pudo iniciar el Sistema: faltan secretos requeridos "
                + "(configúrelos mediante variables de entorno o un almacén externo, Req 11): "
                + String.join(", ", secretosFaltantes)
                + ". No se exponen sus valores.";
    }
}
