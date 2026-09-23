package com.dessti.crm.platform.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuracion de los tokens JWT (Req 1, tarea 9.1).
 *
 * <p>Solo agrupa parametros <b>no sensibles</b> (vigencias y emisor). La
 * <b>clave de firma</b> NO se define aqui: proviene de
 * {@code SecretosProperties} (Req 11) y nunca se escribe en configuracion ni en
 * logs.</p>
 *
 * <p><strong>Invariantes de seguridad (Req 1.4, 1.7):</strong> la vigencia del
 * {@code Token_Acceso} no debe exceder 15 minutos y la del
 * {@code Token_Refresco} no debe exceder 7 dias. {@link ServicioTokensJwt}
 * valida estos limites al construirse (fail-fast) para impedir configuraciones
 * fuera de norma.</p>
 *
 * @param emisor                 valor del claim {@code iss} (identifica al emisor).
 * @param vigenciaTokenAcceso    duracion del Token_Acceso (por defecto 15 min, maximo 15 min).
 * @param vigenciaTokenRefresco  duracion del Token_Refresco (por defecto 7 dias, maximo 7 dias).
 */
@ConfigurationProperties(prefix = "crm.jwt")
public record JwtProperties(
        String emisor,
        Duration vigenciaTokenAcceso,
        Duration vigenciaTokenRefresco
) {

    /** Vigencia maxima permitida para el Token_Acceso (Req 1.4). */
    public static final Duration MAX_VIGENCIA_ACCESO = Duration.ofMinutes(15);

    /** Vigencia maxima permitida para el Token_Refresco (Req 1.7). */
    public static final Duration MAX_VIGENCIA_REFRESCO = Duration.ofDays(7);

    public JwtProperties {
        if (emisor == null || emisor.isBlank()) {
            emisor = "crm-anuncios-luminosos";
        }
        if (vigenciaTokenAcceso == null) {
            vigenciaTokenAcceso = MAX_VIGENCIA_ACCESO;
        }
        if (vigenciaTokenRefresco == null) {
            vigenciaTokenRefresco = MAX_VIGENCIA_REFRESCO;
        }
    }
}
