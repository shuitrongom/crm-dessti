package com.dessti.crm.platform.empresas;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades configurables de la contratación de instrumentos (Plan/Suscripción).
 *
 * <p>Expone el <strong>umbral de aviso de vencimiento</strong> (Req 7.2/7.3): la
 * cantidad de días de anticipación con la que un Contrato vigente se considera
 * "por vencer". El backend usa este umbral para calcular el indicador
 * {@code porVencer} que viaja al frontend, de modo que el umbral vive únicamente
 * en el backend y no se acopla a la capa de presentación.</p>
 *
 * <p>Sigue el mismo patrón que {@code OffboardingProperties}: un {@code record}
 * anotado con {@link ConfigurationProperties} y registrado vía
 * {@code @EnableConfigurationProperties} (ver {@code ContratacionConfig}). Se
 * enlaza a {@code crm.contratacion.*} en {@code application.yml} y admite override
 * por variable de entorno.</p>
 *
 * @param umbralAvisoDias umbral en días para avisar que un Contrato está por
 *                        vencer; por defecto {@code 14} si no se configura
 *                        externamente. Debe ser un valor positivo.
 */
@ConfigurationProperties(prefix = "crm.contratacion")
public record ContratacionProperties(int umbralAvisoDias) {

    /** Umbral de aviso por defecto (14 días) si no se configura externamente. */
    private static final int UMBRAL_AVISO_DIAS_POR_DEFECTO = 14;

    /**
     * Aplica el valor por defecto cuando no se provee y valida que el umbral de
     * aviso sea positivo.
     */
    public ContratacionProperties {
        if (umbralAvisoDias <= 0) {
            umbralAvisoDias = UMBRAL_AVISO_DIAS_POR_DEFECTO;
        }
    }
}
