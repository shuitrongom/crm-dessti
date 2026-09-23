package com.dessti.crm.platform.empresas.offboarding;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades configurables del offboarding del tenant (Req 69).
 *
 * <p>Expone el <strong>Periodo_Gracia configurable</strong> (Req 69.2): la
 * duracion durante la cual, tras cancelar la Suscripcion/Empresa, los datos de
 * negocio se conservan antes de cualquier eliminacion definitiva (Req 69.3),
 * mientras el acceso queda restringido conforme al estado de la Empresa
 * (Req 24.4/69.2).</p>
 *
 * <p>Se enlaza a {@code crm.offboarding.*} en {@code application.yml}. El valor
 * se expresa en formato ISO-8601 de {@link Duration} (por ejemplo {@code P30D}
 * para 30 dias).</p>
 *
 * @param periodoGracia duracion del Periodo_Gracia; por defecto 30 dias
 *                      ({@code P30D}) si no se configura externamente. Debe ser
 *                      una duracion no negativa.
 */
@ConfigurationProperties(prefix = "crm.offboarding")
public record OffboardingProperties(Duration periodoGracia) {

    /** Periodo_Gracia por defecto (30 dias) si no se configura externamente. */
    private static final Duration PERIODO_GRACIA_POR_DEFECTO = Duration.ofDays(30);

    /**
     * Aplica el valor por defecto cuando no se provee y valida que el
     * Periodo_Gracia no sea negativo.
     */
    public OffboardingProperties {
        if (periodoGracia == null) {
            periodoGracia = PERIODO_GRACIA_POR_DEFECTO;
        }
        if (periodoGracia.isNegative()) {
            throw new IllegalArgumentException(
                    "crm.offboarding.periodo-gracia no puede ser negativo");
        }
    }
}
