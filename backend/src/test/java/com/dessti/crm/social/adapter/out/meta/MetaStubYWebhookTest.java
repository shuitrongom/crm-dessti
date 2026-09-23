package com.dessti.crm.social.adapter.out.meta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dessti.crm.social.application.ResultadoEnvioSocial;
import com.dessti.crm.social.application.SolicitudEnvioSocial;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.EstadoEntrega;
import com.dessti.crm.social.domain.TipoMensaje;

/**
 * Prueba de integracion (a nivel de componente, sin contexto de Spring) del
 * {@link MetaStubAdapter} y del helper de firma de webhooks {@link FirmaWebhookMeta}
 * (tarea 40.6; Req 64, 9).
 *
 * <p>Verifica: el <strong>envio exitoso</strong> con id externo y mapeo de
 * {@code estado_entrega} (Req 64.11); el <strong>fallo simulado</strong> con el
 * destinatario centinela (para la politica de reintentos, Req 64.13); la
 * <strong>consulta de estado</strong>; y la <strong>validacion de firma</strong> del
 * webhook (HMAC-SHA256 valido aceptado, cuerpo manipulado o firma ausente
 * rechazados, Req 64.3). Todo determinista, sin red ni credenciales reales.</p>
 */
class MetaStubYWebhookTest {

    private final MetaStubAdapter adaptador = new MetaStubAdapter();

    private static SolicitudEnvioSocial solicitudA(String destinatario, TipoMensaje tipo) {
        return new SolicitudEnvioSocial(
                CanalSocial.WHATSAPP, "cred-ref-1", destinatario, tipo, "Hola desde el CRM");
    }

    // ----------------------------------------------------------------------
    // Adaptador Meta (stub): envio, mapeo de estado_entrega y reintentos
    // ----------------------------------------------------------------------

    @Test
    void enviarTextoExitosoDevuelveIdExternoYEstadoEnviado() {
        ResultadoEnvioSocial resultado = adaptador.enviarTexto(solicitudA("521555000111", TipoMensaje.TEXTO));

        assertThat(resultado.exito()).as("envio exitoso").isTrue();
        assertThat(resultado.externoId()).as("se genera un id externo del proveedor").isNotBlank();
        assertThat(resultado.estadoEntrega())
                .as("estado de entrega inicial mapeado a 'enviado' (Req 64.11)")
                .isEqualTo(EstadoEntrega.ENVIADO);
        assertThat(resultado.mensajeError()).as("sin error en exito").isNull();
    }

    @Test
    void enviarPlantillaEInteractivoTambienExitosos() {
        ResultadoEnvioSocial plantilla =
                adaptador.enviarPlantilla(solicitudA("521555000222", TipoMensaje.PLANTILLA));
        ResultadoEnvioSocial interactivo =
                adaptador.enviarInteractivo(solicitudA("521555000333", TipoMensaje.INTERACTIVO));

        assertThat(plantilla.exito()).isTrue();
        assertThat(plantilla.estadoEntrega()).isEqualTo(EstadoEntrega.ENVIADO);
        assertThat(interactivo.exito()).isTrue();
        assertThat(interactivo.estadoEntrega()).isEqualTo(EstadoEntrega.ENVIADO);
    }

    @Test
    void enviarConDestinatarioCentinelaSimulaFalloParaReintentos() {
        ResultadoEnvioSocial resultado = adaptador.enviarTexto(
                solicitudA(MetaStubAdapter.DESTINATARIO_SIMULA_FALLO, TipoMensaje.TEXTO));

        assertThat(resultado.exito()).as("el proveedor rechaza el envio (simulado)").isFalse();
        assertThat(resultado.externoId()).as("un fallo no asigna id externo").isNull();
        assertThat(resultado.estadoEntrega())
                .as("estado de entrega mapeado a 'fallido' (Req 64.13)")
                .isEqualTo(EstadoEntrega.FALLIDO);
        assertThat(resultado.mensajeError()).as("se informa el motivo del fallo").isNotBlank();
    }

    @Test
    void falloEsDeterministaEnCadaLlamada() {
        // El fallo simulado debe reproducirse de forma estable (base para probar
        // que la politica de reintentos agota los intentos, Req 64.13).
        for (int i = 0; i < 5; i++) {
            ResultadoEnvioSocial r = adaptador.enviarTexto(
                    solicitudA(MetaStubAdapter.DESTINATARIO_SIMULA_FALLO, TipoMensaje.TEXTO));
            assertThat(r.exito()).as("el fallo simulado es determinista").isFalse();
        }
    }

    @Test
    void enviarConSolicitudNulaEsFallo() {
        ResultadoEnvioSocial resultado = adaptador.enviarTexto(null);

        assertThat(resultado.exito()).isFalse();
        assertThat(resultado.estadoEntrega()).isEqualTo(EstadoEntrega.FALLIDO);
        assertThat(resultado.mensajeError()).isNotBlank();
    }

    @Test
    void consultarEstadoMapeaEntregadoOFallido() {
        assertThat(adaptador.consultarEstado("MSG-STUB-1", CanalSocial.WHATSAPP))
                .as("un mensaje con id externo se considera entregado")
                .isEqualTo(EstadoEntrega.ENTREGADO);
        assertThat(adaptador.consultarEstado(null, CanalSocial.WHATSAPP))
                .as("sin id externo el estado es fallido")
                .isEqualTo(EstadoEntrega.FALLIDO);
    }

    // ----------------------------------------------------------------------
    // Validacion de firma del webhook (HMAC-SHA256, X-Hub-Signature-256)
    // ----------------------------------------------------------------------

    private static final String APP_SECRET = "app-secret-de-prueba";
    private static final String CUERPO =
            "{\"identificadorCuenta\":\"555000\",\"remitente\":\"5215551234\",\"texto\":\"Hola\"}";

    @Test
    void firmaValidaSeAcepta() {
        String firma = FirmaWebhookMeta.calcularFirma(APP_SECRET, CUERPO);

        assertThat(FirmaWebhookMeta.esValida(APP_SECRET, CUERPO, firma))
                .as("una firma HMAC-SHA256 correcta se acepta (Req 64.3)")
                .isTrue();
    }

    @Test
    void firmaValidaEsInsensibleAMayusculasYAlPrefijo() {
        String firma = FirmaWebhookMeta.calcularFirma(APP_SECRET, CUERPO);

        // Sin prefijo y en mayusculas: debe seguir validando.
        String sinPrefijo = firma.substring(FirmaWebhookMeta.PREFIJO_SHA256.length()).toUpperCase();
        assertThat(FirmaWebhookMeta.esValida(APP_SECRET, CUERPO, sinPrefijo))
                .as("la validacion tolera ausencia de prefijo y mayusculas en el hex")
                .isTrue();
    }

    @Test
    void cuerpoManipuladoSeRechaza() {
        String firma = FirmaWebhookMeta.calcularFirma(APP_SECRET, CUERPO);
        String cuerpoManipulado = CUERPO.replace("Hola", "Adios");

        assertThat(FirmaWebhookMeta.esValida(APP_SECRET, cuerpoManipulado, firma))
                .as("un cuerpo manipulado invalida la firma (Req 64.3)")
                .isFalse();
    }

    @Test
    void secretoDistintoSeRechaza() {
        String firma = FirmaWebhookMeta.calcularFirma(APP_SECRET, CUERPO);

        assertThat(FirmaWebhookMeta.esValida("otro-secreto", CUERPO, firma))
                .as("una firma calculada con otro secreto se rechaza")
                .isFalse();
    }

    @Test
    void firmaAusenteSeRechaza() {
        assertThat(FirmaWebhookMeta.esValida(APP_SECRET, CUERPO, null))
                .as("una firma ausente se rechaza")
                .isFalse();
        assertThat(FirmaWebhookMeta.esValida(APP_SECRET, CUERPO, "   "))
                .as("una firma en blanco se rechaza")
                .isFalse();
    }
}
