package com.dessti.crm.platform.monetizacion.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.dessti.crm.platform.monetizacion.domain.MonetizacionValidaciones;

/**
 * Propiedades configurables de la monetizacion de plataforma (Fase A de precios
 * de modulos). La plataforma opera con una <strong>unica moneda principal</strong>
 * en la que se cotizan los Planes (suma de precios de modulo) y se emiten las
 * facturas de renta; no hay seleccion de moneda por Plan.
 *
 * <p>Se enlaza a {@code crm.monetizacion.*} en {@code application.yml}, siguiendo
 * el mismo patron que {@code OffboardingProperties}: un {@code record} anotado con
 * {@link ConfigurationProperties} y registrado via
 * {@code @EnableConfigurationProperties}.</p>
 *
 * @param monedaPrincipal codigo ISO 4217 (3 letras) de la moneda principal; por
 *                        defecto {@code MXN} si no se configura externamente
 *                        (variable de entorno {@code MONEDA_PRINCIPAL}). Se
 *                        normaliza a mayusculas y se valida el formato al enlazar;
 *                        la existencia como moneda sembrada/activa se verifica en
 *                        el punto de uso (con degradacion controlada a {@code MXN}).
 */
@ConfigurationProperties(prefix = "crm.monetizacion")
public record MonetizacionProperties(String monedaPrincipal) {

    /** Moneda principal por defecto (peso mexicano) si no se configura externamente. */
    public static final String MONEDA_PRINCIPAL_POR_DEFECTO = "MXN";

    /**
     * Aplica el valor por defecto cuando no se provee y valida/normaliza el
     * formato ISO 4217 del codigo (reutilizando la validacion del dominio).
     */
    public MonetizacionProperties {
        if (monedaPrincipal == null || monedaPrincipal.isBlank()) {
            monedaPrincipal = MONEDA_PRINCIPAL_POR_DEFECTO;
        }
        // Valida formato (3 letras) y normaliza a mayusculas; falla el arranque si
        // el codigo configurado es sintacticamente invalido (fail-fast de config).
        monedaPrincipal = MonetizacionValidaciones.normalizarCodigoMoneda(monedaPrincipal);
    }
}
