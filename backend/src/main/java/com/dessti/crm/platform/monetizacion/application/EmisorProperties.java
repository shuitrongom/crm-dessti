package com.dessti.crm.platform.monetizacion.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos del EMISOR (Dess-TI) que se imprimen en el comprobante de renta de
 * modulos (PDF de Fase A). Son datos de plataforma, NO sensibles: identifican a
 * quien presta el servicio de renta de modulos.
 *
 * <p>Se enlaza a {@code crm.facturacion.emisor.*} en {@code application.yml},
 * siguiendo el mismo patron que {@code OffboardingProperties} /
 * {@code MonetizacionProperties}: un {@code record} anotado con
 * {@link ConfigurationProperties} y registrado via
 * {@code @EnableConfigurationProperties}. Todos los campos admiten override por
 * variable de entorno.</p>
 *
 * <p>Cada campo (salvo {@code nombre}) es opcional: cuando queda en blanco, el
 * generador del PDF simplemente NO imprime esa linea del bloque Emisor. El
 * {@code nombre} nunca queda vacio: si no se configura, cae al valor por defecto
 * {@value #NOMBRE_POR_DEFECTO}.</p>
 *
 * @param nombre    razon social / nombre visible del emisor; por defecto
 *                  {@value #NOMBRE_POR_DEFECTO} si no se configura.
 * @param rfc       RFC del emisor; opcional (vacio = no se imprime).
 * @param direccion direccion del emisor en una linea; opcional.
 * @param email     correo de contacto del emisor; opcional.
 * @param sitioWeb  sitio web del emisor; opcional.
 */
@ConfigurationProperties(prefix = "crm.facturacion.emisor")
public record EmisorProperties(String nombre, String rfc, String direccion,
                               String email, String sitioWeb) {

    /** Nombre del emisor por defecto si no se configura externamente. */
    public static final String NOMBRE_POR_DEFECTO = "Dess-TI";

    /**
     * Normaliza los valores: aplica el nombre por defecto cuando no se provee y
     * convierte cualquier campo en blanco a cadena vacia (asi el generador del
     * PDF omite de forma uniforme los campos "vacios").
     */
    public EmisorProperties {
        nombre = (nombre == null || nombre.isBlank()) ? NOMBRE_POR_DEFECTO : nombre.strip();
        rfc = normalizarOpcional(rfc);
        direccion = normalizarOpcional(direccion);
        email = normalizarOpcional(email);
        sitioWeb = normalizarOpcional(sitioWeb);
    }

    private static String normalizarOpcional(String valor) {
        return (valor == null || valor.isBlank()) ? "" : valor.strip();
    }
}
