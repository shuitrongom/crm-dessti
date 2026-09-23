package com.dessti.crm.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades tipadas que agrupan los secretos y valores sensibles del Sistema
 * (Requisito 11).
 *
 * <p>Todos estos valores provienen <b>fuera del código fuente</b>: se resuelven
 * desde variables de entorno o un almacén de configuración externo (por ejemplo
 * {@code DB_USER}, {@code DB_PASSWORD}, {@code JWT_SIGNING_KEY}). No se define
 * ningún valor por defecto sensible; la ausencia de un secreto requerido debe
 * detener el arranque (ver {@link SecretosValidador}).</p>
 *
 * <p><b>Seguridad (Req 11.3):</b> los valores de secretos <b>nunca</b> se
 * escriben en logs. Por ello {@link #toString()} está sobrescrito para
 * enmascarar por completo el contenido y jamás debe reemplazarse por una
 * representación que exponga los valores.</p>
 *
 * <p>El uso real de la clave de firma JWT ({@link #jwtSigningKey()}) se
 * implementa en la Tarea 9 (autenticación); aquí solo se declara la propiedad
 * y se valida su presencia al arranque.</p>
 *
 * @param dbUser        usuario de la base de datos (origen: entorno {@code DB_USER}).
 * @param dbPassword    contraseña de la base de datos (origen: entorno {@code DB_PASSWORD}).
 * @param jwtSigningKey clave de firma de tokens JWT (origen: entorno {@code JWT_SIGNING_KEY}).
 */
@ConfigurationProperties(prefix = "crm.secretos")
public record SecretosProperties(
        String dbUser,
        String dbPassword,
        String jwtSigningKey
) {

    /**
     * Representación segura que enmascara todos los secretos para evitar su
     * filtración en logs (Req 11.3). Solo indica si cada secreto está presente.
     *
     * @return descripción sin exponer ningún valor de secreto.
     */
    @Override
    public String toString() {
        return "SecretosProperties{"
                + "dbUser=" + enmascarar(dbUser)
                + ", dbPassword=" + enmascarar(dbPassword)
                + ", jwtSigningKey=" + enmascarar(jwtSigningKey)
                + '}';
    }

    /**
     * Enmascara un valor sensible: nunca revela el contenido, solo si fue provisto.
     *
     * @param valor valor sensible (puede ser nulo o vacío).
     * @return {@code "<ausente>"} si no hay valor, {@code "****"} en caso contrario.
     */
    private static String enmascarar(String valor) {
        return (valor == null || valor.isBlank()) ? "<ausente>" : "****";
    }
}
