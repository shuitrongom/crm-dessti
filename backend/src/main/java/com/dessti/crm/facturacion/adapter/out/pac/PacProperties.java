package com.dessti.crm.facturacion.adapter.out.pac;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de conexion con el PAC (Proveedor Autorizado de Certificacion) para
 * el Timbrado/cancelacion de CFDI (Req 35.8, 11).
 *
 * <p><strong>Gestion de secretos (Req 11):</strong> todos estos valores provienen
 * <em>fuera del codigo fuente</em> y se resuelven desde variables de entorno /
 * almacen externo ({@code PAC_USER}, {@code PAC_PASSWORD}, {@code PAC_URL}), tal
 * como {@code SecretosProperties} para las credenciales de BD/JWT. No se define
 * ningun valor por defecto sensible. Sus valores <strong>nunca</strong> se
 * escriben en logs; por ello {@link #toString()} los enmascara.</p>
 *
 * <p>El uso real de estas credenciales corresponde al adaptador HTTP del PAC
 * (trabajo futuro). Mientras tanto, el {@link PacStubAdapter} determinista no las
 * necesita, de modo que el sistema arranca en desarrollo/pruebas sin
 * configurarlas.</p>
 *
 * @param usuario  usuario del PAC (origen: entorno {@code PAC_USER}).
 * @param password contrasena del PAC (origen: entorno {@code PAC_PASSWORD}).
 * @param url      URL del servicio del PAC (origen: entorno {@code PAC_URL}).
 */
@ConfigurationProperties(prefix = "crm.pac")
public record PacProperties(String usuario, String password, String url) {

    /**
     * Representacion segura que enmascara las credenciales para evitar su
     * filtracion en logs (Req 11.3). La URL no es un secreto y se muestra tal cual
     * para diagnostico.
     *
     * @return descripcion sin exponer credenciales.
     */
    @Override
    public String toString() {
        return "PacProperties{"
                + "usuario=" + enmascarar(usuario)
                + ", password=" + enmascarar(password)
                + ", url=" + (url == null || url.isBlank() ? "<ausente>" : url)
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
