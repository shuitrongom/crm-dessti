package com.dessti.crm.platform.empresas;

import java.util.Locale;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Validador del RFC (Registro Federal de Contribuyentes) mexicano para la
 * Empresa (Tenant) a nivel de plataforma (Req 24).
 *
 * <p>El RFC mexicano tiene dos formas segun el tipo de contribuyente:</p>
 * <ul>
 *   <li><strong>Persona moral</strong> (empresa/sociedad): 3 letras de razon
 *       social + 6 digitos de fecha (AAMMDD) + 3 caracteres de homoclave =
 *       12 caracteres.</li>
 *   <li><strong>Persona fisica</strong>: 4 letras (apellidos + nombre) + 6
 *       digitos de fecha (AAMMDD) + 3 caracteres de homoclave = 13 caracteres.</li>
 * </ul>
 *
 * <p>El patron canonico, aplicado siempre sobre el RFC YA NORMALIZADO a
 * mayusculas, es {@code ^([A-ZÑ&]{3,4})\d{6}([A-Z\d]{3})$}: 3 o 4 letras
 * (admite la {@code Ñ} y el {@code &} de las razones sociales), seguidas de 6
 * digitos de fecha y de 3 caracteres alfanumericos de homoclave. Esto NO valida
 * la validez calendarica exacta de la fecha ni el digito verificador oficial:
 * comprueba la ESTRUCTURA sintactica del RFC, que es lo exigido para el registro
 * de plataforma.</p>
 *
 * <p>La clase es una utilidad sin estado; no se instancia.</p>
 */
public final class RfcValidador {

    /**
     * Patron estructural del RFC mexicano (persona moral de 12 o fisica de 13).
     * Se evalua sobre el valor ya normalizado a mayusculas.
     */
    private static final Pattern PATRON_RFC =
            Pattern.compile("^[A-ZÑ&]{3,4}\\d{6}[A-Z\\d]{3}$");

    private RfcValidador() {
        // Utilidad sin estado.
    }

    /**
     * Normaliza el RFC recibido: recorta espacios envolventes y lo pasa a
     * mayusculas. Un {@code null} o en blanco se rechaza como obligatorio.
     *
     * @param rfc RFC en crudo tal como llega del comando/peticion.
     * @return el RFC normalizado (recortado y en mayusculas).
     * @throws ReglaNegocioException si el RFC es nulo o esta en blanco.
     */
    public static String normalizar(String rfc) {
        if (rfc == null || rfc.isBlank()) {
            throw new ReglaNegocioException("El identificador fiscal (RFC) es obligatorio.");
        }
        return rfc.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * Indica si un RFC YA NORMALIZADO cumple la estructura del RFC mexicano.
     *
     * @param rfcNormalizado RFC en mayusculas y sin espacios envolventes.
     * @return {@code true} si respeta el patron de persona moral (12) o fisica (13).
     */
    public static boolean esValido(String rfcNormalizado) {
        return rfcNormalizado != null && PATRON_RFC.matcher(rfcNormalizado).matches();
    }

    /**
     * Normaliza y valida el RFC, devolviendo el valor normalizado listo para
     * persistir. Es el punto de entrada que usa el dominio en el alta de la
     * Empresa.
     *
     * @param rfc RFC en crudo.
     * @return el RFC normalizado y valido.
     * @throws ReglaNegocioException si el RFC es obligatorio y falta (blanco) o
     *                               si su estructura no corresponde a un RFC
     *                               mexicano valido (HTTP 422).
     */
    public static String normalizarYValidar(String rfc) {
        String normalizado = normalizar(rfc);
        if (!esValido(normalizado)) {
            throw new ReglaNegocioException(
                    "El identificador fiscal (RFC) '" + normalizado
                            + "' no tiene un formato valido de RFC mexicano.");
        }
        return normalizado;
    }
}
