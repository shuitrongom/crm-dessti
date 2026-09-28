package com.dessti.crm.reportesbi.adapter.out.ia;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de conexion con el proveedor de <strong>IA</strong> que genera los
 * insights ejecutivos de la suite BI (Req 48, suite BI+IA; Req 11).
 *
 * <p><strong>Gestion de secretos (Req 11):</strong> la clave de API se resuelve
 * <em>fuera del codigo fuente</em>, desde variables de entorno / almacen externo
 * ({@code IA_API_KEY}), igual que {@code PacProperties} para el PAC. No se define
 * ningun valor por defecto sensible: sin configuracion, la {@code url} y la
 * {@code api-key} quedan vacias y el sistema degrada de forma gracil al generador
 * heuristico. Los secretos <strong>nunca</strong> se escriben en logs; por ello
 * {@link #toString()} los enmascara.</p>
 *
 * <p>El {@code modelo} y el {@code timeout} son parametros NO sensibles del proveedor
 * (por ejemplo el identificador del modelo de lenguaje y el tiempo maximo de espera de
 * la llamada HTTP). El {@code timeout} admite un valor por defecto razonable porque no
 * expone informacion sensible.</p>
 *
 * @param apiKey  clave de API del proveedor (origen: entorno {@code IA_API_KEY}).
 * @param url     URL del endpoint de completions del proveedor (origen: {@code IA_URL}).
 * @param modelo  identificador del modelo de lenguaje a usar (origen: {@code IA_MODELO}).
 * @param timeout tiempo maximo de espera de la llamada HTTP (origen: {@code IA_TIMEOUT},
 *                ISO-8601; por defecto {@code PT20S}).
 */
@ConfigurationProperties(prefix = "crm.ia")
public record IaProperties(String apiKey, String url, String modelo, Duration timeout) {

    /**
     * Normaliza el {@code timeout} a un valor por defecto seguro cuando no se provee o
     * es no positivo, para evitar esperas indefinidas del cliente HTTP.
     */
    public IaProperties {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(20);
        }
    }

    /**
     * Indica si el proveedor de IA esta <strong>configurado y utilizable</strong>: se
     * requiere al menos una URL de endpoint y una clave de API. Cuando devuelve
     * {@code false}, el adaptador HTTP no intenta ninguna llamada de red y se usa el
     * generador heuristico (degradacion gracil).
     *
     * @return {@code true} si hay url y api-key presentes.
     */
    public boolean estaConfigurado() {
        return url != null && !url.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Modelo efectivo, con un valor por defecto neutro cuando no se configura, para no
     * dejar el cuerpo de la peticion sin modelo si el proveedor lo exige.
     *
     * @return el modelo configurado o un identificador por defecto.
     */
    public String modeloEfectivo() {
        return (modelo == null || modelo.isBlank()) ? "gpt-4o-mini" : modelo;
    }

    /**
     * Representacion segura que enmascara la clave de API para evitar su filtracion en
     * logs (Req 11.3). La URL, el modelo y el timeout no son secretos y se muestran
     * para diagnostico.
     *
     * @return descripcion sin exponer la clave de API.
     */
    @Override
    public String toString() {
        return "IaProperties{"
                + "apiKey=" + enmascarar(apiKey)
                + ", url=" + (url == null || url.isBlank() ? "<ausente>" : url)
                + ", modelo=" + (modelo == null || modelo.isBlank() ? "<por-defecto>" : modelo)
                + ", timeout=" + timeout
                + '}';
    }

    /**
     * Enmascara un valor sensible: nunca revela el contenido, solo si fue provisto.
     *
     * @param valor valor sensible (puede ser nulo o vacio).
     * @return {@code "<ausente>"} si no hay valor, {@code "****"} en caso contrario.
     */
    private static String enmascarar(String valor) {
        return (valor == null || valor.isBlank()) ? "<ausente>" : "****";
    }
}
