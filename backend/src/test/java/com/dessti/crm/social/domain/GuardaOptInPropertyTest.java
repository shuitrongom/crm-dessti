package com.dessti.crm.social.domain;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 38: Guarda de
 * Opt_In para mensajeria de marketing</strong> (Req 64.8, 64.9, 46.7).
 *
 * <p>Ejercita el nucleo de decision PURO {@link GuardaOptIn} contra el modelo de
 * referencia, sin base de datos ni contexto de Spring. La decision es una funcion
 * booleana pura {@code (esMarketing, tieneOptInVigente) -> boolean}, por lo que se
 * cubre exhaustivamente el espacio de entradas.</p>
 *
 * <h2>Invariantes verificados (Property 38)</h2>
 * <ol>
 *   <li><strong>Marketing requiere Opt_In (Req 64.8):</strong> cuando el mensaje es
 *       de marketing, el envio se permite <em>si y solo si</em> hay Opt_In vigente.</li>
 *   <li><strong>No marketing no se bloquea:</strong> cuando el mensaje no es de
 *       marketing, el envio se permite con independencia del consentimiento.</li>
 *   <li><strong>Rechazo sin consentimiento (Req 64.8):</strong> marketing sin Opt_In
 *       vigente siempre se rechaza.</li>
 * </ol>
 */
class GuardaOptInPropertyTest {

    // Feature: crm-anuncios-luminosos, Property 38: Guarda de Opt_In para mensajería de marketing
    @Property(tries = 1000)
    void marketingSePermiteSiiHayOptInVigente(
            @ForAll boolean esMarketing,
            @ForAll boolean tieneOptInVigente) {

        boolean permite = GuardaOptIn.puedeEnviarMarketing(esMarketing, tieneOptInVigente);

        // Modelo de referencia: no-marketing siempre permitido; marketing solo con opt-in.
        boolean esperado = !esMarketing || tieneOptInVigente;
        assertThat(permite)
                .as("puedeEnviarMarketing(esMarketing=%s, optIn=%s) == %s",
                        esMarketing, tieneOptInVigente, esperado)
                .isEqualTo(esperado);
    }

    // Feature: crm-anuncios-luminosos, Property 38: Guarda de Opt_In para mensajería de marketing
    @Property(tries = 1000)
    void marketingSinOptInSiempreSeRechaza(@ForAll boolean ignorado) {
        assertThat(GuardaOptIn.puedeEnviarMarketing(true, false))
                .as("marketing sin Opt_In vigente se rechaza (Req 64.8)")
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 38: Guarda de Opt_In para mensajería de marketing
    @Property(tries = 1000)
    void noMarketingNuncaSeBloqueaPorOptIn(@ForAll boolean tieneOptInVigente) {
        assertThat(GuardaOptIn.puedeEnviarMarketing(false, tieneOptInVigente))
                .as("los mensajes de servicio (no marketing) no requieren Opt_In (Req 64.8)")
                .isTrue();
    }
}
