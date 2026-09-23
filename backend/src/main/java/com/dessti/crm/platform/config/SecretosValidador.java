package com.dessti.crm.platform.config;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;

/**
 * Verificador de secretos requeridos al arranque (Requisito 11.2).
 *
 * <p>Comprueba que cada secreto esencial esté disponible en el entorno de
 * configuración (variables de entorno o almacén externo). Si falta alguno,
 * detiene el arranque lanzando {@link SecretoFaltanteException}, registrando
 * únicamente el <b>nombre</b> del secreto faltante y <b>nunca</b> su valor
 * (Req 11.2, 11.3).</p>
 *
 * <p>La lógica es pura y opera sobre un {@link PropertyResolver} (satisfecho por
 * cualquier {@link Environment}), de modo que puede probarse de forma unitaria
 * con un entorno simulado, sin arrancar el contexto de Spring ni la base de
 * datos.</p>
 */
public final class SecretosValidador {

    private static final Logger log = LoggerFactory.getLogger(SecretosValidador.class);

    /**
     * Nombres (claves de propiedad) de los secretos requeridos que deben estar
     * presentes al arranque. Se resuelven vía relajación de nombres de Spring
     * desde las variables de entorno {@code DB_USER}, {@code DB_PASSWORD} y
     * {@code JWT_SIGNING_KEY} respectivamente.
     */
    static final List<SecretoRequerido> SECRETOS_REQUERIDOS = List.of(
            new SecretoRequerido("spring.datasource.username", "DB_USER"),
            new SecretoRequerido("spring.datasource.password", "DB_PASSWORD"),
            new SecretoRequerido("crm.secretos.jwt-signing-key", "JWT_SIGNING_KEY")
    );

    private SecretosValidador() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Valida la presencia de todos los secretos requeridos usando la lista por
     * defecto.
     *
     * @param environment entorno de configuración de Spring (o cualquier
     *                    {@link PropertyResolver}); no nulo.
     * @throws SecretoFaltanteException si al menos un secreto requerido falta o
     *                                  está vacío.
     */
    public static void validar(PropertyResolver environment) {
        validar(environment, SECRETOS_REQUERIDOS);
    }

    /**
     * Valida la presencia de los secretos indicados. Recorre toda la lista para
     * reportar de una sola vez todos los faltantes.
     *
     * @param environment entorno de configuración; no nulo.
     * @param requeridos  secretos a verificar; no nulo.
     * @throws SecretoFaltanteException si al menos un secreto está ausente o vacío.
     */
    public static void validar(PropertyResolver environment, List<SecretoRequerido> requeridos) {
        List<String> faltantes = new ArrayList<>();
        for (SecretoRequerido secreto : requeridos) {
            String valor = environment.getProperty(secreto.clave());
            if (valor == null || valor.isBlank()) {
                // Se registra SOLO el nombre del secreto, nunca su valor (Req 11.2, 11.3).
                faltantes.add(secreto.nombreDescriptivo());
            }
        }
        if (!faltantes.isEmpty()) {
            log.error("Arranque abortado: secretos requeridos ausentes (Req 11.2): {}", faltantes);
            throw new SecretoFaltanteException(faltantes);
        }
        log.info("Verificación de secretos requeridos completada: {} secretos presentes (Req 11).",
                requeridos.size());
    }

    /**
     * Descriptor de un secreto requerido.
     *
     * @param clave            clave de propiedad resuelta por Spring (p. ej.
     *                         {@code spring.datasource.username}).
     * @param variableEntorno  nombre de la variable de entorno equivalente
     *                         (p. ej. {@code DB_USER}), usado en mensajes.
     */
    public record SecretoRequerido(String clave, String variableEntorno) {

        /**
         * @return nombre legible del secreto para mensajes y logs, del estilo
         *         {@code DB_USER (spring.datasource.username)}; nunca incluye el valor.
         */
        public String nombreDescriptivo() {
            return variableEntorno + " (" + clave + ")";
        }
    }
}
